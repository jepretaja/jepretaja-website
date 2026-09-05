package com.jepretaja.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.ChatModel
import com.jepretaja.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SendToChatViewModel @Inject constructor(
    private val repository: ChatRepository,
) : ViewModel() {

    fun chats(userId: String): Flow<List<ChatModel>> =
        repository.streamChats(userId).catch { emit(emptyList()) }

    fun send(chatId: String, senderId: String, text: String) {
        viewModelScope.launch { runCatching { repository.sendMessage(chatId, senderId, text) } }
    }
}
