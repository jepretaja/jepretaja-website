package com.jepretaja.app.core.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Ringkasan media terpilih, dipakai layar unggah untuk memeriksa kelayakan. */
data class MediaTerpilih(
    val durationSeconds: Long? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
)

/**
 * Membaca durasi, ukuran, dan resolusi media SEBELUM diunggah.
 *
 * Pemeriksaan di depan jauh lebih murah daripada menolak di belakang: creator
 * di jaringan seluler tidak perlu menghabiskan kuota mengunggah 200 MB hanya
 * untuk diberi tahu bahwa videonya terlalu panjang. Sekaligus mengisi
 * `durationSeconds` yang sudah lama ada di model post tapi tidak pernah ditulis
 * satu kali pun.
 */
object MediaInfo {

    suspend fun baca(context: Context, uri: Uri, isVideo: Boolean): MediaTerpilih =
        withContext(Dispatchers.IO) {
            val ukuran = runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    val kolom = c.getColumnIndex(OpenableColumns.SIZE)
                    if (kolom >= 0 && c.moveToFirst() && !c.isNull(kolom)) c.getLong(kolom) else null
                }
            }.getOrNull()

            if (!isVideo) {
                val opsi = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it, null, opsi)
                    }
                }
                return@withContext MediaTerpilih(
                    durationSeconds = null,
                    sizeBytes = ukuran,
                    width = opsi.outWidth.takeIf { it > 0 },
                    height = opsi.outHeight.takeIf { it > 0 },
                )
            }

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                fun meta(key: Int) = retriever.extractMetadata(key)?.toLongOrNull()
                MediaTerpilih(
                    durationSeconds = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let { it / 1000 },
                    sizeBytes = ukuran,
                    width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt(),
                    height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt(),
                )
            } catch (e: Exception) {
                // Format yang tidak terbaca tidak langsung dianggap gagal; batas
                // ukuran masih bisa diperiksa dari hasil query di atas.
                MediaTerpilih(sizeBytes = ukuran)
            } finally {
                runCatching { retriever.release() }
            }
        }

    /** Alasan penolakan, atau null kalau medianya layak diunggah. */
    fun alasanDitolak(info: MediaTerpilih, isVideo: Boolean): String? {
        val mb = info.sizeBytes?.let { it / 1024 / 1024 }
        if (isVideo) {
            val durasi = info.durationSeconds
            if (durasi != null && durasi > AppConstants.MAX_VIDEO_DURATION_SECONDS) {
                return "Video maksimal ${AppConstants.MAX_VIDEO_DURATION_SECONDS / 60} menit. " +
                    "Video ini ${durasi / 60} menit ${durasi % 60} detik."
            }
            if (mb != null && mb > AppConstants.MAX_VIDEO_SIZE_MB) {
                return "Ukuran video maksimal ${AppConstants.MAX_VIDEO_SIZE_MB} MB. Video ini $mb MB."
            }
        } else if (mb != null && mb > AppConstants.MAX_PHOTO_SIZE_MB) {
            return "Ukuran foto maksimal ${AppConstants.MAX_PHOTO_SIZE_MB} MB. Foto ini $mb MB."
        }

        val lebar = info.width
        if (lebar != null && lebar < AppConstants.MIN_MEDIA_WIDTH) {
            return "Resolusi terlalu kecil (lebar ${lebar}px). Minimal ${AppConstants.MIN_MEDIA_WIDTH}px " +
                "supaya karyamu tidak pecah saat ditampilkan penuh layar."
        }
        return null
    }

    /**
     * Uri tujuan untuk hasil kamera.
     *
     * Wajib lewat FileProvider: sejak Android 7, memberikan Uri `file://` ke
     * aplikasi lain memicu FileUriExposedException dan kameranya tertutup tanpa
     * penjelasan apa pun.
     */
    fun uriTangkapan(context: Context, namaBerkas: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileTangkapan(context, namaBerkas))

    /**
     * Berkas tujuan di cache.
     *
     * CameraX menulis langsung ke File, tidak lewat FileProvider — pembungkus
     * content:// hanya dibutuhkan saat berkasnya diserahkan ke APLIKASI LAIN.
     */
    fun fileTangkapan(context: Context, namaBerkas: String): File {
        val file = File(context.cacheDir, namaBerkas)
        file.parentFile?.mkdirs()
        return file
    }
}
