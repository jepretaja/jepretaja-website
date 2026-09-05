package com.jepretaja.app.ui.screens.creatordashboard

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.PortfolioModel
import com.jepretaja.app.data.model.PortfolioStatus
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.services.StorageService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Kemajuan unggahan banyak berkas sekaligus. */
data class ProgresUnggah(
    val selesai: Int = 0,
    val total: Int = 0,
    val gagal: Int = 0,
) {
    val sedangJalan: Boolean get() = total > 0
}

@HiltViewModel
class CreatorPortfolioManagementViewModel @Inject constructor(
    val repository: CreatorRepository,
    private val storageService: StorageService,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Kabar terakhir untuk creator — ditampilkan layar sebagai snackbar. */
    private val _pesan = MutableStateFlow<String?>(null)
    val pesan: StateFlow<String?> = _pesan.asStateFlow()
    fun pesanDibaca() { _pesan.value = null }

    private val _progres = MutableStateFlow(ProgresUnggah())
    val progres: StateFlow<ProgresUnggah> = _progres.asStateFlow()

    /** Semua album milik creator, TERMASUK draft dan yang ditolak. */
    fun albumSaya(creatorId: String): Flow<List<PortfolioModel>> =
        repository.streamPortfolio(creatorId, hanyaTayang = false).catch { emit(emptyList()) }

    /**
     * Mengunggah beberapa berkas sekaligus menjadi SATU album.
     *
     * Album baru selalu berstatus draft, tidak langsung tayang. Sesi unggah
     * biasanya belum selesai saat berkas pertama masuk — memublikasikannya
     * seketika berarti calon pelanggan melihat album setengah jadi tanpa judul,
     * dan creator tidak punya kesempatan memeriksanya lebih dulu.
     *
     * Berkas yang gagal tidak membatalkan yang lain: dari sepuluh foto, sembilan
     * yang berhasil tetap disimpan dan yang satu dilaporkan jumlahnya.
     */
    fun unggahAlbum(creatorId: String, uris: List<Uri>, urutanBerikutnya: Long) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _progres.value = ProgresUnggah(total = uris.size)
            val url = mutableListOf<String>()
            var gagal = 0
            var adaVideo = false

            uris.forEach { uri ->
                val video = apakahVideo(uri)
                val hasil = runCatching {
                    storageService.uploadPortfolioMedia(
                        creatorId = creatorId,
                        uri = uri,
                        extension = if (video) "mp4" else "jpg",
                        resourceType = if (video) "video" else "image",
                    )
                }
                hasil.onSuccess {
                    url += it
                    if (video) adaVideo = true
                }.onFailure { gagal += 1 }
                _progres.value = _progres.value.copy(selesai = url.size, gagal = gagal)
            }

            if (url.isEmpty()) {
                _progres.value = ProgresUnggah()
                _pesan.value = "Semua berkas gagal diunggah. Periksa koneksimu lalu coba lagi."
                return@launch
            }

            val simpan = runCatching {
                repository.addPortfolioAlbum(
                    creatorId = creatorId,
                    media = url,
                    title = "",
                    category = "",
                    type = if (adaVideo) "video" else "image",
                    // Sampul video belum bisa dibuat di perangkat, jadi
                    // dikosongkan dan grid memakai penanda video sebagai
                    // gantinya — lebih jujur daripada memajang kotak kosong.
                    thumbnailUrl = null,
                    status = PortfolioStatus.DRAFT,
                    order = urutanBerikutnya,
                )
            }
            _progres.value = ProgresUnggah()
            _pesan.value = when {
                simpan.isFailure -> "Media terunggah tapi albumnya gagal disimpan."
                gagal > 0 -> "Album dibuat sebagai draft. $gagal berkas gagal diunggah."
                else -> "Album dibuat sebagai draft. Ajukan review kalau sudah siap."
            }
        }
    }

    fun updatePortfolioItem(portfolioId: String, title: String, category: String) {
        viewModelScope.launch {
            runCatching { repository.updatePortfolioItem(portfolioId, title, category) }
                // Kegagalan yang ditelan diam-diam lebih buruk daripada pesan
                // error: creator menutup layar dengan yakin perubahannya
                // tersimpan, dan baru tahu tidak lama kemudian.
                .onFailure { _pesan.value = "Perubahan gagal disimpan." }
        }
    }

    fun ajukanReview(portfolioId: String) {
        viewModelScope.launch {
            runCatching { repository.updatePortfolioStatus(portfolioId, PortfolioStatus.MENUNGGU) }
                .onSuccess { _pesan.value = "Album diajukan. Moderator akan meninjau." }
                .onFailure { _pesan.value = "Gagal mengajukan album." }
        }
    }

    /** Menarik kembali pengajuan, atau mengembalikan album tayang jadi draft. */
    fun jadikanDraft(portfolioId: String) {
        viewModelScope.launch {
            runCatching { repository.updatePortfolioStatus(portfolioId, PortfolioStatus.DRAFT) }
                .onSuccess { _pesan.value = "Album dikembalikan ke draft dan tidak lagi tampil publik." }
                .onFailure { _pesan.value = "Gagal mengubah status album." }
        }
    }

    fun simpanUrutan(idBerurutan: List<String>) {
        viewModelScope.launch {
            runCatching { repository.simpanUrutanPortfolio(idBerurutan) }
                .onFailure { _pesan.value = "Urutan gagal disimpan. Coba lagi." }
        }
    }

    fun deletePortfolioItem(portfolioId: String) {
        viewModelScope.launch {
            runCatching { repository.deletePortfolioItem(portfolioId) }
                .onFailure { _pesan.value = "Karya gagal dihapus." }
        }
    }

    /**
     * Menebak jenis berkas dari MIME type yang dilaporkan ContentResolver.
     *
     * Bukan dari ekstensi nama berkas: pemilih media Android mengembalikan URI
     * `content://` yang sering tidak punya nama berkas sama sekali.
     */
    private fun apakahVideo(uri: Uri): Boolean =
        context.contentResolver.getType(uri)?.startsWith("video") == true
}
