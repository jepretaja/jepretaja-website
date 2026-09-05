package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ReviewModel(
    @DocumentId val reviewId: String = "",
    val bookingId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val creatorId: String = "",
    val rating: Long = 5,
    val text: String = "",
    val status: String = "published",
    val creatorReply: String? = null,
    val createdAt: Timestamp? = null,
)
