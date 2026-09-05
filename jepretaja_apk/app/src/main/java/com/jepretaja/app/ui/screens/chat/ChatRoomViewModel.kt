package com.jepretaja.app.ui.screens.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.ChatModel
import com.jepretaja.app.data.model.MessageModel
import com.jepretaja.app.data.repository.ChatRepository
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.services.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Keadaan satu gelembung dari sisi pengirim. */
enum class StatusKirim { MENGIRIM, TERKIRIM, TERBACA, GAGAL }

/**
 * Pesan yang belum sampai ke Firestore.
 *
 * Sebelumnya tidak ada konsep ini sama sekali: layar mengosongkan kolom ketik
 * begitu tombol kirim ditekan, pesannya baru muncul setelah server membalas,
 * dan kalau pengiriman gagal satu-satunya jejaknya adalah snackbar plus teks
 * yang dikembalikan ke kolom ketik. Selama jeda itu — yang di sinyal buruk bisa
 * belasan detik — pengguna melihat percakapan yang tidak berubah sama sekali
 * dan biasanya menekan kirim lagi.
 */
data class PesanTertunda(
    val localId: String,
    val teks: String? = null,
    val gambarUri: Uri? = null,
    val gagal: Boolean = false,
    val waktu: Long = System.currentTimeMillis(),
)

@HiltViewModel
class ChatRoomViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val creatorRepository: CreatorRepository,
    private val storageService: StorageService,
) : ViewModel() {

    /* ---- Riwayat & halaman ------------------------------------------- */

    private val _batas = MutableStateFlow(HALAMAN)
    private val _memuatLama = MutableStateFlow(false)
    val memuatLama: StateFlow<Boolean> = _memuatLama.asStateFlow()
    private val _adaLagi = MutableStateFlow(false)
    val adaLagi: StateFlow<Boolean> = _adaLagi.asStateFlow()

    fun chat(chatId: String): StateFlow<ChatModel?> =
        repository.streamChat(chatId).catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Pesan, dibatasi dan bisa ditambah.
     *
     * Query lama menarik SELURUH riwayat percakapan setiap kali ruang chat
     * dibuka — satu pembacaan Firestore per pesan, tanpa batas atas. Sekarang
     * batasnya naik hanya saat pengguna benar-benar meminta pesan lama.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun messages(chatId: String, myUserId: String): StateFlow<List<MessageModel>> = _batas
        .flatMapLatest { batas ->
            repository.streamMessages(chatId, myUserId, batas).map { daftar -> daftar to batas }
        }
        .catch { emit(emptyList<MessageModel>() to 0L) }
        .onEach { (daftar, batas) ->
            _memuatLama.value = false
            // Kalau hasilnya sebanyak batas, hampir pasti masih ada yang lebih
            // lama. Kalau kurang, kita sudah menyentuh awal percakapan.
            _adaLagi.value = batas > 0 && daftar.size >= batas
        }
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun muatLebihLama() {
        if (_memuatLama.value || !_adaLagi.value) return
        _memuatLama.value = true
        _batas.value += HALAMAN
    }

    /* ---- Kehadiran lawan bicara --------------------------------------- */

    fun aktifTerakhir(creatorId: String): StateFlow<Long?> =
        creatorRepository.streamCreator(creatorId)
            .map { it?.lastActiveAt?.toDate()?.time }
            .catch { emit(null) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /* ---- Antrean kirim ------------------------------------------------ */

    private val _tertunda = MutableStateFlow<List<PesanTertunda>>(emptyList())
    val tertunda: StateFlow<List<PesanTertunda>> = _tertunda.asStateFlow()

    private val _pesan = MutableStateFlow<String?>(null)
    val pesan: StateFlow<String?> = _pesan.asStateFlow()
    fun pesanDibaca() { _pesan.value = null }

    fun sendMessage(chatId: String, senderId: String, text: String) {
        val item = PesanTertunda(localId = UUID.randomUUID().toString(), teks = text)
        _tertunda.value = _tertunda.value + item
        kirim(chatId, senderId, item)
    }

    fun sendImage(chatId: String, senderId: String, uri: Uri) {
        val item = PesanTertunda(localId = UUID.randomUUID().toString(), gambarUri = uri)
        _tertunda.value = _tertunda.value + item
        kirim(chatId, senderId, item)
    }

    /** Coba lagi satu pesan yang gagal — tanpa mengetik ulang apa pun. */
    fun kirimUlang(chatId: String, senderId: String, localId: String) {
        val item = _tertunda.value.firstOrNull { it.localId == localId } ?: return
        kirim(chatId, senderId, item)
    }

    fun batalkanTertunda(localId: String) {
        _tertunda.value = _tertunda.value.filterNot { it.localId == localId }
    }

    /**
     * Pengiriman selalu dibungkus runCatching.
     *
     * Sebelumnya `sendMessage` berjalan tanpa penangkap apa pun. Satu penolakan
     * aturan Firestore — persis yang terjadi ketika aturan chat belum
     * ter-deploy — cukup untuk melempar pengecualian keluar dari viewModelScope
     * dan MEMATIKAN aplikasi tepat saat pengguna menekan kirim.
     */
    private fun kirim(chatId: String, senderId: String, item: PesanTertunda) {
        _tertunda.value = _tertunda.value.map {
            if (it.localId == item.localId) it.copy(gagal = false) else it
        }
        viewModelScope.launch {
            val hasil = runCatching {
                val uri = item.gambarUri
                if (uri != null) {
                    val url = storageService.uploadChatAttachment(chatId, uri)
                    repository.sendImageMessage(chatId, senderId, url)
                } else {
                    repository.sendMessage(chatId, senderId, item.teks.orEmpty())
                }
            }
            if (hasil.isSuccess) {
                // Dokumen aslinya sudah datang lewat listener, jadi salinan
                // sementaranya dibuang — kalau tidak, pesannya tampil dua kali.
                _tertunda.value = _tertunda.value.filterNot { it.localId == item.localId }
            } else {
                _tertunda.value = _tertunda.value.map {
                    if (it.localId == item.localId) it.copy(gagal = true) else it
                }
            }
        }
    }

    /* ---- Sisanya ------------------------------------------------------ */

    fun isBlocked(userA: String, userB: String): StateFlow<Boolean> =
        repository.streamIsBlocked(userA, userB).catch { emit(false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun markRead(chatId: String, myUserId: String) {
        viewModelScope.launch { runCatching { repository.markMessagesAsRead(chatId, myUserId) } }
    }

    fun toggleBlock(myId: String, otherId: String, currentlyBlocked: Boolean) {
        viewModelScope.launch {
            runCatching {
                if (currentlyBlocked) repository.unblockUser(myId, otherId) else repository.blockUser(myId, otherId)
            }.onFailure { _pesan.value = "Gagal mengubah status blokir." }
        }
    }

    suspend fun isBlockedByMe(myId: String, otherId: String): Boolean = repository.isBlockedByMe(myId, otherId)

    private companion object {
        const val HALAMAN = 40L
    }
}
