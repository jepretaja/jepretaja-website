package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Satu hubungan "mengikuti", id dokumennya `{creatorId}_{userId}`.
 *
 * Nama dan foto KEDUA pihak ikut disimpan di dokumen ini (denormalisasi).
 * Bukan pengulangan yang sia-sia: daftar pengikut berisi akun konsumen, dan
 * dokumen `users` hanya boleh dibaca pemiliknya sendiri karena memuat email,
 * nomor telepon, dan token FCM. Tanpa nama yang tersimpan di sini, satu-satunya
 * cara menampilkan daftar pengikut adalah membuka data pribadi semua pengguna
 * ke semua orang.
 *
 * Dokumen lama yang dibuat sebelum perubahan ini tidak punya field nama, jadi
 * tampil sebagai "Pengguna" — data lama yang tidak lengkap, bukan kegagalan.
 */
data class FollowModel(
    @DocumentId val id: String = "",
    val creatorId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val creatorName: String = "",
    val creatorPhotoUrl: String? = null,
    val createdAt: Timestamp? = null,
)
