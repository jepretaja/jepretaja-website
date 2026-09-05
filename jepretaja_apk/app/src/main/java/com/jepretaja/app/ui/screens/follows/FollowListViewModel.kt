package com.jepretaja.app.ui.screens.follows

import androidx.lifecycle.ViewModel
import com.jepretaja.app.data.model.FollowModel
import com.jepretaja.app.data.repository.ExploreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class FollowListViewModel @Inject constructor(
    private val repository: ExploreRepository,
) : ViewModel() {

    /**
     * Diurutkan di klien, bukan lewat `orderBy` Firestore.
     *
     * Menambahkan orderBy("createdAt") pada query yang sudah menyaring
     * creatorId/userId menuntut indeks komposit baru untuk dua arah sekaligus.
     * Daftarnya dibatasi 200 baris, jadi mengurutkan di memori jauh lebih murah
     * daripada dua indeks yang harus di-deploy lebih dulu.
     */
    fun followers(creatorId: String): Flow<List<FollowModel>> =
        repository.streamFollowers(creatorId)
            .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }
            .catch { emit(emptyList()) }

    fun following(userId: String): Flow<List<FollowModel>> =
        repository.streamFollowing(userId)
            .map { list -> list.sortedByDescending { it.createdAt?.seconds ?: 0L } }
            .catch { emit(emptyList()) }
}
