package com.jepretaja.app.data.local

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Penyimpanan kecil di perangkat untuk keputusan yang harus DIINGAT antar
 * pembukaan aplikasi.
 *
 * Sebelumnya tidak ada penyimpanan seperti ini sama sekali, dan akibatnya
 * aplikasi terasa "seperti baru dipasang" setiap kali dibuka ulang: Splash
 * mengirim siapa pun yang belum login ke Onboarding, sehingga pengguna yang
 * memilih "Lanjut sebagai Tamu" harus melewati empat halaman perkenalan lagi
 * dan lagi, setiap kali membuka aplikasi.
 *
 * SharedPreferences dipilih (bukan DataStore) karena yang disimpan hanya dua
 * penanda boolean dan nilainya dibutuhkan pada saat paling awal aplikasi jalan
 * — menambah dependensi baru untuk itu tidak sepadan.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jepretaja_prefs", Context.MODE_PRIVATE)

    /** Sudah pernah melihat halaman perkenalan sampai selesai/dilewati. */
    var onboardingSeen: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_SEEN, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, value).apply()

    /**
     * Pengguna sengaja memilih menjelajah tanpa akun.
     *
     * Dibedakan dari "belum login" biasa: tamu yang sudah memilih ingin
     * langsung masuk ke Home saat membuka aplikasi lagi, bukan dilempar balik
     * ke halaman pilih-akses seolah pilihannya tidak pernah ada.
     */
    var guestMode: Boolean
        get() = prefs.getBoolean(KEY_GUEST_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_GUEST_MODE, value).apply()

    /**
     * Riwayat kata kunci pencarian, terbaru di depan, maksimal 8.
     *
     * Disimpan di perangkat, bukan di Firestore: riwayat pencarian adalah
     * jejak minat pribadi, dan menyimpannya di server berarti membuat catatan
     * itu ada di tempat yang bisa dibaca pihak lain padahal tidak ada satu pun
     * fitur yang membutuhkannya di sana.
     *
     * Dipisah dengan "\n" — kata kunci pencarian tidak pernah memuat baris baru.
     */
    var searchHistory: List<String>
        get() = prefs.getString(KEY_SEARCH_HISTORY, null)
            ?.split("\n")
            ?.filter { it.isNotBlank() }
            .orEmpty()
        private set(value) = prefs.edit().putString(KEY_SEARCH_HISTORY, value.joinToString("\n")).apply()

    fun rememberSearch(query: String) {
        val bersih = query.trim()
        if (bersih.isEmpty()) return
        // Kata kunci yang sama diangkat ke depan, bukan ditumpuk dua kali.
        searchHistory = (listOf(bersih) + searchHistory.filterNot { it.equals(bersih, ignoreCase = true) })
            .take(MAX_SEARCH_HISTORY)
    }

    fun removeSearch(query: String) {
        searchHistory = searchHistory.filterNot { it.equals(query, ignoreCase = true) }
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    /**
     * Daftar draft unggahan.
     *
     * Sebelumnya hanya ada SATU slot draft: menyimpan draft kedua diam-diam
     * menimpa yang pertama. Sekarang disimpan sebagai array JSON, jadi creator
     * bisa menumpuk beberapa ide sebelum memilih mana yang dikirim.
     *
     * Tetap di perangkat, bukan Firestore: karya setengah jadi belum tentu akan
     * pernah tayang, dan menulisnya sebagai dokumen berstatus "draft" berarti
     * menaruhnya di tempat yang ikut dibaca aturan, moderasi, dan penghitung.
     *
     * Uri media disimpan sebagai teks. Izin baca atas Uri dari pemilih berkas
     * bisa hangus setelah aplikasi ditutup, jadi layar unggah tetap harus siap
     * menghadapi media draft yang gagal dibuka.
     */
    val drafts: List<DraftTersimpan>
        get() = runCatching {
            val array = JSONArray(prefs.getString(KEY_DRAFTS, "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                DraftTersimpan(
                    id = o.optString("id"),
                    caption = o.optString("caption"),
                    category = o.optString("category"),
                    target = o.optString("target", "Explore"),
                    mediaUris = o.optJSONArray("mediaUris")?.let { m ->
                        (0 until m.length()).map { m.getString(it) }
                    }.orEmpty(),
                    isVideo = o.optBoolean("isVideo"),
                    savedAt = o.optLong("savedAt"),
                )
            }.sortedByDescending { it.savedAt }
        }.getOrDefault(emptyList())

    fun simpanDraft(draft: DraftTersimpan) {
        val lain = drafts.filterNot { it.id == draft.id }
        tulisDrafts((listOf(draft) + lain).take(MAX_DRAFTS))
    }

    fun hapusDraft(id: String) = tulisDrafts(drafts.filterNot { it.id == id })

    private fun tulisDrafts(daftar: List<DraftTersimpan>) {
        val array = JSONArray()
        daftar.forEach { d ->
            array.put(
                JSONObject()
                    .put("id", d.id)
                    .put("caption", d.caption)
                    .put("category", d.category)
                    .put("target", d.target)
                    .put("mediaUris", JSONArray().apply { d.mediaUris.forEach { put(it) } })
                    .put("isVideo", d.isVideo)
                    .put("savedAt", d.savedAt)
            )
        }
        prefs.edit().putString(KEY_DRAFTS, array.toString()).apply()
    }

    /**
     * Penanda kapan kehadiran terakhir ditulis ke Firestore.
     *
     * Ada di perangkat, bukan di server: gunanya justru MENGHINDARI pembacaan
     * dan penulisan server hanya untuk memutuskan apakah perlu menulis.
     */
    var lastPresenceWrite: Long
        get() = prefs.getLong(KEY_PRESENCE, 0L)
        set(value) = prefs.edit().putLong(KEY_PRESENCE, value).apply()

    /**
     * Kategori yang diminati pengguna, dipilih saat pertama masuk.
     *
     * Disimpan lokal DAN di dokumen users (lihat AuthRepository): salinan lokal
     * dipakai peringkat feed supaya tidak perlu satu pembacaan Firestore setiap
     * kali feed dihitung ulang, sementara salinan server membuat minat ikut
     * pindah saat pengguna berganti perangkat.
     */
    var interests: List<String>
        get() = prefs.getString(KEY_INTERESTS, null)?.split("|")?.filter { it.isNotBlank() }.orEmpty()
        set(value) = prefs.edit().putString(KEY_INTERESTS, value.joinToString("|")).apply()

    val interestsChosen: Boolean get() = prefs.getBoolean(KEY_INTERESTS_DONE, false)
    fun markInterestsChosen() = prefs.edit().putBoolean(KEY_INTERESTS_DONE, true).apply()

    /**
     * Post yang ditandai "tidak tertarik".
     *
     * Lokal, bukan di server. Ini sinyal negatif yang sangat pribadi, dan
     * satu-satunya gunanya adalah menyaring feed di perangkat ini juga —
     * mengirimkannya ke server berarti menyimpan daftar hal yang tidak disukai
     * seseorang tanpa ada fitur yang benar-benar membutuhkannya di sana.
     */
    var notInterested: List<String>
        get() = prefs.getStringSet(KEY_NOT_INTERESTED, emptySet()).orEmpty().toList()
        private set(value) = prefs.edit().putStringSet(KEY_NOT_INTERESTED, value.takeLast(300).toSet()).apply()

    fun addNotInterested(postId: String) {
        notInterested = notInterested.filterNot { it == postId } + postId
    }

    /**
     * Riwayat tontonan: id post, terbaru di depan, maksimal 100.
     *
     * Juga dipakai peringkat feed untuk menurunkan karya yang sudah dilihat —
     * tanpa itu, post yang sama terus muncul di urutan atas setiap kali feed
     * dibuka.
     */
    var watchHistory: List<String>
        get() = prefs.getString(KEY_WATCH_HISTORY, null)?.split("|")?.filter { it.isNotBlank() }.orEmpty()
        private set(value) = prefs.edit().putString(KEY_WATCH_HISTORY, value.take(100).joinToString("|")).apply()

    fun addWatched(postId: String) {
        val sekarang = watchHistory
        if (sekarang.firstOrNull() == postId) return
        watchHistory = listOf(postId) + sekarang.filterNot { it == postId }
    }

    fun clearWatchHistory() = prefs.edit().remove(KEY_WATCH_HISTORY).apply()

    /**
     * Booking yang penggunanya sudah menekan "Saya sudah transfer".
     *
     * Disimpan lokal karena server memang tidak punya keadaan untuk itu:
     * pembayaran melompat dari `awaiting_transfer` langsung ke `paid` begitu
     * admin memverifikasi. Tanpa catatan ini, orang yang sudah mentransfer lalu
     * membuka layar pembayaran lagi akan disambut "Menunggu pembayaran" berikut
     * hitung mundurnya — dibuat panik atas sesuatu yang sudah ia lakukan.
     *
     * Catatan ini sengaja TIDAK dianggap bukti apa pun. Ia hanya mengubah
     * kalimat yang dibaca pengguna; yang menentukan tetap verifikasi admin.
     */
    var deklarasiTransfer: Set<String>
        get() = prefs.getStringSet(KEY_DEKLARASI_TRANSFER, emptySet()).orEmpty()
        private set(value) = prefs.edit().putStringSet(KEY_DEKLARASI_TRANSFER, value.toList().takeLast(50).toSet()).apply()

    fun tandaiSudahTransfer(bookingId: String) { deklarasiTransfer = deklarasiTransfer + bookingId }

    fun lupakanDeklarasiTransfer(bookingId: String) { deklarasiTransfer = deklarasiTransfer - bookingId }

    /** Dipanggil saat logout supaya sesi tamu tidak ikut terbawa. */
    fun clearGuestMode() {
        prefs.edit().remove(KEY_GUEST_MODE).apply()
    }

    private companion object {
        const val KEY_ONBOARDING_SEEN = "onboarding_seen"
        const val KEY_GUEST_MODE = "guest_mode"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_DRAFTS = "drafts_json"
        const val KEY_PRESENCE = "presence_last_write"
        const val KEY_INTERESTS = "interests"
        const val KEY_INTERESTS_DONE = "interests_done"
        const val KEY_NOT_INTERESTED = "not_interested"
        const val KEY_WATCH_HISTORY = "watch_history"
        const val KEY_DEKLARASI_TRANSFER = "deklarasi_transfer"
        const val MAX_DRAFTS = 20
        const val MAX_SEARCH_HISTORY = 8
    }
}

/** Satu draft unggahan yang tersimpan di perangkat. */
data class DraftTersimpan(
    val id: String,
    val caption: String,
    val category: String,
    val target: String,
    val mediaUris: List<String>,
    val isVideo: Boolean,
    val savedAt: Long,
)
