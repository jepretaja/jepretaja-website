package com.jepretaja.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.asFlow
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.PackageAddOnModel
import com.jepretaja.app.data.model.PackageModel
import com.jepretaja.app.data.model.PortfolioModel
import com.jepretaja.app.data.model.PortfolioStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class CreatorRepository @Inject constructor(private val db: FirebaseFirestore) {

    suspend fun getCreator(creatorId: String): CreatorModel? =
        db.collection(FirestorePaths.CREATORS).document(creatorId).get().await()
            .toObject(CreatorModel::class.java)

    /**
     * Dokumen creator yang selalu ikut berubah.
     *
     * Dibutuhkan layar rekening: kalau admin atau panel web memperbaiki data
     * bank, perubahan itu harus terlihat di HP tanpa creator perlu menutup dan
     * membuka lagi layarnya — dan justru ketidakcocokan diam-diam antara dua
     * layar itulah yang membuat orang ragu data mana yang dipakai.
     */
    fun streamCreator(creatorId: String): Flow<CreatorModel?> =
        db.collection(FirestorePaths.CREATORS).document(creatorId).asFlow()

    fun streamNearby(city: String? = null): Flow<List<CreatorModel>> {
        var q = db.collection(FirestorePaths.CREATORS).whereEqualTo("status", "active")
        if (city != null) q = q.whereEqualTo("city", city)
        return q.limit(20).asFlow()
    }

    /**
     * Creator aktif diurut rating.
     *
     * [limit] bisa dinaikkan oleh pemanggil yang masih akan menyaring ulang di
     * klien — seksi "Rekomendasi" di Home, misalnya, memeringkat lagi menurut
     * minat pengguna, dan jaring 20 teratas sering tidak menyisakan cukup nama
     * setelah disaring. Query-nya tetap sama, jadi tidak ada index baru yang
     * perlu dibuat.
     */
    fun streamPopular(limit: Long = 20): Flow<List<CreatorModel>> =
        db.collection(FirestorePaths.CREATORS)
            .whereEqualTo("status", "active")
            .orderBy("rating", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(limit).asFlow()

    /**
     * Waktu aktif terakhir beberapa creator sekaligus.
     *
     * Satu query `whereIn` untuk seluruh daftar chat, bukan satu langganan per
     * baris. Dibatasi 30 karena itu batas `in` di Firestore — daftar chat yang
     * lebih panjang dari itu tetap jalan, hanya titik hijau untuk baris di
     * bawahnya yang tidak muncul, dan itu jauh lebih baik daripada query yang
     * gagal seluruhnya.
     */
    fun streamAktifTerakhir(creatorIds: List<String>): Flow<Map<String, Long>> {
        val ids = creatorIds.filter { it.isNotBlank() }.distinct().take(30)
        if (ids.isEmpty()) return kotlinx.coroutines.flow.flowOf(emptyMap())
        return db.collection(FirestorePaths.CREATORS)
            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), ids)
            .asFlow<CreatorModel>()
            .map { daftar ->
                daftar.mapNotNull { c ->
                    c.lastActiveAt?.toDate()?.time?.let { c.creatorId to it }
                }.toMap()
            }
    }

    /**
     * Album portfolio satu creator.
     *
     * [hanyaTayang] default true, dan itu disengaja: fungsi yang sama dipakai
     * profil publik dan layar kelola. Kalau penyaringannya diserahkan ke
     * pemanggil, satu pemanggil yang lupa akan membocorkan draft dan album yang
     * ditolak ke halaman yang dilihat calon pelanggan — kegagalan yang tidak
     * menimbulkan error apa pun dan karena itu tidak akan ketahuan.
     *
     * Pengurutan dikerjakan di klien. `orderBy("order")` akan MEMBUANG setiap
     * album yang dibuat sebelum field itu ada, karena Firestore mengecualikan
     * dokumen yang tidak punya field yang diurutkan.
     */
    fun streamPortfolio(creatorId: String, hanyaTayang: Boolean = true): Flow<List<PortfolioModel>> =
        db.collection(FirestorePaths.PORTFOLIOS)
            .whereEqualTo("creatorId", creatorId).asFlow<PortfolioModel>()
            .map { daftar ->
                daftar.filter { !hanyaTayang || it.status in PortfolioStatus.TAYANG }
                    .sortedWith(compareBy({ it.order }, { it.title }))
            }

    fun streamPackages(creatorId: String): Flow<List<PackageModel>> =
        db.collection(FirestorePaths.PACKAGES)
            .whereEqualTo("creatorId", creatorId)
            .whereEqualTo("active", true).asFlow()

    /**
     * Add-on disimpan sebagai ARRAY `addOns` di dalam dokumen paket, bukan
     * sub-koleksi `packages/{id}/add_ons`.
     *
     * Dua alasan, keduanya bikin fitur ini dulu tidak pernah jalan:
     * 1. Server (api/_lib/actions/appBooking.js -> hitungHarga) membaca
     *    `pkg.addOns` dan mencocokkan `a.id === id`. Selama add-on disimpan di
     *    sub-koleksi, server tidak pernah menemukannya dan setiap booking yang
     *    memilih add-on ditolak dengan "Add-on tidak tersedia pada paket ini".
     * 2. firestore.rules tidak pernah menyebut sub-koleksi `add_ons`, sehingga
     *    ia jatuh ke aturan tolak-semua paling bawah — membaca dan menulisnya
     *    dari APK selalu PERMISSION_DENIED.
     *
     * Bentuk tiap elemen: { id, name, price } — `id` dibuat di klien karena
     * elemen array tidak punya document id sendiri.
     */
    fun streamAddOns(packageId: String): Flow<List<PackageAddOnModel>> = callbackFlow {
        val registration = db.collection(FirestorePaths.PACKAGES).document(packageId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(bacaAddOns(snapshot?.get("addOns")))
            }
        awaitClose { registration.remove() }
    }

    suspend fun addAddOn(packageId: String, name: String, price: Long) {
        val item = mapOf(
            "id" to UUID.randomUUID().toString(),
            "name" to name,
            "price" to price,
        )
        db.collection(FirestorePaths.PACKAGES).document(packageId)
            .update("addOns", FieldValue.arrayUnion(item)).await()
    }

    suspend fun deleteAddOn(packageId: String, addOnId: String) {
        val ref = db.collection(FirestorePaths.PACKAGES).document(packageId)
        // arrayRemove butuh nilai elemen yang sama persis, jadi elemennya
        // dibaca dulu lalu ditulis ulang tanpa yang dihapus.
        val sisa = bacaAddOns(ref.get().await().get("addOns"))
            .filter { it.addOnId != addOnId }
            .map { mapOf("id" to it.addOnId, "name" to it.name, "price" to it.price) }
        ref.update("addOns", sisa).await()
    }

    private fun bacaAddOns(raw: Any?): List<PackageAddOnModel> =
        (raw as? List<*>).orEmpty().mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null
            val id = map["id"] as? String ?: return@mapNotNull null
            PackageAddOnModel(
                addOnId = id,
                name = map["name"] as? String ?: "",
                price = (map["price"] as? Number)?.toLong() ?: 0L,
            )
        }

    /**
     * Menandai creator ini baru saja aktif.
     *
     * Dibatasi sekali per 5 menit lewat penanda lokal. Tanpa pembatas, setiap
     * pembukaan layar menulis satu dokumen — biaya yang tidak sebanding untuk
     * sebuah titik hijau, dan cukup untuk membuat kuota tulis Firestore habis
     * pada pengguna yang aktif seharian.
     */
    suspend fun touchPresence(creatorId: String) {
        db.collection(FirestorePaths.CREATORS).document(creatorId)
            .set(mapOf("lastActiveAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
    }

    /** Saklar menerima booking, beserta tanggal kembali dan catatannya. */
    suspend fun setAcceptingBookings(
        creatorId: String,
        menerima: Boolean,
        awayUntilMillis: Long? = null,
        catatan: String? = null,
    ) {
        db.collection(FirestorePaths.CREATORS).document(creatorId).set(
            mapOf(
                "acceptingBookings" to menerima,
                "awayUntil" to awayUntilMillis?.let { com.google.firebase.Timestamp(java.util.Date(it)) },
                "awayNote" to catatan,
            ),
            SetOptions.merge(),
        ).await()
    }

    /**
     * Menyimpan rekening pencairan creator.
     *
     * Ditulis dengan merge ke dokumen creator yang sama yang dibaca panel web,
     * jadi rekening yang diisi dari HP langsung terlihat admin saat memproses
     * penarikan — tidak ada salinan kedua yang bisa berbeda isinya.
     *
     * Nomor rekening dibersihkan dari spasi dan tanda hubung di sini, bukan di
     * layar: bentuk yang tersimpan harus satu macam apa pun cara creator
     * mengetiknya, karena angka inilah yang nanti disalin admin ke aplikasi
     * banknya.
     */
    suspend fun simpanRekening(
        creatorId: String,
        namaBank: String,
        nomorRekening: String,
        namaPemilik: String,
    ) {
        db.collection(FirestorePaths.CREATORS).document(creatorId).set(
            mapOf(
                "bankCode" to namaBank.trim(),
                "bankAccountNumber" to nomorRekening.filter { it.isDigit() },
                "bankAccountName" to namaPemilik.trim(),
                "bankUpdatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    /**
     * Menghapus rekening tersimpan.
     *
     * Memakai FieldValue.delete(), bukan menulis null: rekening yang "ada tapi
     * kosong" akan lolos pemeriksaan `!= null` di layar penarikan dan berakhir
     * sebagai pengajuan tanpa tujuan yang harus ditolak admin secara manual.
     */
    suspend fun hapusRekening(creatorId: String) {
        db.collection(FirestorePaths.CREATORS).document(creatorId).set(
            mapOf(
                "bankCode" to FieldValue.delete(),
                "bankAccountNumber" to FieldValue.delete(),
                "bankAccountName" to FieldValue.delete(),
                "bankUpdatedAt" to FieldValue.delete(),
            ),
            SetOptions.merge(),
        ).await()
    }

    /** Menyematkan satu karya di urutan pertama grid profil. */
    suspend fun setPinnedPost(creatorId: String, postId: String?) {
        db.collection(FirestorePaths.CREATORS).document(creatorId)
            .set(mapOf("pinnedPostId" to postId), SetOptions.merge()).await()
    }

    suspend fun upsertPackage(pkg: PackageModel) {
        val id = pkg.packageId.ifEmpty { db.collection(FirestorePaths.PACKAGES).document().id }
        // merge, BUKAN set biasa: PackageModel tidak memuat field `addOns`,
        // jadi set penuh akan menghapus seluruh add-on paket ini setiap kali
        // creator menyunting harga atau namanya.
        db.collection(FirestorePaths.PACKAGES).document(id)
            .set(pkg.copy(packageId = id), SetOptions.merge()).await()
        syncMinPrice(pkg.creatorId)
    }

    suspend fun deletePackage(packageId: String, creatorId: String) {
        db.collection(FirestorePaths.PACKAGES).document(packageId).delete().await()
        syncMinPrice(creatorId)
    }

    /** Sinkronkan creators.minPrice = harga terendah paket aktif — dipakai
     * filter/sort "Harga" di Search (section 19). */
    private suspend fun syncMinPrice(creatorId: String) {
        val snap = db.collection(FirestorePaths.PACKAGES)
            .whereEqualTo("creatorId", creatorId)
            .whereEqualTo("active", true).get().await()
        val minPrice = snap.documents.mapNotNull { it.getLong("price") }.minOrNull()
        db.collection(FirestorePaths.CREATORS).document(creatorId).update("minPrice", minPrice).await()
    }

    /**
     * Menyimpan satu album berisi beberapa media sekaligus.
     *
     * `media` memang sudah bertipe List sejak awal, tapi tidak ada satu pun
     * jalan untuk mengisinya lebih dari satu: [addPortfolioItem] selalu
     * membungkus satu URL. Akibatnya sebuah sesi pemotretan berisi dua belas
     * foto menjadi dua belas album terpisah tanpa judul di grid creator.
     */
    suspend fun addPortfolioAlbum(
        creatorId: String,
        media: List<String>,
        title: String,
        category: String,
        type: String,
        thumbnailUrl: String?,
        status: String,
        order: Long,
    ) {
        db.collection(FirestorePaths.PORTFOLIOS).add(
            mapOf(
                "creatorId" to creatorId,
                "media" to media,
                "title" to title,
                "category" to category,
                "type" to type,
                "thumbnailUrl" to thumbnailUrl,
                "status" to status,
                "order" to order,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    suspend fun updatePortfolioStatus(portfolioId: String, status: String) {
        val isi = mutableMapOf<String, Any?>("status" to status)
        if (status == PortfolioStatus.MENUNGGU) {
            isi["submittedAt"] = FieldValue.serverTimestamp()
            // Alasan penolakan lama dibersihkan saat diajukan ulang. Kalau
            // dibiarkan, album yang sudah diperbaiki tetap memajang keluhan
            // moderator atas versi sebelumnya.
            isi["rejectedReason"] = null
        }
        db.collection(FirestorePaths.PORTFOLIOS).document(portfolioId).update(isi).await()
    }

    /**
     * Menyimpan urutan baru sekaligus dalam satu batch.
     *
     * Satu tulisan per album akan menghasilkan urutan yang separuh tersimpan
     * kalau koneksi putus di tengah — dan urutan yang separuh benar lebih
     * membingungkan daripada urutan lama yang utuh.
     */
    suspend fun simpanUrutanPortfolio(idBerurutan: List<String>) {
        val batch = db.batch()
        idBerurutan.forEachIndexed { indeks, id ->
            batch.update(db.collection(FirestorePaths.PORTFOLIOS).document(id), "order", indeks.toLong())
        }
        batch.commit().await()
    }

    suspend fun addPortfolioItem(creatorId: String, mediaUrl: String, title: String, category: String) {
        db.collection(FirestorePaths.PORTFOLIOS).add(
            mapOf("creatorId" to creatorId, "media" to listOf(mediaUrl), "title" to title, "category" to category, "status" to "active")
        ).await()
    }

    /** Ubah judul/kategori item portfolio (media tetap — mengganti foto berarti
     * unggah baru, bukan sunting). Sebelumnya item portfolio hanya bisa
     * ditambah dan dihapus, salah ketik judul berarti harus unggah ulang. */
    suspend fun updatePortfolioItem(portfolioId: String, title: String, category: String) {
        db.collection(FirestorePaths.PORTFOLIOS).document(portfolioId).update(
            mapOf("title" to title, "category" to category)
        ).await()
    }

    suspend fun deletePortfolioItem(portfolioId: String) {
        db.collection(FirestorePaths.PORTFOLIOS).document(portfolioId).delete().await()
    }

    /** Update profil creator (section 8) — field non-finansial saja
     * (nama, bio, kota, kategori); RBAC/verified TIDAK bisa diubah dari
     * sini, hanya lewat Admin. */
    suspend fun updateProfile(creatorId: String, displayName: String, bio: String?, city: String?, categories: List<String>) {
        db.collection(FirestorePaths.CREATORS).document(creatorId).update(
            mapOf("displayName" to displayName, "bio" to bio, "city" to city, "categories" to categories)
        ).await()
    }

    /** Menyimpan jam kerja mingguan. */
    suspend fun updateJamKerja(creatorId: String, jam: com.jepretaja.app.data.model.WorkingHours) {
        db.collection(FirestorePaths.CREATORS).document(creatorId).set(
            mapOf(
                "workingHours" to mapOf(
                    "days" to jam.days,
                    "start" to jam.start,
                    "end" to jam.end,
                    "note" to jam.note,
                )
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun submitVerificationDocument(creatorId: String, documentUrl: String, type: String) {
        db.collection(FirestorePaths.CREATOR_VERIFICATIONS).add(
            mapOf("creatorId" to creatorId, "documentUrl" to documentUrl, "type" to type, "status" to "pending", "submittedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp())
        ).await()
    }

    /** Nearby dengan jarak Haversine — sama seperti catatan skala di versi
     * Flutter: client-side filtering, cukup untuk ratusan/ribuan creator,
     * migrasi ke geohash query untuk skala nasional. */
    suspend fun nearbyWithDistance(lat: Double, lng: Double, radiusKm: Double): List<Pair<CreatorModel, Double>> {
        val snap = db.collection(FirestorePaths.CREATORS).whereEqualTo("status", "active").limit(200).get().await()
        return snap.documents.mapNotNull { doc ->
            val creator = doc.toObject(CreatorModel::class.java) ?: return@mapNotNull null
            val cLat = creator.serviceLat ?: return@mapNotNull null
            val cLng = creator.serviceLng ?: return@mapNotNull null
            val distance = haversineKm(lat, lng, cLat, cLng)
            if (distance <= radiusKm) creator to distance else null
        }.sortedBy { it.second }
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
