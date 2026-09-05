package com.jepretaja.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.data.model.UserModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lapisan autentikasi. Sama seperti versi Flutter — TIDAK dipercaya untuk
 * validasi role sensitif; Firestore Rules & Cloud Functions tetap sumber
 * kebenaran server-side (section 24).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val exploreRepository: ExploreRepository,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun getCurrentUserProfile(): UserModel? {
        val uid = currentUser?.uid ?: return null
        val snap = db.collection(FirestorePaths.USERS).document(uid).get().await()
        return snap.toObject(UserModel::class.java)
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun registerCustomer(name: String, email: String, password: String, phone: String?) {
        val cred = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = cred.user!!.uid
        db.collection(FirestorePaths.USERS).document(uid).set(
            mapOf(
                "userId" to uid, "name" to name, "email" to email, "phone" to phone,
                "role" to "customer", "status" to "active", "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        cred.user?.sendEmailVerification()?.await()
    }

    suspend fun registerCreator(name: String, email: String, password: String, city: String, phone: String?) {
        val cred = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = cred.user!!.uid
        val batch = db.batch()
        batch.set(
            db.collection(FirestorePaths.USERS).document(uid),
            mapOf(
                "userId" to uid, "name" to name, "email" to email, "phone" to phone,
                "role" to "creator", "status" to "active", "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        batch.set(
            db.collection(FirestorePaths.CREATORS).document(uid),
            mapOf(
                "userId" to uid, "displayName" to name, "city" to city, "categories" to emptyList<String>(),
                "rating" to 0.0, "reviewCount" to 0, "followerCount" to 0, "verified" to false,
                "status" to "active", "createdAt" to FieldValue.serverTimestamp(),
            )
        )
        // CATATAN: dokumen wallets/{uid} SENGAJA tidak dibuat di sini.
        // firestore.rules menutup total penulisan ke koleksi `wallets`
        // (allow write: if false) karena isinya saldo uang. Karena batch
        // Firestore bersifat atomic, satu penulisan terlarang dulu membuat
        // SELURUH pendaftaran creator gagal — dokumen users dan creators ikut
        // batal padahal akun Auth-nya sudah terlanjur dibuat, sehingga setiap
        // panggilan server balas "Akun tidak ditemukan".
        //
        // Wallet dibuat server saat dana pertama dilepas (releaseEscrow pakai
        // set + merge), dan layar wallet menampilkan saldo 0 selama dokumennya
        // belum ada.
        batch.commit().await()
        cred.user?.sendEmailVerification()?.await()
    }

  /** Google Sign-In (section 8). Dokumen users/{uid} dibuat otomatis
   * dengan role customer bila belum ada — mendaftar & login jadi satu alur. */
  suspend fun signInWithGoogleIdToken(idToken: String) {
      val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
      val result = auth.signInWithCredential(credential).await()
      val user = result.user!!
      val docRef = db.collection(FirestorePaths.USERS).document(user.uid)
      val existing = docRef.get().await()
      if (!existing.exists()) {
          docRef.set(
              mapOf(
                  "userId" to user.uid, "name" to (user.displayName ?: "Pengguna"), "email" to (user.email ?: ""),
                  "photoUrl" to user.photoUrl?.toString(), "role" to "customer", "status" to "active",
                  "createdAt" to FieldValue.serverTimestamp(),
              )
          ).await()
      }
  }

  fun logout() = auth.signOut()

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun sendEmailVerification() {
        currentUser?.let { if (!it.isEmailVerified) it.sendEmailVerification().await() }
    }

    /**
     * Menanyakan ulang status verifikasi ke server Firebase.
     *
     * `currentUser.isEmailVerified` dibaca dari sesi yang di-cache di perangkat,
     * dan nilainya TIDAK berubah dengan sendirinya setelah pengguna menekan
     * tautan verifikasi di email — cache itu hanya diperbarui oleh reload().
     * Tanpa ini, aplikasi terus menampilkan "Email belum diverifikasi" selamanya
     * walaupun pengguna sudah benar-benar memverifikasi, dan satu-satunya cara
     * memperbaruinya adalah keluar lalu masuk lagi.
     */
    suspend fun refreshEmailVerified(): Boolean {
        val user = currentUser ?: return false
        user.reload().await()
        return currentUser?.isEmailVerified == true
    }

    /**
     * Ubah profil sendiri (konsumen maupun creator).
     *
     * Sengaja hanya menyentuh field milik pengguna: `role` dan `status` tidak
     * ikut ditulis, supaya tidak ada jalan bagi klien untuk menaikkan haknya
     * sendiri lewat layar edit profil.
     */
    suspend fun updateMyProfile(name: String, phone: String?, photoUrl: String?) {
        val uid = currentUser?.uid ?: throw IllegalStateException("Belum masuk.")
        val updates = mutableMapOf<String, Any?>("name" to name)
        updates["phone"] = phone
        if (photoUrl != null) updates["photoUrl"] = photoUrl
        db.collection(FirestorePaths.USERS).document(uid).update(updates).await()
    }

    /**
     * Hapus akun beserta jejak kontennya.
     *
     * Urutannya penting: konten dibersihkan SELAGI pengguna masih terautentikasi,
     * karena begitu akun Firebase Auth dihapus, aturan Firestore tidak lagi
     * mengenalinya sebagai pemilik dan penghapusan pasti ditolak. Sebelumnya
     * fungsi ini hanya menandai dokumen user sebagai "deleted", sehingga post dan
     * komentar lama tetap tayang atas nama akun yang sudah tidak ada.
     */
    suspend fun deleteAccount() {
        val uid = currentUser?.uid ?: return
        exploreRepository.purgeUserContent(uid)
        db.collection(FirestorePaths.USERS).document(uid).update(
            mapOf("status" to "deleted", "email" to "deleted_$uid@jepretaja.app", "name" to "Pengguna Dihapus")
        ).await()
        currentUser?.delete()?.await()
    }
}
