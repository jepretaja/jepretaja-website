package com.jepretaja.app.services

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.jepretaja.app.JepretAjaApplication

/** Event tracking (section 50 & 51) — setara AnalyticsService di versi
 * Flutter. Object statis dengan FirebaseAnalytics.getInstance(context) —
 * API stabil sejak versi awal Firebase SDK, tidak bergantung artifact -ktx. */
object AnalyticsService {
    private val analytics: FirebaseAnalytics get() = FirebaseAnalytics.getInstance(JepretAjaApplication.appContext)

    fun logCreatorView(creatorId: String) = logEvent("creator_view", "creator_id" to creatorId)
    fun logPackageView(packageId: String) = logEvent("package_view", "package_id" to packageId)
    fun logExploreView(postId: String) = logEvent("explore_view", "post_id" to postId)
    fun logBookingStarted(packageId: String) = logEvent("booking_started", "package_id" to packageId)
    fun logBookingCreated(bookingId: String, total: Long) = logEvent("booking_created", "booking_id" to bookingId, "value" to total)
    fun logPaymentStarted(bookingId: String) = logEvent("payment_started", "booking_id" to bookingId)
    fun logBookingCompleted(bookingId: String) = logEvent("booking_completed", "booking_id" to bookingId)
    fun logWithdrawalRequested(amount: Long) = logEvent("withdrawal_requested", "value" to amount)

    private fun logEvent(name: String, vararg params: Pair<String, Any>) {
        val bundle = Bundle()
        params.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putInt(key, value)
                is Double -> bundle.putDouble(key, value)
            }
        }
        analytics.logEvent(name, bundle)
    }
}
