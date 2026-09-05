package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class NotificationModel(
    @DocumentId val notificationId: String = "",
    val userId: String = "",
    val type: String = "",
    val title: String = "",
    val body: String = "",
    val referenceId: String? = null,
    val deepLink: String? = null,
    val createdAt: Timestamp? = null,
    val readAt: Timestamp? = null,
)
