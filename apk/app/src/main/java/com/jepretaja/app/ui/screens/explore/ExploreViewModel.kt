package com.jepretaja.app.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.core.util.FeedRanker
import com.jepretaja.app.data.local.AppPreferences
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.data.repository.ExploreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val repository: ExploreRepository,
    private val creatorRepository: CreatorRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    /** Batas feed yang tumbuh 30 setiap kali pengguna mendekati ujung. */
    private val _limit = MutableStateFlow(30L)

    /** True kalau server sudah memberi lebih sedikit dari yang diminta. */
    private val _habis = MutableStateFlow(false)
    val habis: StateFlow<Boolean> = _habis.asStateFlow()

    private val _memuatLagi = MutableStateFlow(false)
    val memuatLagi: StateFlow<Boolean> = _memuatLagi.asStateFlow()

    /** Kecepatan putar video, dipilih lewat menu tekan-lama. */
    private val _kecepatan = MutableStateFlow(1f)
    val kecepatan: StateFlow<Float> = _kecepatan.asStateFlow()
    fun setKecepatan(nilai: Float) { _kecepatan.value = nilai }

    private val _tidakTertarik = MutableStateFlow(prefs.notInterested.toSet())

    /**
     * Dipanggil saat pengguna mendekati ujung feed.
     *
     * Menaikkan batas, bukan mengambil halaman terpisah — lihat streamFeed di
     * repository untuk alasannya.
     */
    fun muatLagi() {
        if (_habis.value || _memuatLagi.value) return
        _memuatLagi.value = true
        _limit.value = _limit.value + 30
    }

    /** Menyegarkan: batas dikembalikan ke awal dan daftar dihitung ulang. */
    fun segarkan() {
        _habis.value = false
        _limit.value = 30
    }

    fun tidakTertarik(postId: String) {
        prefs.addNotInterested(postId)
        _tidakTertarik.value = prefs.notInterested.toSet()
        _message.value = "Karya serupa akan lebih jarang muncul."
    }
    private val _activeTab = MutableStateFlow("For You")
    val activeTab: StateFlow<String> = _activeTab.asStateFlow()

    /** Diisi layar begitu tahu siapa yang sedang masuk — dibutuhkan tab
     * "Following" yang isinya bergantung pada akun. */
    private val _userId = MutableStateFlow<String?>(null)
    fun setUserId(uid: String?) { _userId.value = uid }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    /** Lokasi terakhir yang diketahui — diisi layar setelah izin diberikan,
     * dibutuhkan tab "Nearby". */
    private val _location = MutableStateFlow<Pair<Double, Double>?>(null)
    fun setLocation(lat: Double, lng: Double) { _location.value = lat to lng }

    private val _needsLocation = MutableStateFlow(false)
    val needsLocation: StateFlow<Boolean> = _needsLocation.asStateFlow()

    private data class KunciFeed(
        val tab: String,
        val uid: String?,
        val loc: Pair<Double, Double>?,
        val limit: Long,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val mentah: Flow<List<ExplorePostModel>> =
        combine(_activeTab, _userId, _location, _limit) { tab, uid, loc, limit ->
            KunciFeed(tab, uid, loc, limit)
        }
            .flatMapLatest { (tab, uid, loc, limit) ->
                // "Following" dan "Nearby" butuh sumber yang berbeda; sebelumnya
                // keduanya dibuang begitu saja di streamFeed() sehingga kedua tab
                // menampilkan feed umum yang sama persis dengan "For You".
                when {
                    tab == "Following" && uid != null -> {
                        _needsLocation.value = false
                        repository.streamFollowingFeed(uid).catch { emit(emptyList()) }
                    }
                    tab == "Nearby" -> {
                        _needsLocation.value = loc == null
                        if (loc == null) flowOf(emptyList())
                        else repository.streamNearbyFeed(loc.first, loc.second).catch { emit(emptyList()) }
                    }
                    else -> {
                        _needsLocation.value = false
                        repository.streamFeed(tab, limit).catch { emit(emptyList()) }
                    }
                }
            }
            .onEach { daftar ->
                _memuatLagi.value = false
                // Server memberi lebih sedikit dari yang diminta => tidak ada
                // lagi yang bisa diambil. Penanda inilah yang membuat feed bisa
                // berkata "kamu sudah lihat semua" alih-alih diam saja.
                if (daftar.size < _limit.value) _habis.value = true
            }

    /**
     * Feed siap tampil: disaring dari "tidak tertarik", lalu diperingkat untuk
     * tab For You.
     *
     * Tab lain (kategori, Following, Nearby) sengaja TIDAK diperingkat ulang:
     * di sana pengguna sudah menyatakan apa yang ia mau, dan mengaduk urutannya
     * hanya membuat hasilnya sulit ditebak.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val posts: StateFlow<List<ExplorePostModel>> =
        combine(mentah, _activeTab, _userId, _tidakTertarik) { daftar, tab, uid, tolak ->
            Triple(daftar, tab, uid) to tolak
        }
            .flatMapLatest { (bagian, tolak) ->
                val (daftar, tab, uid) = bagian
                val diikuti = if (uid != null) {
                    repository.streamFollowedIds(uid).catch { emit(emptySet()) }
                } else {
                    flowOf(emptySet())
                }
                diikuti.map { ids ->
                    if (tab != "For You") {
                        daftar.filterNot { it.postId in tolak }
                    } else {
                        FeedRanker.rank(
                            posts = daftar,
                            interests = prefs.interests.toSet(),
                            followedCreatorIds = ids,
                            watched = prefs.watchHistory.toSet(),
                            notInterested = tolak,
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Creator saran untuk tab Following yang masih kosong. */
    val saranCreator: StateFlow<List<com.jepretaja.app.data.model.CreatorModel>> =
        repository.streamSuggestedCreators()
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Dipanggil feed saat sebuah post benar-benar berhenti di layar. */
    fun registerView(postId: String) = viewModelScope.launch {
        prefs.addWatched(postId)
        runCatching { repository.incrementViewCount(postId) }
    }

    fun setTab(tab: String) { _activeTab.value = tab }

    fun isLiked(postId: String, userId: String) = repository.isLiked(postId, userId)
    fun isSaved(postId: String, userId: String) = repository.isSaved(postId, userId)
    fun isFollowing(creatorId: String, userId: String) = repository.isFollowing(creatorId, userId)

    // runCatching wajib di sini: aksi-aksi ini menulis ke Firestore, dan tanpa
    // pengaman satu kegagalan (aturan menolak, koneksi putus) akan lolos dari
    // coroutine dan menutup aplikasi — hanya karena pengguna menekan "suka".
    /**
     * Menyematkan / melepas sematan satu karya.
     *
     * Penandanya disimpan di dokumen creator (`pinnedPostId`), bukan di
     * dokumen post: yang disematkan selalu satu, dan menyimpannya di satu
     * tempat membuat mustahil ada dua karya yang sama-sama mengaku tersemat.
     */
    fun togglePin(creatorId: String, postId: String) = viewModelScope.launch {
        runCatching {
            val sekarang = creatorRepository.getCreator(creatorId)?.pinnedPostId
            creatorRepository.setPinnedPost(creatorId, if (sekarang == postId) null else postId)
        }.onFailure { _message.value = it.message ?: "Gagal menyematkan karya." }
    }

    fun setCommentPolicy(postId: String, userId: String, policy: String) = viewModelScope.launch {
        runCatching { repository.setCommentPolicy(postId, userId, policy) }
            .onFailure { _message.value = it.message ?: "Gagal mengubah pengaturan komentar." }
    }

    fun like(postId: String, userId: String) = viewModelScope.launch { runCatching { repository.like(postId, userId) } }
    fun save(postId: String, userId: String) = viewModelScope.launch { runCatching { repository.save(postId, userId) } }
    /**
     * Nama & foto kedua pihak ikut dikirim supaya tersimpan di dokumen follow.
     * Daftar Pengikut/Mengikuti membacanya dari sana — koleksi `users` tertutup
     * aturan Firestore, jadi nama tidak bisa diterjemahkan belakangan.
     */
    fun toggleFollow(
        creatorId: String,
        userId: String,
        userName: String = "",
        userPhotoUrl: String? = null,
        creatorName: String = "",
        creatorPhotoUrl: String? = null,
    ) = viewModelScope.launch {
        runCatching {
            repository.toggleFollow(creatorId, userId, userName, userPhotoUrl, creatorName, creatorPhotoUrl)
        }
    }
    fun report(postId: String, userId: String, reason: String) = viewModelScope.launch { runCatching { repository.report(postId, userId, reason) } }
    fun incrementShare(postId: String) = viewModelScope.launch { runCatching { repository.incrementShareCount(postId) } }

    fun deletePost(postId: String, userId: String) = viewModelScope.launch {
        val ok = runCatching { repository.deletePost(postId, userId) }.isSuccess
        _message.value = if (ok) "Post dihapus" else "Gagal menghapus post"
    }

    fun updatePost(postId: String, userId: String, caption: String, category: String) = viewModelScope.launch {
        val ok = runCatching { repository.updatePost(postId, userId, caption, category) }.isSuccess
        _message.value = if (ok) "Post diperbarui" else "Gagal memperbarui post"
    }
}
