package com.jepretaja.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jepretaja.app.core.util.CloudFunctions
import com.jepretaja.app.data.remote.ApiClient
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.asFlow
import com.jepretaja.app.data.model.BookingModel
import com.jepretaja.app.data.model.BookingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Status yang membuat sebuah tanggal dianggap sudah terpakai.
 *
 * `draft` dan `pending_payment` TIDAK termasuk secara sengaja: booking yang
 * belum dibayar bisa saja tidak pernah dilanjutkan, dan mengunci tanggal
 * karenanya berarti siapa pun bisa memblokir kalender seorang creator hanya
 * dengan membuat pesanan lalu meninggalkannya. `cancelled`, `refund_requested`,
 * dan `disputed` juga tidak mengunci — pekerjaannya tidak jadi berjalan.
 */
private val STATUS_MENGUNCI_TANGGAL = setOf(
    BookingStatus.PAID,
    BookingStatus.CONFIRMED,
    BookingStatus.UPCOMING,
    BookingStatus.IN_PROGRESS,
    BookingStatus.COMPLETED,
    BookingStatus.CUSTOMER_CONFIRMED,
    BookingStatus.FUNDS_RELEASED,
    BookingStatus.REVIEWED,
)

/**
 * PENTING: booking TIDAK PERNAH ditulis langsung ke Firestore dari APK —
 * Firestore Rules memblokir total create/update pada collection `bookings`
 * untuk client. Semua di bawah ini memanggil endpoint server yang
 * menghitung harga dan memvalidasi graf transisi status.
 *
 * Sebelumnya memakai Cloud Functions callable. Dipindah ke Vercel
 * Serverless Function (/api/app) karena Cloud Functions mensyaratkan paket
 * Blaze berbayar, sementara Firestore + Auth tetap gratis di paket Spark.
 * Nama aksinya tetap sama, jadi konstanta CloudFunctions masih dipakai.
 */
@Singleton
class BookingRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val api: ApiClient,
) {
    suspend fun createBooking(
        packageId: String, date: String, time: String, location: String, note: String?,
        addOnIds: List<String> = emptyList(), travelFee: Long = 0, voucherCode: String? = null,
    ): Map<String, Any?> {
        return api.call(
            CloudFunctions.CREATE_BOOKING,
            mapOf(
                "packageId" to packageId, "date" to date, "time" to time, "location" to location,
                "note" to note, "addOnIds" to addOnIds, "travelFee" to travelFee, "voucherCode" to voucherCode,
            )
        )
    }

    suspend fun previewPrice(
        packageId: String, addOnIds: List<String> = emptyList(), travelFee: Long = 0, voucherCode: String? = null,
    ): Map<String, Any?> {
        return api.call(
            CloudFunctions.PREVIEW_BOOKING_PRICE,
            mapOf("packageId" to packageId, "addOnIds" to addOnIds, "travelFee" to travelFee, "voucherCode" to voucherCode)
        )
    }

    suspend fun startService(bookingId: String) {
        api.call(CloudFunctions.START_SERVICE, mapOf("bookingId" to bookingId))
    }

    suspend fun markServiceCompleted(bookingId: String) {
        api.call(CloudFunctions.MARK_SERVICE_COMPLETED, mapOf("bookingId" to bookingId))
    }

    suspend fun confirmCompletion(bookingId: String) {
        api.call(CloudFunctions.CONFIRM_BOOKING_COMPLETION, mapOf("bookingId" to bookingId))
    }

    suspend fun cancelBooking(bookingId: String, reason: String? = null) {
        api.call(CloudFunctions.CANCEL_BOOKING, mapOf("bookingId" to bookingId, "reason" to reason))
    }

    suspend fun requestRefund(bookingId: String, reason: String) {
        api.call(CloudFunctions.REQUEST_REFUND, mapOf("bookingId" to bookingId, "reason" to reason))
    }

    suspend fun openDispute(bookingId: String, reason: String, evidence: List<String> = emptyList()): String {
        val data = api.call(
            CloudFunctions.OPEN_DISPUTE,
            mapOf("bookingId" to bookingId, "reason" to reason, "evidence" to evidence)
        )
        return data["disputeId"] as? String ?: ""
    }

    // --- Baca data (read-only, langsung dari Firestore — aman) ---

    /**
     * Tanggal yang sudah terpakai booking lain milik creator ini.
     *
     * Dipakai form booking untuk MEMATIKAN tanggalnya di kalender, bukan sekadar
     * menolaknya setelah ditekan. Sebelumnya satu-satunya yang dihindari adalah
     * tanggal yang diblokir creator sendiri, sementara tanggal yang sudah dipesan
     * orang lain tetap terbuka lebar — dua pelanggan bisa memesan hari yang sama
     * dan baru ketahuan setelah keduanya membayar.
     *
     * Ini pertahanan LAPIS PERTAMA, bukan satu-satunya: keputusan akhir tetap di
     * server saat createBooking, karena dua orang bisa menekan tombol pesan pada
     * detik yang sama dan tidak ada pemeriksaan di perangkat yang bisa menangkap
     * itu. Bila aturan Firestore tidak mengizinkan pembacaan ini, alirannya
     * memancarkan daftar kosong — kalendernya kembali seperti sebelumnya, tidak
     * ada yang rusak.
     */
    fun streamBookedDates(creatorId: String): Flow<Set<LocalDate>> =
        db.collection(FirestorePaths.BOOKINGS)
            .whereEqualTo("creatorId", creatorId)
            .asFlow<BookingModel>()
            .map { bookings ->
                bookings
                    .filter { it.status in STATUS_MENGUNCI_TANGGAL }
                    .mapNotNull { booking ->
                        runCatching {
                            booking.date?.toDate()?.toInstant()
                                ?.atZone(ZoneId.systemDefault())?.toLocalDate()
                        }.getOrNull()
                    }
                    .toSet()
            }

    fun streamCustomerBookings(customerId: String): Flow<List<BookingModel>> =
        db.collection(FirestorePaths.BOOKINGS)
            .whereEqualTo("customerId", customerId)
            .orderBy("createdAt", Query.Direction.DESCENDING).asFlow()

    fun streamCreatorBookings(creatorId: String): Flow<List<BookingModel>> =
        db.collection(FirestorePaths.BOOKINGS)
            .whereEqualTo("creatorId", creatorId)
            .orderBy("createdAt", Query.Direction.DESCENDING).asFlow()

    suspend fun getBooking(bookingId: String): BookingModel? =
        db.collection(FirestorePaths.BOOKINGS).document(bookingId).get().await().toObject(BookingModel::class.java)

    fun streamBooking(bookingId: String) =
        db.collection(FirestorePaths.BOOKINGS).document(bookingId).asFlow<BookingModel>()
}
