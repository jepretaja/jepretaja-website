package com.jepretaja.app.ui.screens.creatordashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.repository.CreatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Rekening pencairan creator.
 *
 * Kegagalan menulis DITANGKAP dan dijadikan pesan, bukan dibiarkan naik: satu
 * PERMISSION_DENIED dari Firestore di dalam viewModelScope akan menjatuhkan
 * seluruh aplikasi, dan layar yang mati mendadak saat menyimpan nomor rekening
 * adalah cara terburuk untuk memberi tahu bahwa datanya belum tersimpan.
 */
@HiltViewModel
class CreatorBankAccountViewModel @Inject constructor(
    private val repository: CreatorRepository,
) : ViewModel() {

    fun streamCreator(creatorId: String): Flow<CreatorModel?> =
        repository.streamCreator(creatorId).catch { emit(null) }

    private val _menyimpan = MutableStateFlow(false)
    val menyimpan: StateFlow<Boolean> = _menyimpan.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _tersimpan = MutableStateFlow(false)
    val tersimpan: StateFlow<Boolean> = _tersimpan.asStateFlow()

    fun simpan(creatorId: String, namaBank: String, nomorRekening: String, namaPemilik: String) {
        viewModelScope.launch {
            _menyimpan.value = true; _error.value = null; _tersimpan.value = false
            try {
                repository.simpanRekening(creatorId, namaBank, nomorRekening, namaPemilik)
                _tersimpan.value = true
            } catch (e: Exception) {
                _error.value = pesanGagal(e)
            } finally {
                _menyimpan.value = false
            }
        }
    }

    fun hapus(creatorId: String) {
        viewModelScope.launch {
            _menyimpan.value = true; _error.value = null; _tersimpan.value = false
            try {
                repository.hapusRekening(creatorId)
            } catch (e: Exception) {
                _error.value = pesanGagal(e)
            } finally {
                _menyimpan.value = false
            }
        }
    }

    fun bersihkanStatus() { _tersimpan.value = false; _error.value = null }

    /**
     * Pesan Firestore mentah ("PERMISSION_DENIED: Missing or insufficient
     * permissions") tidak memberi tahu creator apa pun yang bisa ia lakukan,
     * jadi dua kegagalan yang paling mungkin diterjemahkan lebih dulu.
     */
    private fun pesanGagal(e: Exception): String {
        val mentah = e.message.orEmpty()
        return when {
            mentah.contains("PERMISSION_DENIED", ignoreCase = true) ->
                "Akunmu belum diizinkan menyimpan rekening. Hubungi Admin JepretAja."
            mentah.contains("UNAVAILABLE", ignoreCase = true) ->
                "Tidak ada koneksi ke server. Periksa internetmu lalu coba lagi."
            else -> mentah.ifBlank { "Rekening gagal disimpan. Coba lagi." }
        }
    }
}
