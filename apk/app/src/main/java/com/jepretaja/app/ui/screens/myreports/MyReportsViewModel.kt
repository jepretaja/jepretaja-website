package com.jepretaja.app.ui.screens.myreports

import androidx.lifecycle.ViewModel
import com.jepretaja.app.data.model.ReportModel
import com.jepretaja.app.data.repository.ExploreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class MyReportsViewModel @Inject constructor(
    private val repository: ExploreRepository,
) : ViewModel() {
    fun myReports(userId: String): Flow<List<ReportModel>> = repository.streamMyReports(userId)
}
