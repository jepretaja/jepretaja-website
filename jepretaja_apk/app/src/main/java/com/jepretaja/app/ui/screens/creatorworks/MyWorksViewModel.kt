package com.jepretaja.app.ui.screens.creatorworks

import androidx.lifecycle.ViewModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.repository.ExploreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

@HiltViewModel
class MyWorksViewModel @Inject constructor(
    private val repository: ExploreRepository,
) : ViewModel() {
    fun myPosts(creatorId: String): Flow<List<ExplorePostModel>> =
        repository.streamMyPosts(creatorId).catch { emit(emptyList()) }
}
