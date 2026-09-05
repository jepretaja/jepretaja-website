package com.jepretaja.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.local.AppPreferences
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.repository.ExploreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Riwayat pencarian.
 *
 * Disimpan di perangkat lewat AppPreferences. ViewModel hanya menyalinnya ke
 * StateFlow supaya layar ikut berubah begitu ada entri baru — SharedPreferences
 * sendiri tidak memberi tahu Compose kalau isinya berubah.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val exploreRepository: ExploreRepository,
) : ViewModel() {

    /**
     * Tagar yang sedang naik, dihitung dari frekuensi `tags` pada 60 karya
     * terbaru.
     *
     * Tidak ada penghitung tagar tersendiri di server, dan sengaja begitu:
     * memelihara koleksi agregat berarti setiap unggahan harus menaikkan
     * beberapa dokumen sekaligus, dengan risiko selisih yang tidak pernah
     * ketahuan. Menghitungnya dari karya terbaru justru lebih jujur pada arti
     * kata "sedang naik".
     */
    val tagarNaik: StateFlow<List<Pair<String, Int>>> =
        exploreRepository.streamFeed(limit = 60)
            .map { daftar ->
                daftar.flatMap { it.tags }
                    .groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(12)
                    .map { it.key to it.value }
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val creatorNaik: StateFlow<List<CreatorModel>> =
        exploreRepository.streamSuggestedCreators(8)
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _history = MutableStateFlow(prefs.searchHistory)
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun remember(query: String) {
        prefs.rememberSearch(query)
        _history.value = prefs.searchHistory
    }

    fun remove(query: String) {
        prefs.removeSearch(query)
        _history.value = prefs.searchHistory
    }

    fun clear() {
        prefs.clearSearchHistory()
        _history.value = emptyList()
    }
}
