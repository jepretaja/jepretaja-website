package com.jepretaja.app.core.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pemotongan video dan pengambilan frame sampul.
 *
 * Menggantikan pemilih sampul lama yang hanya menawarkan empat frame tetap:
 * sekarang creator menggeser penanda ke posisi mana pun, dan frame di posisi
 * itulah yang jadi sampul.
 */
object VideoTools {

    /** Frame pada [posisiMs], ditulis ke cache dan dikembalikan sebagai Uri. */
    suspend fun frameAt(context: Context, video: Uri, posisiMs: Long): Uri? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, video)
            // OPTION_CLOSEST, bukan CLOSEST_SYNC: saat penanda digeser, frame
            // yang diminta harus benar-benar yang di posisi itu. CLOSEST_SYNC
            // melompat ke keyframe terdekat, sehingga geseran kecil tidak
            // mengubah gambar sama sekali dan penggesernya terasa rusak.
            val frame: Bitmap = retriever.getFrameAtTime(
                posisiMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST,
            ) ?: return@withContext null

            val file = File(context.cacheDir, "cover_scrub.jpg")
            FileOutputStream(file).use { out -> frame.compress(Bitmap.CompressFormat.JPEG, 88, out) }
            // Nama berkasnya tetap, jadi Uri-nya juga sama setiap kali. Ditambah
            // parameter waktu supaya Coil tidak menampilkan gambar lama dari
            // cache-nya sendiri.
            "${file.toUri()}?t=$posisiMs".toUri()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Memotong video ke rentang [mulaiMs]..[selesaiMs].
     *
     * Benar-benar meng-encode ulang berkasnya, bukan sekadar menyimpan angka
     * potongan. Alasannya: yang diunggah harus berupa berkas yang sudah pendek,
     * supaya kuota dan waktu unggah creator ikut hemat — menyimpan penanda
     * potong lalu tetap mengunggah video utuh tidak menyelesaikan masalah yang
     * membuat fitur ini dibutuhkan.
     *
     * Mengembalikan null kalau pemotongan gagal; pemanggil lalu memakai berkas
     * aslinya, karena gagal memotong bukan alasan membatalkan unggahan.
     */
    @OptIn(UnstableApi::class)
    suspend fun trim(context: Context, video: Uri, mulaiMs: Long, selesaiMs: Long): Uri? =
        withContext(Dispatchers.Main) {
            val keluaran = File(context.cacheDir, "trim_${System.currentTimeMillis()}.mp4")
            val item = MediaItem.Builder()
                .setUri(video)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(mulaiMs)
                        .setEndPositionMs(selesaiMs)
                        .build()
                )
                .build()

            runCatching {
                suspendCancellableCoroutine { lanjutan ->
                    val transformer = Transformer.Builder(context)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) {
                                lanjutan.resume(keluaran.toUri())
                            }

                            override fun onError(
                                composition: Composition,
                                result: ExportResult,
                                exception: ExportException,
                            ) {
                                lanjutan.resumeWithException(exception)
                            }
                        })
                        .build()

                    transformer.start(item, keluaran.absolutePath)
                    lanjutan.invokeOnCancellation { runCatching { transformer.cancel() } }
                }
            }.getOrNull()
        }
}
