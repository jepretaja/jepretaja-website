package com.jepretaja.app.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.NotificationModel
import com.jepretaja.app.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {
    fun stream(userId: String): Flow<List<NotificationModel>> =
        repository.stream(userId).catch { emit(emptyList()) }

    fun markRead(notificationId: String) {
        viewModelScope.launch { runCatching { repository.markRead(notificationId) } }
    }
}
