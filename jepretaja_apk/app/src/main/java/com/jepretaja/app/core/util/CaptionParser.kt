package com.jepretaja.app.core.util

/**
 * Pemindai caption untuk hashtag dan sebutan (@).
 *
 * Hasil pindaian hashtag disimpan ke field `tags` saat unggah dan saat caption
 * diedit. Alasannya teknis: Firestore tidak bisa mencari substring di dalam
 * teks, jadi halaman tagar mustahil dibuat kalau tagarnya hanya "ada di dalam
 * caption" — ia harus jadi array yang bisa dicocokkan `array-contains`.
 *
 * Tagar disimpan huruf kecil semua supaya #Wedding dan #wedding tidak jadi dua
 * halaman berbeda.
 */
object CaptionParser {

    private val REGEX_TAGAR = Regex("#([\\p{L}0-9_]{1,50})")
    private val REGEX_SEBUTAN = Regex("@([\\p{L}0-9_. ]{1,40})")

    fun hashtags(caption: String): List<String> =
        REGEX_TAGAR.findAll(caption)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(20)
            .toList()

    /** Rentang posisi tiap tagar di dalam teks, untuk dibuat bisa diketuk. */
    fun hashtagRanges(caption: String): List<Pair<IntRange, String>> =
        REGEX_TAGAR.findAll(caption).map { it.range to it.groupValues[1].lowercase() }.toList()

    /**
     * Rentang sebutan yang cocok dengan daftar [namaTerdaftar].
     *
     * Pencocokan dibatasi pada nama yang memang tercatat di field `mentions`
     * post — tanpa itu, setiap kata setelah "@" akan tampak seperti tautan
     * padahal tidak menuju ke mana-mana.
     */
    fun mentionRanges(caption: String, namaTerdaftar: Collection<String>): List<Pair<IntRange, String>> {
        if (namaTerdaftar.isEmpty()) return emptyList()
        val hasil = mutableListOf<Pair<IntRange, String>>()
        // Nama terpanjang dicoba lebih dulu supaya "@Budi Santoso" tidak
        // terpotong jadi "@Budi" ketika keduanya sama-sama terdaftar.
        val urut = namaTerdaftar.filter { it.isNotBlank() }.sortedByDescending { it.length }
        for (nama in urut) {
            var mulai = caption.indexOf("@$nama", ignoreCase = true)
            while (mulai >= 0) {
                val akhir = mulai + nama.length
                if (hasil.none { mulai in it.first || akhir in it.first }) {
                    hasil += (mulai..akhir) to nama
                }
                mulai = caption.indexOf("@$nama", mulai + 1, ignoreCase = true)
            }
        }
        return hasil
    }
}
