package com.jepretaja.app.data.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jepretaja.app.core.util.CaptionParser
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.MediaUnggahan
import com.jepretaja.app.data.local.AppPreferences
import com.jepretaja.app.data.local.DraftTersimpan
import com.jepretaja.app.services.StorageService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/**
 * Mengunggah satu post di latar belakang.
 *
 * Sebelumnya unggahan berjalan di viewModelScope layar unggah: menutup layar
 * atau berpindah aplikasi cukup lama membuat prosesnya mati, dan creator harus
 * mengulang dari nol — paling menyakitkan tepat pada video besar yang paling
 * lama diunggah.
 *
 * Sengaja TIDAK memakai Hilt (@HiltWorker). Dua ketergantungannya bisa dibuat
 * langsung — StorageService hanya butuh Context, dan Firestore punya instance
 * global — sehingga menambah HiltWorkerFactory beserta konfigurasi khusus di
 * Application hanya menambah bagian yang bisa salah tanpa keuntungan apa pun.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val creatorId = inputData.getString(KEY_CREATOR_ID) ?: return Result.failure()
        val creatorName = inputData.getString(KEY_CREATOR_NAME).orEmpty()
        val uris = inputData.getStringArray(KEY_URIS)?.toList().orEmpty()
        if (uris.isEmpty()) return Result.failure()

        val isVideo = inputData.getBoolean(KEY_IS_VIDEO, false)
        val caption = inputData.getString(KEY_CAPTION).orEmpty()
        val category = inputData.getString(KEY_CATEGORY).orEmpty()
        val target = inputData.getString(KEY_TARGET) ?: "Explore"
        val location = inputData.getString(KEY_LOCATION)
        val commentPolicy = inputData.getString(KEY_COMMENT_POLICY) ?: "all"
        val allowSave = inputData.getBoolean(KEY_ALLOW_SAVE, true)
        val durasi = inputData.getLong(KEY_DURATION, 0L).takeIf { it > 0 }
        val coverUri = inputData.getString(KEY_COVER)
        val packageId = inputData.getString(KEY_PACKAGE_ID)
        val packageName = inputData.getString(KEY_PACKAGE_NAME)
        val packagePrice = inputData.getLong(KEY_PACKAGE_PRICE, 0L).takeIf { it > 0 }
        val mentions = bacaMentions(inputData.getString(KEY_MENTIONS))

        val storage = StorageService(applicationContext)
        val db = FirebaseFirestore.getInstance()

        return try {
            if (isVideo) {
                val docRef = db.collection(FirestorePaths.EXPLORE_POSTS).document()
                // onProgress dipanggil dari benang unggahan dan BUKAN fungsi
                // suspend, sedangkan setProgress() suspend. coroutineScope di
                // sini menyediakan scope untuk melempar laporan itu tanpa
                // memblokir unggahan, dan menjamin semua laporan selesai
                // sebelum eksekusi lanjut ke penulisan dokumen.
                val video = coroutineScope {
                    storage.uploadExploreVideo(creatorId, docRef.id, Uri.parse(uris.first()), "mp4") { p ->
                        launch { laporkan(p) }
                    }
                }
                val sampul = coverUri
                    ?.let { runCatching { storage.uploadExploreMedia(creatorId, Uri.parse(it), "jpg") }.getOrNull() }
                    ?: video.thumbnailUrl

                docRef.set(
                    dokumenPost(
                        creatorId, creatorName, "video", listOf(video.url), sampul,
                        caption, category, location, mentions, commentPolicy, allowSave, durasi, "Explore",
                        packageId, packageName, packagePrice,
                    )
                ).await()
            } else {
                // Beberapa foto diunggah berurutan supaya kemajuannya bisa
                // dilaporkan sebagai satu angka yang masuk akal, dan supaya
                // kegagalan di foto ketiga tidak menyisakan dua foto yatim
                // tanpa dokumen post.
                val urls = coroutineScope {
                    uris.mapIndexed { index, uri ->
                        storage.uploadExploreMedia(creatorId, Uri.parse(uri), "jpg") { p ->
                            launch { laporkan((index + p) / uris.size) }
                        }
                    }
                }
                if (target == "Portfolio") {
                    urls.forEach { url ->
                        db.collection(FirestorePaths.PORTFOLIOS).add(
                            mapOf(
                                "creatorId" to creatorId, "mediaUrl" to url, "caption" to caption,
                                "category" to category, "createdAt" to FieldValue.serverTimestamp(),
                            )
                        ).await()
                    }
                } else {
                    db.collection(FirestorePaths.EXPLORE_POSTS).add(
                        dokumenPost(
                            creatorId, creatorName, "photo", urls, urls.first(),
                            caption, category, location, mentions, commentPolicy, allowSave, null, target,
                            packageId, packageName, packagePrice,
                        )
                    ).await()
                }
            }
            // Salinan lokal baru dibuang setelah dokumen post benar-benar
            // tertulis. Membuangnya lebih awal berarti percobaan ulang setelah
            // gangguan jaringan tidak punya lagi berkas untuk diunggah.
            MediaUnggahan.bersihkan(applicationContext, uris + listOfNotNull(coverUri))
            Result.success(workDataOf(KEY_PROGRESS to 1f))
        } catch (e: SecurityException) {
            // Seharusnya tidak terjadi lagi sejak media disalin lebih dulu, tapi
            // kalau toh terjadi, pesannya harus menyebut sebab yang sebenarnya —
            // bukan "unggahan gagal" yang tidak bisa ditindaklanjuti siapa pun.
            gagalkan(
                caption, category, target, uris, isVideo,
                pesan = "Media tidak bisa dibaca lagi. Buka Draft, pilih ulang fotonya, lalu unggah lagi.",
            )
        } catch (e: IOException) {
            // Gangguan jaringan diulang dengan backoff oleh WorkManager. Ini
            // justru inti dari memindahkan unggahan ke sini: sinyal yang putus
            // di lift tidak lagi berarti mengulang dari awal secara manual.
            if (runAttemptCount < 3) Result.retry() else gagalkan(caption, category, target, uris, isVideo)
        } catch (e: Exception) {
            gagalkan(caption, category, target, uris, isVideo)
        }
    }

    private suspend fun laporkan(p: Double) {
        setProgress(workDataOf(KEY_PROGRESS to p.toFloat().coerceIn(0f, 1f)))
    }

    /**
     * Kegagalan permanen mengembalikan isinya ke daftar draft.
     *
     * Tanpa ini, caption, kategori, dan pilihan media yang sudah disusun creator
     * hilang begitu saja — dan satu-satunya jejak yang tersisa cuma notifikasi
     * "gagal" yang tidak bisa ditindaklanjuti.
     */
    private fun gagalkan(
        caption: String,
        category: String,
        target: String,
        uris: List<String>,
        isVideo: Boolean,
        pesan: String = "Unggahan gagal. Isinya dikembalikan ke Draft.",
    ): Result {
        runCatching {
            AppPreferences(applicationContext).simpanDraft(
                DraftTersimpan(
                    id = UUID.randomUUID().toString(),
                    caption = caption,
                    category = category,
                    target = target,
                    mediaUris = uris,
                    isVideo = isVideo,
                    savedAt = System.currentTimeMillis(),
                )
            )
        }
        return Result.failure(workDataOf(KEY_ERROR to pesan))
    }

    private fun dokumenPost(
        creatorId: String,
        creatorName: String,
        type: String,
        mediaUrls: List<String>,
        thumbnailUrl: String?,
        caption: String,
        category: String,
        location: String?,
        mentions: List<Map<String, String>>,
        commentPolicy: String,
        allowSave: Boolean,
        durationSeconds: Long?,
        target: String,
        packageId: String?,
        packageName: String?,
        packagePrice: Long?,
    ): Map<String, Any?> = mapOf(
        "creatorId" to creatorId, "creatorName" to creatorName, "creatorVerified" to false,
        "type" to type, "mediaUrls" to mediaUrls, "thumbnailUrl" to thumbnailUrl,
        "caption" to caption, "category" to category, "location" to location,
        "tags" to CaptionParser.hashtags(caption),
        "mentions" to mentions,
        "commentPolicy" to commentPolicy,
        "allowSave" to allowSave,
        "durationSeconds" to durationSeconds,
        "target" to target,
        "packageId" to packageId,
        "packageName" to packageName,
        "packagePrice" to packagePrice,
        "metrics" to mapOf("like" to 0, "comment" to 0, "save" to 0, "share" to 0, "view" to 0),
        "status" to "pending_review",
        "createdAt" to FieldValue.serverTimestamp(),
    )

    private fun bacaMentions(json: String?): List<Map<String, String>> = runCatching {
        val array = JSONArray(json ?: "[]")
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            mapOf("name" to o.optString("name"), "creatorId" to o.optString("creatorId"))
        }
    }.getOrDefault(emptyList())

    companion object {
        const val TAG = "unggah_post"
        const val KEY_CREATOR_ID = "creatorId"
        const val KEY_CREATOR_NAME = "creatorName"
        const val KEY_URIS = "uris"
        const val KEY_IS_VIDEO = "isVideo"
        const val KEY_CAPTION = "caption"
        const val KEY_CATEGORY = "category"
        const val KEY_TARGET = "target"
        const val KEY_LOCATION = "location"
        const val KEY_COMMENT_POLICY = "commentPolicy"
        const val KEY_ALLOW_SAVE = "allowSave"
        const val KEY_DURATION = "durationSeconds"
        const val KEY_COVER = "coverUri"
        const val KEY_MENTIONS = "mentions"
        const val KEY_PACKAGE_ID = "packageId"
        const val KEY_PACKAGE_NAME = "packageName"
        const val KEY_PACKAGE_PRICE = "packagePrice"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"

        fun data(
            creatorId: String,
            creatorName: String,
            uris: List<String>,
            isVideo: Boolean,
            caption: String,
            category: String,
            target: String,
            location: String?,
            commentPolicy: String,
            allowSave: Boolean,
            durationSeconds: Long?,
            coverUri: String?,
            mentions: List<Map<String, String>>,
            packageId: String?,
            packageName: String?,
            packagePrice: Long?,
        ): Data = workDataOf(
            KEY_CREATOR_ID to creatorId,
            KEY_CREATOR_NAME to creatorName,
            KEY_URIS to uris.toTypedArray(),
            KEY_IS_VIDEO to isVideo,
            KEY_CAPTION to caption,
            KEY_CATEGORY to category,
            KEY_TARGET to target,
            KEY_LOCATION to location,
            KEY_COMMENT_POLICY to commentPolicy,
            KEY_ALLOW_SAVE to allowSave,
            KEY_DURATION to (durationSeconds ?: 0L),
            KEY_COVER to coverUri,
            KEY_PACKAGE_ID to packageId,
            KEY_PACKAGE_NAME to packageName,
            KEY_PACKAGE_PRICE to (packagePrice ?: 0L),
            KEY_MENTIONS to JSONArray().apply {
                mentions.forEach { m ->
                    put(JSONObject().put("name", m["name"]).put("creatorId", m["creatorId"]))
                }
            }.toString(),
        )
    }
}
