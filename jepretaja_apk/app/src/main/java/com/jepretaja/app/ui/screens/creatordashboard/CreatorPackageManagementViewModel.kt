package com.jepretaja.app.ui.screens.creatordashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.PackageModel
import com.jepretaja.app.data.repository.CreatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreatorPackageManagementViewModel @Inject constructor(
    val repository: CreatorRepository,
) : ViewModel() {

    /**
     * Kabar hasil tiap aksi.
     *
     * Sebelumnya keempat aksi di bawah membungkus kegagalan dengan
     * `runCatching` lalu membuangnya. Efeknya: paket yang gagal disimpan
     * terlihat persis sama seperti paket yang berhasil disimpan — lembar
     * formulirnya tertutup, dan creator baru sadar paketnya tidak ada saat
     * memeriksa profilnya sendiri.
     */
    private val _pesan = MutableStateFlow<String?>(null)
    val pesan: StateFlow<String?> = _pesan.asStateFlow()

    fun pesanDibaca() { _pesan.value = null }

    fun savePackage(pkg: PackageModel) {
        viewModelScope.launch {
            runCatching { repository.upsertPackage(pkg) }
                .onSuccess { _pesan.value = "Paket \"${pkg.name}\" tersimpan" }
                .onFailure { _pesan.value = "Paket gagal disimpan. Periksa koneksimu lalu coba lagi." }
        }
    }
    fun deletePackage(packageId: String, creatorId: String) {
        viewModelScope.launch {
            runCatching { repository.deletePackage(packageId, creatorId) }
                .onSuccess { _pesan.value = "Paket dihapus" }
                .onFailure { _pesan.value = "Paket gagal dihapus." }
        }
    }
    fun addAddOn(packageId: String, name: String, price: Long) {
        viewModelScope.launch {
            runCatching { repository.addAddOn(packageId, name, price) }
                .onFailure { _pesan.value = "Add-on gagal ditambahkan." }
        }
    }
    fun deleteAddOn(packageId: String, addOnId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteAddOn(packageId, addOnId) }
                .onFailure { _pesan.value = "Add-on gagal dihapus." }
        }
    }
}
