package com.jepretaja.app.core.navigation

/** Nama route — setara AppRoutes di versi Flutter. */
object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val CHOOSE_ACCESS = "choose_access"
    const val LOGIN = "login"
    const val REGISTER_CUSTOMER = "register_customer"
    const val REGISTER_CREATOR = "register_creator"

    const val HOME = "home"
    const val EXPLORE = "explore"
    const val EXPLORE_DETAIL = "explore/{postId}"
    const val SEARCH = "search"
    const val SEARCH_RESULT = "search/result"
    const val NEARBY = "nearby"
    const val TAG_FEED = "tag/{tag}?category={category}"

    const val CREATOR_PROFILE = "creator/{creatorId}"
    const val PACKAGE_DETAIL = "package/{packageId}"
    const val REVIEWS = "creator/{creatorId}/reviews"
    const val WRITE_REVIEW = "booking/{bookingId}/review/{creatorId}"

    const val BOOKING_FORM = "booking/form/{packageId}"
    const val BOOKING_CONFIRMATION = "booking/confirmation/{bookingId}"
    const val BOOKING_DETAIL = "booking/{bookingId}"
    const val PAYMENT = "payment/{bookingId}"
    const val PAYMENT_RESULT = "payment/{bookingId}/result"

    const val CHAT_LIST = "chat"
    const val CHAT_ROOM = "chat/{chatId}"

    const val NOTIFICATIONS = "notifications"
    const val MY_BOOKINGS = "my_bookings"
    const val MY_REVIEWS = "my_reviews"
    const val MY_REPORTS = "my_reports"
    const val FAVORITES = "favorites"
    const val PROFILE = "profile"
    const val EDIT_PROFILE = "profile/edit"
    const val SAVED_POSTS = "profile/saved"
    const val FOLLOW_LIST = "follows/{targetUserId}/{tab}"
    const val HELP = "help"

    const val CREATOR_DASHBOARD = "creator_dashboard"
    const val CREATOR_UPLOAD = "creator_upload"
    const val MY_WORKS = "creator_my_works"
    const val INTERESTS = "interests"
    const val WATCH_HISTORY = "watch_history"
    const val CREATOR_PORTFOLIO_MANAGEMENT = "creator_portfolio_management"
    const val CREATOR_PACKAGE_MANAGEMENT = "creator_package_management"
    const val CREATOR_BOOKING_MANAGEMENT = "creator_booking_management"
    const val CREATOR_WALLET = "creator_wallet"
    const val CREATOR_WITHDRAWAL = "creator_withdrawal"
    const val CREATOR_BANK_ACCOUNT = "creator_bank_account"
    const val CREATOR_AVAILABILITY = "creator_availability"
    const val CREATOR_SETTINGS = "creator_settings"

    fun creatorProfile(creatorId: String) = "creator/$creatorId"
    fun packageDetail(packageId: String) = "package/$packageId"
    fun bookingForm(packageId: String) = "booking/form/$packageId"
    fun bookingConfirmation(bookingId: String) = "booking/confirmation/$bookingId"
    fun bookingDetail(bookingId: String) = "booking/$bookingId"
    fun payment(bookingId: String) = "payment/$bookingId"
    fun paymentResult(bookingId: String) = "payment/$bookingId/result"
    fun chatRoom(chatId: String) = "chat/$chatId"
    fun exploreDetail(postId: String) = "explore/$postId"
    /** [isCategory] membedakan halaman kategori resmi dari tagar bebas. */
    fun tagFeed(tag: String, isCategory: Boolean = false) =
        "tag/${java.net.URLEncoder.encode(tag, "UTF-8")}?category=$isCategory"
    /** [tab]: 0 = Mengikuti, 1 = Pengikut. */
    fun followList(targetUserId: String, tab: Int) = "follows/$targetUserId/$tab"
    fun reviews(creatorId: String) = "creator/$creatorId/reviews"
    fun writeReview(bookingId: String, creatorId: String) = "booking/$bookingId/review/$creatorId"
}
