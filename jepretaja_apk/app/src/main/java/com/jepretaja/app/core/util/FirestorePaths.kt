package com.jepretaja.app.core.util

/**
 * Nama collection Firestore — HARUS sinkron persis dengan sisi web:
 * `src/firebase/paths.js` (daftar koleksi panel admin) dan `firestore.rules`,
 * keduanya di project jepretaja_admin.
 */
object FirestorePaths {
    const val USERS = "users"
    const val CREATORS = "creators"
    const val CREATOR_VERIFICATIONS = "creator_verifications"
    const val EXPLORE_POSTS = "explore_posts"
    const val EXPLORE_COMMENTS = "explore_comments"
    const val EXPLORE_LIKES = "explore_likes"
    const val EXPLORE_SAVES = "explore_saves"
    const val EXPLORE_COMMENT_LIKES = "explore_comment_likes"
    const val FOLLOWS = "follows"
    const val PORTFOLIOS = "portfolios"
    const val PACKAGES = "packages"
    const val BOOKINGS = "bookings"
    const val CHATS = "chats"
    const val MESSAGES = "messages"
    const val PAYMENTS = "payments"
    const val ESCROW_TRANSACTIONS = "escrow_transactions"
    const val WALLETS = "wallets"
    const val WALLET_TRANSACTIONS = "wallet_transactions"
    const val WITHDRAWALS = "withdrawals"
    const val REFUNDS = "refunds"
    const val DISPUTES = "disputes"
    const val REVIEWS = "reviews"
    const val NOTIFICATIONS = "notifications"
    const val REPORTS = "reports"
    const val CATEGORIES = "categories"
    const val PROMOTIONS = "promotions"
    const val SETTINGS = "settings"
    const val AVAILABILITY_BLOCKS = "availability_blocks"
    const val BLOCKED_USERS = "blocked_users"
}

/** Nama aksi server — HARUS sinkron persis dengan kunci object HANDLERS di
 * api/app.js pada project jepretaja_admin (dipanggil lewat ApiClient, bukan
 * Cloud Functions callable). */
object CloudFunctions {
    const val CREATE_BOOKING = "createBooking"
    const val PREVIEW_BOOKING_PRICE = "previewBookingPrice"
    const val START_SERVICE = "startService"
    const val MARK_SERVICE_COMPLETED = "markServiceCompleted"
    const val CONFIRM_BOOKING_COMPLETION = "confirmBookingCompletion"
    const val CANCEL_BOOKING = "cancelBooking"
    const val REQUEST_REFUND = "requestRefund"
    const val OPEN_DISPUTE = "openDispute"
    const val CREATE_PAYMENT_ORDER = "createPaymentOrder"
    const val BLOCK_AVAILABILITY_DATE = "blockAvailabilityDate"
    const val UNBLOCK_AVAILABILITY_DATE = "unblockAvailabilityDate"
    const val REQUEST_WITHDRAWAL = "requestWithdrawal"
    const val NOTIFY_INTERACTION = "notifyInteraction"
}

object AppConstants {
    val SERVICE_CATEGORIES = listOf(
        "Wedding", "Prewedding", "Event", "Wisuda", "Couple", "Family", "Product", "Commercial", "Video",
    )
    val EXPLORE_TABS = listOf(
        "For You", "Following", "Wedding", "Prewedding", "Event", "Wisuda", "Couple", "Family", "Product", "Commercial", "Video", "Nearby",
    )
    const val MIN_WITHDRAWAL_DEFAULT = 100_000L

    /**
     * Batas unggahan.
     *
     * Sebelumnya tidak ada batas apa pun: video sepuluh menit ikut diunggah
     * sampai selesai, menghabiskan kuota creator dan penyimpanan, lalu tetap
     * ditolak moderasi karena bukan format yang dipakai feed.
     */
    const val MAX_VIDEO_DURATION_SECONDS = 180L
    const val MAX_VIDEO_SIZE_MB = 200L
    const val MAX_PHOTO_SIZE_MB = 15L
    const val MIN_MEDIA_WIDTH = 480
}
