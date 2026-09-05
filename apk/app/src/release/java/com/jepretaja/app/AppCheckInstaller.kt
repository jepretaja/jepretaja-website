package com.jepretaja.app

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Versi RILIS: memakai Play Integrity.
 *
 * Berkas ini sengaja terpisah dari versi debug supaya penyedia debug — yang
 * membuat App Check bisa dilewati dengan token yang didaftarkan manual — tidak
 * pernah ikut terkompilasi ke dalam aplikasi yang dipublikasikan.
 */
fun Application.installAppCheck() {
    FirebaseAppCheck.getInstance()
        .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
