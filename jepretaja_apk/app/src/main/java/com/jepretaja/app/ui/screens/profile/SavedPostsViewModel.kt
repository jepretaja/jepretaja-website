package com.jepretaja.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.repository.ExploreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

@HiltViewModel
class SavedPostsViewModel @Inject constructor(
    private val repository: ExploreRepository,
) : ViewModel() {
    fun savedPosts(userId: String): Flow<List<ExplorePostModel>> =
        repository.streamSavedPosts(userId).catch { emit(emptyList()) }
}
