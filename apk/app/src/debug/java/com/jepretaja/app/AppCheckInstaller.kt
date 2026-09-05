package com.jepretaja.app

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Versi DEBUG: memakai penyedia debug App Check.
 *
 * Token debug yang muncul di Logcat harus didaftarkan di Firebase Console >
 * App Check > Manage debug tokens, kalau tidak seluruh permintaan Firestore
 * akan ditolak saat App Check ditegakkan.
 */
fun Application.installAppCheck() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
