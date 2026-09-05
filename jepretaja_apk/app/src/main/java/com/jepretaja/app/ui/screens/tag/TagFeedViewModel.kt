package com.jepretaja.app.ui.screens.tag

import androidx.lifecycle.ViewModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.repository.ExploreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

@HiltViewModel
class TagFeedViewModel @Inject constructor(
    private val repository: ExploreRepository,
) : ViewModel() {

    fun byTag(tag: String): Flow<List<ExplorePostModel>> =
        repository.streamByTag(tag).catch { emit(emptyList()) }

    fun byCategory(category: String): Flow<List<ExplorePostModel>> =
        repository.streamFeed(category).catch { emit(emptyList()) }
}
