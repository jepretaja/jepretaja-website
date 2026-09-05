package com.jepretaja.app.ui.screens.favorites

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.repository.CreatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val creatorRepository: CreatorRepository,
) : ViewModel() {
    /** follows/{creatorId}_{userId} -> ambil semua creator yang di-follow
     * user ini, lalu fetch profil masing-masing. */
    fun favorites(userId: String): Flow<List<CreatorModel>> = flow {
        try {
            val snap = db.collection(FirestorePaths.FOLLOWS).whereEqualTo("userId", userId).get().await()
            val creatorIds = snap.documents.mapNotNull { it.getString("creatorId") }
            val creators = creatorIds.mapNotNull { id -> creatorRepository.getCreator(id) }
            emit(creators)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
}
