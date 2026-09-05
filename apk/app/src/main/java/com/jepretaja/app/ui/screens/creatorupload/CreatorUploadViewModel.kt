package com.jepretaja.app.ui.screens.creatorupload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.MediaUnggahan
import com.jepretaja.app.data.local.AppPreferences
import com.jepretaja.app.data.local.DraftTersimpan
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.PackageModel
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.data.work.UploadQueue
import com.jepretaja.app.data.work.UploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

/**
 * Pilihan creator yang menyertai satu unggahan.
 *
 * Dikumpulkan jadi satu objek supaya menambah pengaturan berikutnya tidak
 * membuat daftar parameter terus memanjang.
 */
data class PengaturanPost(
    val location: String? = null,
    val commentPolicy: String = "all",
    val allowSave: Boolean = true,
    val durationSeconds: Long? = null,
    /** Waktu tayang yang dijadwalkan, dalam epoch millis. Null = kirim sekarang. */
    val scheduledAt: Long? = null,
    val packageId: String? = null,
    val packageName: String? = null,
    val packagePrice: Long? = null,
)

@HiltViewModel
class CreatorUploadViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val prefs: AppPreferences,
    private val creatorRepository: CreatorRepository,
) : ViewModel() {

    /** Paket milik creator ini, untuk ditautkan ke karya yang diunggah. */
    fun paketSaya(creatorId: String): Flow<List<PackageModel>> =
        creatorRepository.streamPackages(creatorId).catch { emit(emptyList()) }

    /**
     * Kabar KEBERHASILAN — layar menutup dirinya sendiri begitu ini terisi.
     *
     * Kegagalan sengaja tidak lewat sini: layar memperlakukan `message` sebagai
     * tanda "sudah selesai, silakan kembali", jadi menaruh pesan error di jalur
     * yang sama akan melempar creator keluar dari layar unggah tepat pada saat
     * ia paling perlu membaca apa yang salah.
     */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() { _error.value = null }

    private val _drafts = MutableStateFlow(prefs.drafts)
    val drafts: StateFlow<List<DraftTersimpan>> = _drafts.asStateFlow()

    /**
     * Menitipkan unggahan ke WorkManager, lalu selesai.
     *
     * ViewModel sengaja tidak lagi memegang status "sedang mengunggah". Kemajuan
     * unggahan kini milik antrean, bukan milik layar — itulah yang membuat
     * menutup layar tidak membatalkan apa pun. Layar hanya menyalakan proses
     * lalu boleh ditinggalkan.
     */
    suspend fun kirim(
        context: Context,
        creatorId: String,
        creatorName: String,
        uris: List<Uri>,
        isVideo: Boolean,
        caption: String,
        category: String,
        target: String,
        mentions: List<Map<String, String>>,
        coverUri: Uri?,
        pengaturan: PengaturanPost,
    ) {
        // Media disalin ke penyimpanan aplikasi SEBELUM masuk antrean. Izin baca
        // dari pemilih galeri berumur pendek dan sudah hilang saat WorkManager
        // benar-benar menjalankan unggahannya — lihat MediaUnggahan untuk
        // uraian lengkapnya.
        val salinan = try {
            uris.map { MediaUnggahan.salin(context, it, if (isVideo) "mp4" else "jpg") }
        } catch (e: Exception) {
            _error.value = e.message?.takeIf { it.isNotBlank() }
                ?: "Media gagal disiapkan. Coba pilih ulang dari galeri."
            return
        }
        val salinanSampul = coverUri?.let {
            runCatching { MediaUnggahan.salin(context, it, "jpg") }.getOrNull()
        }

        val data = UploadWorker.data(
            creatorId = creatorId,
            creatorName = creatorName,
            uris = salinan.map { it.toString() },
            isVideo = isVideo,
            caption = caption,
            category = category,
            target = target,
            location = pengaturan.location,
            commentPolicy = pengaturan.commentPolicy,
            allowSave = pengaturan.allowSave,
            durationSeconds = pengaturan.durationSeconds,
            coverUri = salinanSampul?.toString(),
            mentions = mentions,
            packageId = pengaturan.packageId,
            packageName = pengaturan.packageName,
            packagePrice = pengaturan.packagePrice,
        )
        val jeda = pengaturan.scheduledAt?.minus(System.currentTimeMillis())?.coerceAtLeast(0L) ?: 0L
        UploadQueue.enqueue(context, data, jeda)

        _message.value = when {
            jeda > 0 -> "Dijadwalkan. Akan diunggah otomatis saat waktunya tiba."
            else -> "Masuk antrean unggah. Kamu boleh menutup layar ini."
        }
    }

    /**
     * Creator yang cocok untuk pemilih sebutan (@).
     *
     * Disaring di klien atas 60 creator aktif. Firestore tidak punya pencarian
     * awalan yang peka huruf besar-kecil, dan mengunduh daftar pendek lalu
     * menyaringnya lebih sederhana daripada memelihara field pencarian khusus.
     */
    fun cariCreator(prefix: String, onResult: (List<CreatorModel>) -> Unit) {
        viewModelScope.launch {
            val hasil = runCatching {
                db.collection(FirestorePaths.CREATORS)
                    .whereEqualTo("status", "active")
                    .limit(60).get().await()
                    .toObjects(CreatorModel::class.java)
                    .filter { it.displayName.contains(prefix, ignoreCase = true) }
                    .take(8)
            }.getOrDefault(emptyList())
            onResult(hasil)
        }
    }

    // --- Draft ---------------------------------------------------------------

    fun simpanDraft(
        id: String?,
        caption: String,
        category: String,
        target: String,
        mediaUris: List<Uri>,
        isVideo: Boolean,
    ) {
        prefs.simpanDraft(
            DraftTersimpan(
                id = id ?: UUID.randomUUID().toString(),
                caption = caption,
                category = category,
                target = target,
                mediaUris = mediaUris.map { it.toString() },
                isVideo = isVideo,
                savedAt = System.currentTimeMillis(),
            )
        )
        _drafts.value = prefs.drafts
        _message.value = "Tersimpan ke draft."
    }

    fun hapusDraft(id: String) {
        prefs.hapusDraft(id)
        _drafts.value = prefs.drafts
    }

    fun refreshDrafts() { _drafts.value = prefs.drafts }

    fun clearMessage() { _message.value = null }
}
