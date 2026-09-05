package com.jepretaja.app.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName
import com.google.firebase.Timestamp

/**
 * State machine status booking — HARUS sinkron persis dengan
 * backend/functions/src/bookingStatus.js. Ditulis sebagai String (bukan
 * enum Firestore) supaya kompatibel dengan status baru yang mungkin
 * ditambahkan di backend tanpa perlu update APK.
 */
object BookingStatus {
    const val DRAFT = "draft"
    const val PENDING_PAYMENT = "pending_payment"
    const val PAID = "paid"
    const val CONFIRMED = "confirmed"
    const val UPCOMING = "upcoming"
    const val IN_PROGRESS = "in_progress"
    const val COMPLETED = "completed"
    const val CUSTOMER_CONFIRMED = "customer_confirmed"
    const val FUNDS_RELEASED = "funds_released"
    const val REVIEWED = "reviewed"
    const val CANCELLED = "cancelled"
    const val REFUND_REQUESTED = "refund_requested"
    const val DISPUTED = "disputed"
}

data class PriceBreakdown(
    val packagePrice: Long = 0,
    val addOnsTotal: Long = 0,
    val travelFee: Long = 0,
    val discount: Long = 0,
    val voucherCode: String? = null,
    val platformFeePercent: Long = 0,
    val platformFee: Long = 0,
    val subtotal: Long = 0,
)

data class BookingModel(
    @DocumentId val bookingId: String = "",
    val customerId: String = "",
    val creatorId: String = "",
    val packageId: String = "",
    val packageName: String = "",
    val date: Timestamp? = null,
    val time: String = "",
    val location: String = "",
    val note: String? = null,
    val total: Long = 0,
    val priceBreakdown: PriceBreakdown? = null,
    val status: String = BookingStatus.DRAFT,
    @get:PropertyName("customerConfirmedAt") @set:PropertyName("customerConfirmedAt")
    var customerConfirmedAt: Timestamp? = null,
    val fundsReleasedAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
)
