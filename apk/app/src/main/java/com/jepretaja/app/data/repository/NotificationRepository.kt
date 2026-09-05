package com.jepretaja.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.asFlow
import com.jepretaja.app.data.model.NotificationModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifikasi pengguna.
 *
 * Sebelumnya query notifikasi ditulis langsung di dalam NotificationsViewModel,
 * jadi ketika badge "belum dibaca" perlu angka yang sama, tidak ada satu tempat
 * pun untuk memakainya ulang. Semua akses `notifications` sekarang lewat sini.
 *
 * Notifikasi hanya ditulis server (aturan Firestore: `allow create, delete: if
 * false`); dari aplikasi satu-satunya perubahan yang sah adalah menandainya
 * sudah dibaca.
 */
@Singleton
class NotificationRepository @Inject constructor(private val db: FirebaseFirestore) {

    fun stream(userId: String): Flow<List<NotificationModel>> =
        db.collection(FirestorePaths.NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50).asFlow()

    /** Jumlah notifikasi yang belum dibaca — angka untuk badge di tombol lonceng. */
    fun streamUnreadCount(userId: String): Flow<Int> = callbackFlow {
        val registration = db.collection(FirestorePaths.NOTIFICATIONS)
            .whereEqualTo("userId", userId)
            .whereEqualTo("readAt", null)
            .addSnapshotListener { snapshot, error ->
                trySend(if (error != null) 0 else snapshot?.size() ?: 0)
            }
        awaitClose { registration.remove() }
    }

    suspend fun markRead(notificationId: String) {
        db.collection(FirestorePaths.NOTIFICATIONS).document(notificationId)
            .update("readAt", FieldValue.serverTimestamp()).await()
    }
}
