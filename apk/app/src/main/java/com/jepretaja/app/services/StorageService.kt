package com.jepretaja.app.services

import android.content.Context
import android.net.Uri
import com.jepretaja.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Hasil upload video: URL video plus poster/thumbnail yang dibuat otomatis. */
data class UploadedVideo(val url: String, val thumbnailUrl: String)

/**
 * Upload media Explore / Portfolio / Chat / Verifikasi ke **Cloudinary**.
 *
 * Kenapa bukan Firebase Storage lagi? Sejak 3 Februari 2026 Cloud Storage for
 * Firebase mewajibkan paket Blaze (harus menautkan kartu), sementara Cloudinary
 * memberi 25 GB gratis tanpa kartu. Firestore & Authentication tetap di Firebase
 * karena keduanya masih gratis penuh di paket Spark.
 *
 * Dipakai mode **unsigned upload preset**: APK hanya membawa cloud name dan
 * nama preset, tidak pernah membawa API secret. Ini penting — API secret di
 * dalam APK bisa diekstrak siapa saja dan dipakai untuk menghapus seluruh media
 * Anda. Batasan ukuran/format/folder diatur di sisi Cloudinary lewat preset.
 *
 * Sengaja memakai HttpURLConnection bawaan Android, bukan SDK Cloudinary atau
 * OkHttp, supaya tidak menambah dependensi baru ke APK.
 */
