package com.jepretaja.app.ui.screens.search

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.asFlow
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.ExplorePostModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@HiltViewModel
class SearchResultViewModel @Inject constructor(
    private val db: FirebaseFirestore,
) : ViewModel() {

    /**
     * Hasil creator.
     *
     * [lat]/[lng] hanya dipakai saat urutannya "Terdekat"; layar hasil yang
     * meminta izin lokasi, bukan ViewModel, supaya ViewModel tetap bisa diuji
     * tanpa Android.
     */
    fun results(filters: SearchFilters, lat: Double? = null, lng: Double? = null): Flow<List<CreatorModel>> {
        var q = db.collection(FirestorePaths.CREATORS).whereEqualTo("status", "active") as Query
        if (filters.verifiedOnly) q = q.whereEqualTo("verified", true)

        // Urutan yang dipakai SERVER. "Relevan" dan "Terdekat" tidak bisa
        // diminta ke Firestore — keduanya butuh perbandingan dengan kata kunci
        // atau posisi pengguna yang tidak ada di indeks — jadi keduanya memakai
        // rating sebagai jaring pengambilan, lalu diurutkan ulang di klien.
        q = when (filters.sort) {
            SearchSort.HARGA_TERMURAH -> q.orderBy("minPrice", Query.Direction.ASCENDING)
            SearchSort.HARGA_TERMAHAL -> q.orderBy("minPrice", Query.Direction.DESCENDING)
            SearchSort.RATING, SearchSort.RELEVAN, SearchSort.TERDEKAT ->
                q.orderBy("rating", Query.Direction.DESCENDING)
        }

        // "Terdekat" perlu jendela lebih lebar: 60 creator dengan rating
        // tertinggi belum tentu memuat satu pun yang benar-benar dekat.
        val batas = if (filters.sort == SearchSort.TERDEKAT) 200L else 60L

        return q.limit(batas).asFlow<CreatorModel>()
            .map { list -> urutkan(list.filter { cocok(it, filters) }, filters, lat, lng) }
            .catch { emit(emptyList()) }
    }

    private fun cocok(c: CreatorModel, f: SearchFilters): Boolean {
        val kata = f.query?.trim()
        val cocokKata = kata.isNullOrBlank() ||
            c.displayName.contains(kata, ignoreCase = true) ||
            (c.city?.contains(kata, ignoreCase = true) == true) ||
            c.categories.any { it.contains(kata, ignoreCase = true) }
        return cocokKata &&
            (f.categories.isEmpty() || c.categories.any { it in f.categories }) &&
            (f.minRating == null || c.rating >= f.minRating!!) &&
            // Creator tanpa harga TIDAK lolos filter harga. Sebelumnya ia lolos
            // begitu saja, jadi memasang batas "di bawah Rp2jt" tetap menampilkan
            // creator yang harganya tidak diketahui sama sekali — persis hal
            // yang ingin dihindari orang saat memasang batas anggaran.
            (f.minPrice == null || (c.minPrice != null && c.minPrice!! >= f.minPrice!!)) &&
            (f.maxPrice == null || (c.minPrice != null && c.minPrice!! <= f.maxPrice!!))
    }

    private fun urutkan(
        list: List<CreatorModel>,
        f: SearchFilters,
        lat: Double?,
        lng: Double?,
    ): List<CreatorModel> = when (f.sort) {
        // Urutan harga & rating sudah ditentukan server; menyusunnya ulang di
        // sini hanya akan membuat dua sumber kebenaran yang bisa berselisih.
        SearchSort.HARGA_TERMURAH, SearchSort.HARGA_TERMAHAL, SearchSort.RATING -> list

        SearchSort.TERDEKAT -> {
            if (lat == null || lng == null) list
            else list.mapNotNull { c ->
                val cLat = c.serviceLat ?: return@mapNotNull null
                val cLng = c.serviceLng ?: return@mapNotNull null
                c to haversineKm(lat, lng, cLat, cLng)
            }.sortedBy { it.second }.map { it.first }
        }

        SearchSort.RELEVAN -> list.sortedByDescending { skorRelevansi(it, f.query) }
    }

    /**
     * Skor "Relevan".
     *
     * Firestore tidak punya pencarian teks penuh, jadi relevansi disusun di sini
     * dari hal-hal yang bisa diukur: seberapa langsung nama creator menjawab
     * kata kunci, lalu rating dan bukti sosialnya. Kecocokan nama sengaja
     * diberi bobot jauh di atas rating — orang yang mengetik nama studio ingin
     * studio itu, bukan studio lain yang ratingnya kebetulan 0,2 lebih tinggi.
     */
    private fun skorRelevansi(c: CreatorModel, kata: String?): Double {
        val k = kata?.trim().orEmpty()
        var skor = 0.0
        if (k.isNotBlank()) {
            val nama = c.displayName
            when {
                nama.equals(k, ignoreCase = true) -> skor += 100
                nama.startsWith(k, ignoreCase = true) -> skor += 60
                nama.contains(k, ignoreCase = true) -> skor += 40
            }
            if (c.categories.any { it.equals(k, ignoreCase = true) }) skor += 25
            if (c.city?.contains(k, ignoreCase = true) == true) skor += 15
        }
        skor += c.rating * 3
        // Jumlah ulasan diredam dengan akar: creator berulasan 400 memang lebih
        // terbukti daripada yang berulasan 40, tapi tidak sepuluh kali lebih —
        // tanpa peredaman, satu-dua nama lama akan selalu menempati puncak
        // apa pun yang dicari orang.
        skor += sqrt(c.reviewCount.toDouble())
        if (c.verified) skor += 8
        if (c.acceptingBookings) skor += 5
        return skor
    }

    private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Tab "Karya": post Explore yang cocok dengan kata kunci.
     *
     * Penyaringannya di klien atas 120 post terbaru, bukan lewat query.
     * Firestore tidak punya pencarian teks penuh sama sekali — `whereEqualTo`
     * hanya cocok persis, dan trik awalan (`>=`/`<=`) tidak bisa mencari kata
     * di tengah caption. Pencarian teks yang sesungguhnya butuh layanan indeks
     * terpisah seperti Algolia atau Typesense; sampai itu ada, penyaringan di
     * memori adalah yang paling jujur bisa dijanjikan.
     */
    fun posts(filters: SearchFilters): Flow<List<ExplorePostModel>> {
        val kata = filters.query?.trim().orEmpty()
        return db.collection(FirestorePaths.EXPLORE_POSTS)
            .whereEqualTo("status", "published")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(120).asFlow<ExplorePostModel>()
            .map { list ->
                list.filter { p ->
                    (kata.isBlank() ||
                        p.caption.contains(kata, ignoreCase = true) ||
                        p.creatorName.contains(kata, ignoreCase = true) ||
                        p.category.contains(kata, ignoreCase = true)) &&
                        (filters.categories.isEmpty() || p.category in filters.categories)
                }
            }
            .catch { emit(emptyList()) }
    }
}
