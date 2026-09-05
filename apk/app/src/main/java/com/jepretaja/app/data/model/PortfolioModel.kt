package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Status satu album portfolio.
 *
 * `active` dan `hidden` adalah kosakata yang SUDAH dipakai server dan panel
 * moderasi admin, jadi keduanya dipertahankan apa adanya — mengganti namanya
 * akan membuat album lama tak dikenali dan album baru tak terbaca panel web.
 * Tiga sisanya menambah tahap yang sebelumnya tidak ada sama sekali.
 */
object PortfolioStatus {
    /** Belum diajukan. Tidak pernah tampil ke publik. */
    const val DRAFT = "draft"

    /** Sudah diajukan, menunggu moderator. Belum tampil ke publik. */
    const val MENUNGGU = "pending_review"

    /** Disetujui dan tayang. Nilai lama semua album yang sudah ada. */
    const val DISETUJUI = "active"

    /** Ditolak moderator, dengan alasan di [PortfolioModel.rejectedReason]. */
    const val DITOLAK = "rejected"

    /** Diturunkan admin dari panel web setelah sempat tayang. */
    const val DISEMBUNYIKAN = "hidden"

    /** Hanya status ini yang boleh terlihat orang lain di profil creator. */
    val TAYANG = setOf(DISETUJUI)
}

data class PortfolioModel(
    @DocumentId val portfolioId: String = "",
    val creatorId: String = "",
    val media: List<String> = emptyList(),
    val title: String = "",
    val category: String = "",
    val status: String = PortfolioStatus.DISETUJUI,
    /** "image" atau "video" — menentukan ikon dan cara membuka pratinjaunya. */
    val type: String = "image",
    /**
     * Sampul untuk album video.
     *
     * Video tidak bisa dipakai langsung sebagai gambar oleh Coil, jadi tanpa
     * sampul album video tampil sebagai kotak abu-abu kosong di grid.
     */
    val thumbnailUrl: String? = null,
    /**
     * Urutan tampil, kecil lebih dulu.
     *
     * Diurutkan di KLIEN, bukan lewat `orderBy` Firestore: album yang dibuat
     * sebelum field ini ada tidak punya `order`, dan dokumen tanpa field yang
     * diurutkan akan hilang seluruhnya dari hasil query.
     */
    val order: Long = 0,
    val rejectedReason: String? = null,
    val submittedAt: Timestamp? = null,
    val createdAt: Timestamp? = null,
)
