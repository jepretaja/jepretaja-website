package com.jepretaja.app

import android.app.Application
import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp

/**
 * Entry point Hilt DI + inisialisasi Firebase App Check (section 45).
 * Debug provider dipakai di BuildConfig.DEBUG supaya bisa dites tanpa
 * signing release — WAJIB daftarkan debug token yang muncul di Logcat ke
 * Firebase Console > App Check > Manage debug tokens.
 */
@HiltAndroidApp
class JepretAjaApplication : Application() {

    companion object {
        // Dipakai AnalyticsService (object biasa, di luar graf Hilt) untuk
        // akses FirebaseAnalytics.getInstance(context) — pola pragmatis
        // yang stabil di semua versi Firebase SDK (tidak bergantung pada
        // resolusi artifact -ktx yang statusnya bisa berubah antar versi BOM).
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        // Pemasangan App Check dipisah per build type (lihat src/debug dan
        // src/release). Penyedia debug kini hanya ada di build debug, jadi ia
        // tidak bisa lagi disebut dari kode bersama ini — dan itu memang
        // tujuannya: kelas penerobos App Check tidak ikut ke aplikasi rilis.
        installAppCheck()

        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
    }
}