@Singleton
class StorageService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private fun endpoint(resourceType: String): URL {
        if (BuildConfig.CLOUDINARY_CLOUD_NAME.isBlank() || BuildConfig.CLOUDINARY_UPLOAD_PRESET.isBlank()) {
            throw IOException(
                "Cloudinary belum dikonfigurasi. Isi CLOUDINARY_CLOUD_NAME dan " +
                    "CLOUDINARY_UPLOAD_PRESET di gradle.properties (atau sebagai secret GitHub Actions)."
            )
        }
        return URL("https://api.cloudinary.com/v1_1/${BuildConfig.CLOUDINARY_CLOUD_NAME}/$resourceType/upload")
    }

    /**
     * Kirim satu berkas sebagai multipart/form-data.
     *
     * Berkas dibaca dan ditulis per potongan 64 KB, tidak pernah dimuat penuh
     * ke memori — video 200 MB tetap aman di HP kelas bawah.
     */
    private suspend fun upload(
        uri: Uri,
        folder: String,
        fileName: String,
        resourceType: String,
        onProgress: ((Double) -> Unit)?,
    ): JSONObject = withContext(Dispatchers.IO) {
        val boundary = "----JepretAja" + UUID.randomUUID().toString()
        val resolver = context.contentResolver

        val totalBytes: Long = try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: Exception) {
            -1L
        }

        val header = StringBuilder()
            .append("--").append(boundary).append("\r\n")
            .append("Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n")
            .append(BuildConfig.CLOUDINARY_UPLOAD_PRESET).append("\r\n")
            .append("--").append(boundary).append("\r\n")
            .append("Content-Disposition: form-data; name=\"folder\"\r\n\r\n")
            .append(folder).append("\r\n")
            .append("--").append(boundary).append("\r\n")
            .append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(fileName).append("\"\r\n")
            .append("Content-Type: application/octet-stream\r\n\r\n")
            .toString()
            .toByteArray(Charsets.UTF_8)
        val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        val conn = endpoint(resourceType).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.useCaches = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            if (totalBytes >= 0L) {
                conn.setFixedLengthStreamingMode(header.size.toLong() + totalBytes + footer.size.toLong())
            } else {
                conn.setChunkedStreamingMode(0)
            }

            var terkirim = 0L
            conn.outputStream.use { out ->
                out.write(header)
                val input = resolver.openInputStream(uri)
                    ?: throw IOException("Berkas tidak bisa dibaca. Coba pilih ulang dari galeri.")
                input.use { ins ->
                    val buffer = ByteArray(64 * 1024)
                    var read = ins.read(buffer)
                    while (read >= 0) {
                        if (read > 0) {
                            out.write(buffer, 0, read)
                            terkirim += read.toLong()
                            if (totalBytes > 0L) {
                                onProgress?.invoke(terkirim.toDouble() / totalBytes.toDouble())
                            }
                        }
                        read = ins.read(buffer)
                    }
                }
                out.write(footer)
                out.flush()

                // Beberapa penyedia media (Google Photos, berkas yang masih di
                // awan) melaporkan panjang yang berbeda dari isi yang benar-benar
                // terbaca. Dalam mode fixed-length, selisih itu muncul sebagai
                // "unexpected end of stream" — pesan yang tidak berarti apa-apa
                // bagi pengguna. Diperiksa di sini supaya sebabnya tersurat.
                if (totalBytes > 0L && terkirim != totalBytes) {
                    throw IOException(
                        "Berkas berubah saat diunggah (terbaca $terkirim dari $totalBytes byte). " +
                            "Salin dulu fotonya ke galeri perangkat, lalu coba unggah lagi."
                    )
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

            if (code !in 200..299) {
                // Cloudinary membalas { "error": { "message": "..." } } — pakai
                // pesan itu kalau ada, karena jauh lebih jelas bagi pengguna
                // daripada sekadar kode HTTP.
                val detail = try {
                    JSONObject(body).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    if (body.length > 200) body.substring(0, 200) else body
                }
                throw IOException("Upload gagal (HTTP $code): $detail")
            }

            onProgress?.invoke(1.0)
            JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun secureUrlOf(json: JSONObject): String {
        val url = json.optString("secure_url")
        if (url.isBlank()) {
            throw IOException("Cloudinary tidak mengembalikan URL berkas. Periksa pengaturan upload preset.")
        }
        return url
    }

    /**
     * Poster video dibuat Cloudinary secara on-the-fly: sisipkan transformasi
     * `so_0` (detik ke-0) dan ganti ekstensi jadi .jpg. Tidak perlu upload
     * terpisah dan tidak perlu pipeline video-processor sendiri.
     */
    private fun videoThumbnailUrl(secureUrl: String): String {
        val marker = "/upload/"
        val index = secureUrl.indexOf(marker)
        if (index < 0) return secureUrl
        val head = secureUrl.substring(0, index + marker.length)
        val tail = secureUrl.substring(index + marker.length)
        return head + "so_0/" + tail.substringBeforeLast('.') + ".jpg"
    }

    suspend fun uploadExploreMedia(
        creatorId: String,
        uri: Uri,
        extension: String,
        onProgress: ((Double) -> Unit)? = null,
    ): String {
        val json = upload(uri, "explore/$creatorId", "${UUID.randomUUID()}.$extension", "image", onProgress)
        return secureUrlOf(json)
    }

    /**
     * Upload video Explore. Berbeda dengan versi Firebase sebelumnya yang hanya
     * menaruh berkas mentah lalu menunggu Cloud Run memprosesnya, Cloudinary
     * langsung mengembalikan URL siap putar beserta thumbnail — jadi post tidak
     * pernah tersangkut di status "processing".
     */
    suspend fun uploadExploreVideo(
        creatorId: String,
        postId: String,
        uri: Uri,
        extension: String,
        onProgress: ((Double) -> Unit)? = null,
    ): UploadedVideo {
        val json = upload(uri, "explore_videos/$creatorId/$postId", "original.$extension", "video", onProgress)
        val url = secureUrlOf(json)
        return UploadedVideo(url = url, thumbnailUrl = videoThumbnailUrl(url))
    }

    /**
     * Media portfolio.
     *
     * [resourceType] harus "video" untuk berkas video — Cloudinary punya
     * endpoint berbeda per jenis, dan mengunggah video ke endpoint gambar
     * ditolak. Sebelumnya jenisnya dipaku "image" dan ekstensinya dipaku "jpg"
     * di pemanggil, jadi portfolio tidak pernah bisa memuat video sama sekali.
     */
    suspend fun uploadPortfolioMedia(
        creatorId: String,
        uri: Uri,
        extension: String,
        resourceType: String = "image",
        onProgress: ((Double) -> Unit)? = null,
    ): String {
        val json = upload(uri, "portfolio/$creatorId", "${UUID.randomUUID()}.$extension", resourceType, onProgress)
        return secureUrlOf(json)
    }

    /** Foto profil pengguna (konsumen maupun creator). */
    suspend fun uploadProfilePhoto(userId: String, uri: Uri): String {
        val json = upload(uri, "avatars/$userId", "${UUID.randomUUID()}.jpg", "image", null)
        return secureUrlOf(json)
    }

    suspend fun uploadChatAttachment(chatId: String, uri: Uri): String {
        val json = upload(uri, "chat/$chatId", "${UUID.randomUUID()}.jpg", "image", null)
        return secureUrlOf(json)
    }

    /**
     * Dokumen identitas creator. Preset Cloudinary untuk folder ini sebaiknya
     * diatur ke mode "Authenticated" di dashboard supaya URL-nya tidak bisa
     * ditebak dan dibuka publik.
     */
    suspend fun uploadVerificationDocument(creatorId: String, uri: Uri, extension: String): String {
        val json = upload(uri, "verifications/$creatorId", "${UUID.randomUUID()}.$extension", "image", null)
        return secureUrlOf(json)
    }
}
