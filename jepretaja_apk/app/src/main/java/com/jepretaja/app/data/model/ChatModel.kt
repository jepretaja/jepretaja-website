package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ChatModel(
    @DocumentId val chatId: String = "",
    val bookingId: String? = null,
    val customerId: String = "",
    val creatorId: String = "",
    val otherPartyName: String = "",
    val otherPartyPhotoUrl: String? = null,
    val lastMessage: String = "",
    val updatedAt: Timestamp? = null,
)

data class MessageModel(
    @DocumentId val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val type: String = "text", // text|image|file
    val text: String? = null,
    val mediaUrl: String? = null,
    val createdAt: Timestamp? = null,
    val readAt: Timestamp? = null,
)
