package com.jepretaja.app.core.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Menyalin media yang dipilih ke penyimpanan milik aplikasi sebelum diunggah.
 *
 * **Ini yang membuat unggahan foto/video akhirnya berhasil.** Pemilih berkas
 * (`ActivityResultContracts.GetContent`) mengembalikan `content://` beserta izin
 * baca SEMENTARA — izin itu menempel pada Activity yang menerimanya dan hilang
 * begitu Activity-nya selesai atau proses aplikasinya dimatikan sistem. Berbeda
 * dari `OpenDocument`, izin dari `GetContent` juga tidak bisa dipermanenkan
 * dengan `takePersistableUriPermission`.
 *
 * Sementara itu seluruh unggahan dititipkan ke WorkManager, yang justru
 * dirancang untuk berjalan BELAKANGAN — mungkin beberapa menit kemudian, mungkin
 * setelah aplikasinya sudah dimatikan. Saat worker akhirnya jalan dan memanggil
 * `openInputStream`, izinnya sudah tidak ada lagi: `SecurityException`, unggahan
 * gagal, dan satu-satunya jejak yang dilihat creator hanyalah karyanya kembali
 * ke Draft tanpa penjelasan.
 *
 * Menyalin isinya lebih dulu memutus ketergantungan itu sepenuhnya: worker
 * membaca berkas milik aplikasi sendiri, yang tidak butuh izin siapa pun dan
 * tidak bisa hilang di tengah jalan. Sebagai bonus, ukuran berkasnya jadi pasti,
 * sehingga `setFixedLengthStreamingMode` di StorageService tidak lagi berisiko
 * salah hitung pada penyedia media yang melaporkan panjang berbeda dari isi yang
 * sebenarnya terbaca.
 */
object MediaUnggahan {

    private const val FOLDER = "upload_queue"

    /**
     * Menyalin satu media, mengembalikan `file://` yang aman dibaca kapan pun.
     *
     * Disalin per potongan 64 KB, tidak pernah dimuat penuh ke memori — video
     * ratusan megabita tetap aman di HP kelas bawah.
     */
    suspend fun salin(context: Context, uri: Uri, extension: String): Uri = withContext(Dispatchers.IO) {
        // Berkas yang sudah milik kita sendiri tidak perlu disalin lagi. Ini
        // terjadi pada video yang baru dipotong VideoTools dan pada hasil
        // rekaman kamera dalam aplikasi.
        if (uri.scheme == "file" && uri.path?.contains("/$FOLDER/") == true) return@withContext uri

        val dir = File(context.filesDir, FOLDER).apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.$extension")
        try {
            context.contentResolver.openInputStream(uri)?.use { masuk ->
                target.outputStream().use { keluar -> masuk.copyTo(keluar, 64 * 1024) }
            } ?: throw IOException("Media tidak bisa dibaca. Pilih ulang dari galeri.")
        } catch (e: Exception) {
            // Salinan setengah jadi lebih berbahaya daripada tidak ada salinan:
            // ia akan terunggah sebagai berkas rusak tanpa ada yang menyadari.
            target.delete()
            throw e
        }
        if (target.length() == 0L) {
            target.delete()
            throw IOException("Berkas yang dipilih kosong. Coba pilih media lain.")
        }
        Uri.fromFile(target)
    }

    /**
     * Menghapus salinan yang sudah selesai diunggah.
     *
     * Hanya menyentuh berkas di dalam folder milik antrean — string apa pun di
     * luar itu diabaikan, supaya kesalahan pemanggilan tidak pernah bisa
     * menghapus berkas pengguna.
     */
    fun bersihkan(context: Context, uris: List<String>) {
        val dir = File(context.filesDir, FOLDER)
        uris.forEach { teks ->
            runCatching {
                val berkas = File(Uri.parse(teks).path ?: return@runCatching)
                if (berkas.parentFile?.canonicalPath == dir.canonicalPath) berkas.delete()
            }
        }
    }
}
