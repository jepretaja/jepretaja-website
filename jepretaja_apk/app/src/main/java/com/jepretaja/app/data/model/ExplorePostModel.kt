package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class ExplorePostModel(
    @DocumentId val postId: String = "",
    val creatorId: String = "",
    val creatorName: String = "",
    val creatorPhotoUrl: String? = null,
    val creatorVerified: Boolean = false,
    val type: String = "photo", // photo|video
    val mediaUrls: List<String> = emptyList(),
    val thumbnailUrl: String? = null,
    val caption: String = "",
    val category: String = "",
    val location: String? = null,
    val metrics: Map<String, Long> = emptyMap(),
    val status: String = "published", // draft|processing|pending_review|published|rejected|hidden|processing_failed
    val durationSeconds: Long? = null,
    val processingError: String? = null,
    /** Alasan yang ditulis admin saat menolak atau menyembunyikan karya. */
    val moderationNote: String? = null,
    /** Hashtag hasil pindaian caption saat diunggah, tanpa tanda pagar. */
    val tags: List<String> = emptyList(),
    /** Creator yang disebut di caption: tiap item berisi "name" dan "creatorId". */
    val mentions: List<Map<String, String>> = emptyList(),
    /** all | followers | off — siapa yang boleh berkomentar. */
    val commentPolicy: String = "all",
    /** Creator bisa mematikan tombol simpan untuk karyanya. */
    val allowSave: Boolean = true,

    /**
     * Paket jasa yang dipakai di sesi ini.
     *
     * Nama dan harganya ikut disimpan (denormalisasi) supaya kartu di feed tidak
     * perlu membaca satu dokumen paket per post saat menggulir. Konsekuensinya
     * jujur: kalau creator mengubah harga paketnya, post lama tetap menampilkan
     * harga saat karya itu diunggah — dan justru itu yang benar, karena harga
     * itulah yang berlaku ketika foto tersebut dikerjakan.
     */
    val packageId: String? = null,
    val packageName: String? = null,
    val packagePrice: Long? = null,
    /** Komentar yang disematkan creator di puncak daftar komentar. */
    val pinnedCommentId: String? = null,
    /**
     * Waktu unggah dari server. Dibutuhkan bukan cuma untuk ditampilkan:
     * FeedRanker memakainya menghitung kesegaran, dan tanpa field ini setiap
     * post dianggap berumur sama sehingga peringkat "For You" runtuh jadi
     * urutan keterlibatan saja.
     */
    val createdAt: Timestamp? = null,
) {
    val likeCount get() = metrics["like"] ?: 0
    val commentCount get() = metrics["comment"] ?: 0
    val saveCount get() = metrics["save"] ?: 0
    val shareCount get() = metrics["share"] ?: 0
    val viewCount get() = metrics["view"] ?: 0
}
