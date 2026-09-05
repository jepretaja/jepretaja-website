package com.jepretaja.app.core.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memantau ada/tidaknya koneksi internet secara langsung.
 *
 * Sebelumnya aplikasi tidak punya cara apa pun mengetahui keadaan jaringan.
 * Akibatnya saat internet mati, layar-layar hanya diam kosong atau memutar
 * skeleton tanpa akhir — terbaca seperti aplikasi rusak atau data yang memang
 * belum ada, padahal masalahnya cuma tidak ada sinyal.
 *
 * Yang diperiksa bukan sekadar "ada jaringan terhubung", tapi
 * NET_CAPABILITY_VALIDATED — yaitu Android sudah membuktikan jaringan itu
 * benar-benar bisa menjangkau internet. Bedanya terasa pada kasus yang paling
 * membingungkan pengguna: WiFi tersambung penuh tapi tidak ada internet
 * (hotspot hotel/kafe yang belum login, kuota habis). Tanpa pembedaan itu,
 * aplikasi akan bersikeras "online" sementara semua permintaan gagal.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    /** true = ada internet yang sudah tervalidasi. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val cm = manager
        if (cm == null) {
            // Tidak bisa memeriksa: lebih baik dianggap online daripada
            // menampilkan peringatan palsu terus-menerus.
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        fun periksaSekarang(): Boolean {
            val jaringan = cm.activeNetwork ?: return false
            val kemampuan = cm.getNetworkCapabilities(jaringan) ?: return false
            return kemampuan.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                kemampuan.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        trySend(periksaSekarang())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(periksaSekarang()) }
            override fun onLost(network: Network) { trySend(periksaSekarang()) }
            override fun onUnavailable() { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }
        }

        val permintaan = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        // Pendaftaran callback bisa melempar pada perangkat tertentu bila izin
        // tidak tersedia; kegagalannya tidak boleh menjatuhkan seluruh aplikasi.
        val terdaftar = runCatching { cm.registerNetworkCallback(permintaan, callback) }.isSuccess

        awaitClose {
            if (terdaftar) runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()
}
