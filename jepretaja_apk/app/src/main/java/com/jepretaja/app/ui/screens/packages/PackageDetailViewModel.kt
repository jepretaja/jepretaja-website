package com.jepretaja.app.ui.screens.packages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.data.model.PackageModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class PackageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: FirebaseFirestore,
) : ViewModel() {
    val packageId: String = checkNotNull(savedStateHandle["packageId"])

    private val _pkg = MutableStateFlow<PackageModel?>(null)
    val pkg: StateFlow<PackageModel?> = _pkg.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _notFound = MutableStateFlow(false)
    val notFound: StateFlow<Boolean> = _notFound.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true; _error.value = null; _notFound.value = false
            try {
                val snap = db.collection(FirestorePaths.PACKAGES).document(packageId).get().await()
                if (snap.exists()) _pkg.value = snap.toObject(PackageModel::class.java) else _notFound.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal memuat paket."
            } finally {
                _loading.value = false
            }
        }
    }
}
