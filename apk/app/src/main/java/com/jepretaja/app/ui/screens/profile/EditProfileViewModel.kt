package com.jepretaja.app.ui.screens.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.services.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Mengurus bagian edit profil yang butuh jaringan: mengunggah foto profil baru
 * ke Cloudinary sebelum URL-nya disimpan ke Firestore.
 */
@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val storageService: StorageService,
) : ViewModel() {

    /**
     * [onDone] menerima (photoUrl, error). photoUrl bernilai null saat pengguna
     * tidak mengganti foto — pemanggil memperlakukannya sebagai "biarkan foto
     * lama apa adanya", bukan "hapus foto".
     */
    fun save(
        name: String,
        phone: String?,
        photoUri: Uri?,
        userId: String?,
        onDone: (photoUrl: String?, error: String?) -> Unit,
    ) {
        if (photoUri == null || userId == null) {
            onDone(null, null)
            return
        }
        viewModelScope.launch {
            val result = runCatching { storageService.uploadProfilePhoto(userId, photoUri) }
            onDone(result.getOrNull(), result.exceptionOrNull()?.message?.let { "Gagal mengunggah foto: $it" })
        }
    }
}
