package com.jepretaja.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.ChatModel
import com.jepretaja.app.data.repository.ChatRepository
import com.jepretaja.app.data.repository.CreatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val creatorRepository: CreatorRepository,
) : ViewModel() {

    fun chats(userId: String): StateFlow<List<ChatModel>> =
        repository.streamChats(userId).catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Pesan belum dibaca per percakapan.
     *
     * Sebelumnya angka ini hanya ada sebagai satu total untuk lencana di Home;
     * daftar chat sendiri tidak punya cara membedakan percakapan yang menunggu
     * jawaban dari yang sudah selesai. Untuk daftar chat, itu justru satu-satunya
     * informasi yang menentukan urutan perhatian.
     */
    fun belumDibaca(userId: String): StateFlow<Map<String, Int>> =
        repository.streamUnreadPerChat(userId).catch { emit(emptyMap()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _idCreator = MutableStateFlow<List<String>>(emptyList())

    /** Dipanggil layar setiap daftar chat berubah, supaya query kehadiran ikut menyesuaikan. */
    fun pantauKehadiran(creatorIds: List<String>) {
        val bersih = creatorIds.filter { it.isNotBlank() }.distinct()
        if (bersih != _idCreator.value) _idCreator.value = bersih
    }

    /** creatorId -> waktu aktif terakhir (millis). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val aktifTerakhir: StateFlow<Map<String, Long>> = _idCreator
        .flatMapLatest { ids -> creatorRepository.streamAktifTerakhir(ids) }
        .catch { emit(emptyMap()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
}

/** Online = aktif dalam 2 menit terakhir, ambang yang sama dengan profil creator. */
fun sedangOnline(aktifTerakhirMillis: Long?): Boolean {
    val t = aktifTerakhirMillis ?: return false
    return System.currentTimeMillis() - t < 2 * 60 * 1000
}
