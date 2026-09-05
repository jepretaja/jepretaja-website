package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

/**
 * Jam kerja creator.
 *
 * [days] memakai angka 1–7 dengan 1 = Senin (ISO), bukan penomoran Calendar
 * Android yang dimulai dari Minggu — supaya nilainya sama dibaca panel web
 * JavaScript maupun aplikasi.
 */
data class WorkingHours(
    val days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    /** "09:00" */
    val start: String = "09:00",
    /** "17:00" */
    val end: String = "17:00",
    val note: String? = null,
)

data class CreatorModel(
    @DocumentId val creatorId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val bio: String? = null,
    val city: String? = null,
    val categories: List<String> = emptyList(),
    val rating: Double = 0.0,
    val reviewCount: Int = 0,
    val followerCount: Int = 0,
    val verified: Boolean = false,
    val status: String = "active",
    val photoUrl: String? = null,
    val coverUrl: String? = null,
    val equipment: List<String> = emptyList(),
    val socialLinks: List<String> = emptyList(),
    val serviceLat: Double? = null,
    val serviceLng: Double? = null,
    val minPrice: Long? = null,

    /**
     * Terakhir kali aplikasi creator ini aktif.
     *
     * Ditulis berkala (paling sering sekali per 5 menit) saat aplikasinya
     * dibuka. Titik hijau di profil dihitung dari sini — bukan dari koneksi
     * socket, yang untuk aplikasi seperti ini terlalu mahal dibanding gunanya.
     */
    val lastActiveAt: Timestamp? = null,

    /**
     * Saklar "menerima booking".
     *
     * Berbeda dari titik hijau kehadiran: hijau hanya berarti aplikasinya baru
     * dibuka, sementara ini keputusan sadar creator apakah pesanan baru boleh
     * masuk. Ditegakkan juga di server (createBooking), bukan cuma menyembunyikan
     * tombol.
     */
    val acceptingBookings: Boolean = true,
    val awayUntil: Timestamp? = null,
    val awayNote: String? = null,

    /** Rata-rata menit balasan chat, diukur di perangkat creator saat ia membalas. */
    val avgResponseMinutes: Int? = null,

    /**
     * Rekening tujuan pencairan dana.
     *
     * Disimpan di dokumen creator, BUKAN di tiap dokumen penarikan, supaya
     * satu-satunya sumber kebenarannya ada di tempat yang juga dibaca panel web
     * saat admin memproses pencairan. Sebelum ini creator harus mengetik ulang
     * nama bank, nomor, dan nama pemilik setiap kali menarik dana — pengulangan
     * yang tidak hanya melelahkan tapi juga tempat paling mudah salah ketik,
     * dan salah satu digit di nomor rekening berarti uang berangkat ke orang
     * lain.
     *
     * Nama fieldnya sengaja sama persis dengan yang dikirim `requestWithdrawal`
     * ke server (`bankCode`, `bankAccountNumber`, `bankAccountName`) supaya
     * aplikasi, server, dan panel admin memakai satu kosakata.
     */
    val bankCode: String? = null,
    val bankAccountNumber: String? = null,
    val bankAccountName: String? = null,
    val bankUpdatedAt: Timestamp? = null,
    /**
     * Jam kerja mingguan.
     *
     * Disimpan di dokumen creator, bukan koleksi terpisah: nilainya satu baris
     * untuk seluruh akun, dibaca setiap kali profil dibuka, dan tidak pernah
     * dicari lintas creator. Field ini tidak termasuk yang dilindungi
     * firestore.rules, jadi pemiliknya boleh menulisnya sendiri.
     */
    val workingHours: WorkingHours? = null,

    /** Karya yang disematkan creator di urutan pertama grid Explore. */
    val pinnedPostId: String? = null,

    // Dihitung server saat status booking berpindah; dilindungi firestore.rules
    // supaya tidak bisa dikarang sendiri oleh pemilik profil.
    val totalBookings: Int = 0,
    val completedBookings: Int = 0,
)
