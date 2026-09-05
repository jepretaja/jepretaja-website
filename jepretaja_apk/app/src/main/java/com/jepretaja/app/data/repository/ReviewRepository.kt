package com.jepretaja.app.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.asFlow
import com.jepretaja.app.data.model.ReviewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ulasan pelanggan terhadap creator.
 *
 * Sebelumnya repository ini TIDAK ADA sama sekali: layar ulasan hanya membaca
 * koleksi `reviews` langsung dari Firestore, dan tidak ada satu pun jalan untuk
 * MENULIS ulasan. Padahal layar "Review Saya" sudah menjanjikannya secara
 * tertulis ("Setelah booking selesai, Anda bisa memberi ulasan untuk creator di
 * sini") — janji yang tidak pernah bisa ditepati karena fungsinya belum pernah
 * dibuat. Ini yang melengkapinya, sekalian memberi creator kemampuan membalas.
 */
@Singleton
class ReviewRepository @Inject constructor(private val db: FirebaseFirestore) {

    fun streamForCreator(creatorId: String): Flow<List<ReviewModel>> =
        db.collection(FirestorePaths.REVIEWS)
            .whereEqualTo("creatorId", creatorId)
            .whereEqualTo("status", "published")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .asFlow()

    fun streamByCustomer(customerId: String): Flow<List<ReviewModel>> =
        db.collection(FirestorePaths.REVIEWS)
            .whereEqualTo("customerId", customerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .asFlow()

    /** Ulasan yang sudah ditulis untuk satu booking, kalau ada. */
    suspend fun forBooking(bookingId: String): ReviewModel? =
        db.collection(FirestorePaths.REVIEWS)
            .whereEqualTo("bookingId", bookingId)
            .limit(1).get().await()
            .documents.firstOrNull()?.toObject(ReviewModel::class.java)

    /**
     * Kirim ulasan untuk satu booking.
     *
     * Id dokumen sengaja memakai bookingId, bukan id acak: satu booking hanya
     * boleh punya satu ulasan, dan memakai bookingId sebagai kunci membuat
     * aturan itu berlaku dengan sendirinya — menekan kirim dua kali menimpa
     * ulasan yang sama, bukan membuat ulasan kembar.
     *
     * `rating` creator TIDAK dihitung ulang di sini. Rata-rata rating adalah
     * angka yang menentukan urutan di pencarian, jadi ia hanya boleh ditulis
     * server (aturan Firestore pun menolak klien menyentuhnya).
     */
    suspend fun submitReview(
        bookingId: String,
        customerId: String,
        customerName: String,
        creatorId: String,
        rating: Int,
        text: String,
    ) {
        db.collection(FirestorePaths.REVIEWS).document(bookingId).set(
            mapOf(
                "bookingId" to bookingId,
                "customerId" to customerId,
                "customerName" to customerName,
                "creatorId" to creatorId,
                "rating" to rating.toLong().coerceIn(1L, 5L),
                "text" to text,
                "status" to "published",
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
    }

    /** Balasan creator atas ulasan yang diterimanya. */
    suspend fun replyToReview(reviewId: String, reply: String) {
        db.collection(FirestorePaths.REVIEWS).document(reviewId)
            .update("creatorReply", reply).await()
    }
}
