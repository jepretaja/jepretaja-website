package com.jepretaja.app.ui.screens.search

import java.io.Serializable

/**
 * Model filter & sort Search.
 *
 * WAJIB Serializable supaya bisa dikirim lewat SavedStateHandle antar route
 * (Navigation Compose menyimpannya di Bundle).
 *
 * Perubahan dibanding versi sebelumnya:
 *
 * 1. **Harga jadi rentang, bukan cuma batas atas.** `maxPrice` sendirian tidak
 *    bisa menjawab "paket menengah" — pengguna yang sengaja menghindari creator
 *    termurah tidak punya cara menyatakannya.
 * 2. **`minRating` jadi Double.** Ambang yang paling sering dipakai orang adalah
 *    4,5; dengan Int satu-satunya pilihan di sekitar situ adalah 4 atau 5, dan 5
 *    hampir selalu mengosongkan hasil.
 * 3. **Urutan memakai kosakata yang dimengerti pengguna** (lihat [SearchSort]).
 */
data class SearchFilters(
    val query: String? = null,
    val categories: List<String> = emptyList(),
    val minRating: Double? = null,
    val minPrice: Long? = null,
    val maxPrice: Long? = null,
    val verifiedOnly: Boolean = false,
    val sort: SearchSort = SearchSort.RELEVAN,
) : Serializable {

    val hargaAktif: Boolean get() = minPrice != null || maxPrice != null
    val ratingAktif: Boolean get() = minRating != null
    val layananAktif: Boolean get() = categories.isNotEmpty()

    /**
     * Berapa banyak KELOMPOK filter yang aktif — bukan berapa nilai yang dipilih.
     *
     * Angka inilah yang ditempel di lencana tombol Filter. Memilih tiga kategori
     * sekaligus tetap dihitung satu, karena yang ingin diketahui pengguna dari
     * lencana adalah "ada berapa hal yang mempersempit hasilku", bukan jumlah
     * ketukan yang pernah ia lakukan.
     *
     * Urutan sengaja TIDAK ikut dihitung: mengurutkan tidak membuang satu pun
     * hasil, jadi menampilkannya sebagai filter aktif akan membuat pengguna
     * mengira hasilnya sedang disaring padahal tidak.
     */
    val jumlahFilterAktif: Int
        get() = listOf(hargaAktif, ratingAktif, layananAktif, verifiedOnly).count { it }

    val adaFilterAktif: Boolean get() = jumlahFilterAktif > 0

    /** Reset: buang seluruh penyaring, tapi pertahankan kata kunci & urutan. */
    fun direset(): SearchFilters = SearchFilters(query = query, sort = sort)

    /** Ringkasan pendek untuk label chip, mis. "Rp1jt – Rp3jt" atau "≤ Rp5jt". */
    fun ringkasanHarga(): String? = when {
        minPrice != null && maxPrice != null -> "${singkatRupiah(minPrice)} – ${singkatRupiah(maxPrice)}"
        minPrice != null -> "≥ ${singkatRupiah(minPrice)}"
        maxPrice != null -> "≤ ${singkatRupiah(maxPrice)}"
        else -> null
    }

    fun ringkasanRating(): String? = minRating?.let { "${formatRating(it)}+" }

    fun ringkasanLayanan(): String? = when (categories.size) {
        0 -> null
        1 -> categories.first()
        else -> "${categories.first()} +${categories.size - 1}"
    }

    companion object {
        private const val serialVersionUID: Long = 2L
    }
}

/**
 * Pilihan urutan.
 *
 * Label ditulis sebagai jawaban atas "diurutkan bagaimana", bukan nama kolom
 * database. "Price" tidak pernah memberi tahu pengguna apakah yang muncul
 * duluan termurah atau termahal; "Harga termurah" tidak bisa salah paham.
 */
enum class SearchSort(val label: String) {
    RELEVAN("Relevan"),
    RATING("Rating tertinggi"),
    HARGA_TERMURAH("Harga termurah"),
    HARGA_TERMAHAL("Harga termahal"),
    TERDEKAT("Terdekat"),
}

/** "Rp1,5jt" / "Rp750rb" — dipakai di label chip yang ruangnya sempit. */
internal fun singkatRupiah(nilai: Long): String = when {
    nilai >= 1_000_000_000 -> "Rp" + bilangan(nilai / 1_000_000_000.0) + "m"
    nilai >= 1_000_000 -> "Rp" + bilangan(nilai / 1_000_000.0) + "jt"
    nilai >= 1_000 -> "Rp" + bilangan(nilai / 1_000.0) + "rb"
    else -> "Rp$nilai"
}

private fun bilangan(nilai: Double): String =
    if (nilai % 1.0 == 0.0) nilai.toInt().toString() else "%.1f".format(nilai).replace('.', ',')

internal fun formatRating(nilai: Double): String =
    if (nilai % 1.0 == 0.0) nilai.toInt().toString() else "%.1f".format(nilai).replace('.', ',')
