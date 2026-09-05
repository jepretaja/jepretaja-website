package com.jepretaja.app.core.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Mengubah koordinat menjadi alamat yang bisa dibaca manusia.
 *
 * Dipakai tombol "Gunakan lokasi saya": GPS hanya menghasilkan angka lintang &
 * bujur, sementara yang perlu dibaca creator saat menerima booking adalah nama
 * jalan dan kota. Tanpa langkah ini, mengisi lokasi otomatis sama saja dengan
 * menuliskan "-6.2088, 106.8456" ke formulir — benar secara teknis, tapi tidak
 * berguna bagi siapa pun yang harus datang ke sana.
 */
object GeocoderHelper {

    /**
     * @return alamat singkat, atau null bila layanan geocoder tidak tersedia
     *         atau tidak menemukan apa pun. Pemanggil memperlakukan null
     *         sebagai "isi manual saja", bukan sebagai kegagalan fatal.
     */
    suspend fun alamatDari(context: Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale("id", "ID"))

        val hasil: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Sejak Android 13 versi blocking dilarang dipanggil dan melempar;
            // yang tersedia hanya varian callback.
            suspendCancellableCoroutine { lanjutan ->
                runCatching {
                    geocoder.getFromLocation(lat, lng, 1) { daftar ->
                        if (lanjutan.isActive) lanjutan.resume(daftar)
                    }
                }.onFailure { if (lanjutan.isActive) lanjutan.resume(null) }
            }
        } else {
            // Versi lama: panggilan memblokir, jadi wajib di luar main thread.
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(lat, lng, 1) }.getOrNull()
            }
        }

        val alamat = hasil?.firstOrNull() ?: return null
        return rangkai(alamat)
    }

    /**
     * Menyusun alamat dari bagian yang paling berguna saja.
     *
     * `getAddressLine(0)` sengaja tidak dipakai mentah-mentah: ia sering
     * memuat kode pos dan nama negara yang membuat kolom penuh oleh keterangan
     * yang tidak menolong siapa pun mencari tempatnya.
     */
    private fun rangkai(a: Address): String {
        val bagian = listOfNotNull(
            a.thoroughfare ?: a.featureName,   // nama jalan
            a.subLocality,                      // kelurahan/kawasan
            a.locality ?: a.subAdminArea,       // kota/kabupaten
            a.adminArea,                        // provinsi
        ).distinct().filter { it.isNotBlank() }

        return if (bagian.isEmpty()) {
            a.getAddressLine(0) ?: ""
        } else {
            bagian.joinToString(", ")
        }
    }
}
