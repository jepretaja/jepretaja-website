package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/** Laporan yang diajukan user (konten, creator, dsb) — koleksi `reports`.
 * Ditulis pertama kali lewat [com.jepretaja.app.data.repository.ExploreRepository.report]. */
data class ReportModel(
    @DocumentId val reportId: String = "",
    val reporterId: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val reason: String = "",
    val status: String = "open",
    val createdAt: Timestamp? = null,
)
