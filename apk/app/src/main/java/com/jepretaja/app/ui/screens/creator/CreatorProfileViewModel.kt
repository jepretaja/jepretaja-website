package com.jepretaja.app.ui.screens.creator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.model.PackageModel
import com.jepretaja.app.data.model.PortfolioModel
import com.jepretaja.app.data.model.ReviewModel
import com.jepretaja.app.data.repository.AvailabilityRepository
import com.jepretaja.app.data.repository.ChatRepository
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.data.repository.ExploreRepository
import com.jepretaja.app.data.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CreatorProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    val creatorRepository: CreatorRepository,
    private val chatRepository: ChatRepository,
    private val reviewRepository: ReviewRepository,
    private val availabilityRepository: AvailabilityRepository,
    private val exploreRepository: ExploreRepository,
) : ViewModel() {
    val creatorId: String = checkNotNull(savedStateHandle["creatorId"])

    private val _creator = MutableStateFlow<CreatorModel?>(null)
    val creator: StateFlow<CreatorModel?> = _creator.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true; _error.value = null
            try {
                _creator.value = creatorRepository.getCreator(creatorId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal memuat profil creator."
            } finally {
                _loading.value = false
            }
        }
    }

    /** Dua ulasan terbaru untuk ditampilkan langsung di profil. */
    fun ulasanTeratas(): Flow<List<ReviewModel>> =
        reviewRepository.streamForCreator(creatorId).catch { emit(emptyList()) }

    /** Tanggal yang sudah diblokir creator — dipakai ringkasan ketersediaan. */
    fun tanggalPenuh(): Flow<List<LocalDate>> =
        availabilityRepository.streamBlockedDates(creatorId).catch { emit(emptyList()) }

    /** Berapa creator yang diikuti profil ini — angka "Mengikuti" ala TikTok. */
    fun jumlahMengikuti(): Flow<Int> =
        exploreRepository.streamFollowingCount(creatorId).catch { emit(0) }

    /**
     * Karya Explore milik creator ini.
     *
     * Dipakai dua hal sekaligus: isi tab Explore DAN angka "suka" di kepala
     * profil. Sebelumnya keduanya membuka listener Firestore sendiri-sendiri ke
     * koleksi yang sama — dua langganan, dua kali biaya baca, untuk data yang
     * persis sama. Total suka sekarang dijumlahkan di layar dari daftar yang
     * sudah ada di tangan.
     */
    fun karyaExplore(): Flow<List<ExplorePostModel>> =
        exploreRepository.streamByCreator(creatorId).catch { emit(emptyList()) }

    fun portofolio(): Flow<List<PortfolioModel>> =
        creatorRepository.streamPortfolio(creatorId).catch { emit(emptyList()) }

    fun paket(): Flow<List<PackageModel>> =
        creatorRepository.streamPackages(creatorId).catch { emit(emptyList()) }

    /**
     * Memblokir creator.
     *
     * Memakai daftar blokir yang sama dengan chat: memblokir seseorang di
     * marketplace jasa hampir selalu berarti "jangan biarkan dia menghubungi
     * saya lagi", jadi membuat daftar blokir kedua yang terpisah hanya akan
     * membuat dua tempat yang bisa saling bertentangan.
     */
    fun blokir(myUid: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { chatRepository.blockUser(myUid, creatorId) }
            onDone()
        }
    }

    fun apakahDiikuti(myUid: String): Flow<Boolean> =
        exploreRepository.isFollowing(creatorId, myUid).catch { emit(false) }

    fun toggleFollow(
        myUid: String,
        userName: String,
        userPhotoUrl: String?,
    ) {
        val c = _creator.value ?: return
        viewModelScope.launch {
            runCatching {
                exploreRepository.toggleFollow(
                    creatorId = creatorId,
                    userId = myUid,
                    userName = userName,
                    userPhotoUrl = userPhotoUrl,
                    creatorName = c.displayName,
                    creatorPhotoUrl = c.photoUrl,
                )
            }
            // Jumlah pengikut dibaca ulang supaya angkanya ikut berubah
            // seketika, bukan baru betul setelah layar dibuka lagi.
            _creator.value = runCatching { creatorRepository.getCreator(creatorId) }.getOrNull() ?: c
        }
    }

    fun laporkan(myUid: String, alasan: String, onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { exploreRepository.reportCreator(creatorId, myUid, alasan) }
            onDone()
        }
    }

    suspend fun openChat(myUid: String, creatorName: String): String =
        chatRepository.getOrCreateDirectChat(myUid, creatorId, creatorName)
}
