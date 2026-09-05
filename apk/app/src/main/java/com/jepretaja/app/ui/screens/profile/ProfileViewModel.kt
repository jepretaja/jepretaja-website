package com.jepretaja.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import com.jepretaja.app.data.model.BookingModel
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.model.ReviewModel
import com.jepretaja.app.data.repository.BookingRepository
import com.jepretaja.app.data.repository.ChatRepository
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.data.repository.ExploreRepository
import com.jepretaja.app.data.repository.NotificationRepository
import com.jepretaja.app.data.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Data profil: dokumen creator (pengikut, rating, bio), karya sendiri di
 * Explore, post tersimpan, dan angka untuk baris statistik ala TikTok.
 *
 * Semua aliran dibungkus `catch`: satu pembacaan yang gagal tidak boleh
 * menjatuhkan seluruh halaman profil, cukup membuat angkanya 0 atau daftarnya
 * kosong.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val creatorRepository: CreatorRepository,
    private val exploreRepository: ExploreRepository,
    private val bookingRepository: BookingRepository,
    private val chatRepository: ChatRepository,
    private val notificationRepository: NotificationRepository,
    private val reviewRepository: ReviewRepository,
) : ViewModel() {

    fun creator(creatorId: String): Flow<CreatorModel?> = flow {
        emit(runCatching { creatorRepository.getCreator(creatorId) }.getOrNull())
    }.catch { emit(null) }

    fun myPosts(creatorId: String): Flow<List<ExplorePostModel>> =
        exploreRepository.streamByCreator(creatorId).catch { emit(emptyList()) }

    fun savedPosts(userId: String): Flow<List<ExplorePostModel>> =
        exploreRepository.streamSavedPosts(userId).catch { emit(emptyList()) }

    fun likedPosts(userId: String): Flow<List<ExplorePostModel>> =
        exploreRepository.streamLikedPosts(userId).catch { emit(emptyList()) }

    fun followingCount(userId: String): Flow<Int> =
        exploreRepository.streamFollowingCount(userId).catch { emit(0) }

    fun myBookings(customerId: String): Flow<List<BookingModel>> =
        bookingRepository.streamCustomerBookings(customerId).catch { emit(emptyList()) }

    /** Angka badge untuk dua tombol pintasan di baris aksi profil. */
    fun unreadChats(userId: String): Flow<Int> =
        chatRepository.streamUnreadCount(userId).catch { emit(0) }

    fun unreadNotifications(userId: String): Flow<Int> =
        notificationRepository.streamUnreadCount(userId).catch { emit(0) }

    /**
     * Ulasan untuk tab Review.
     *
     * Arahnya dibalik menurut peran: creator melihat ulasan yang DITERIMA,
     * konsumen melihat ulasan yang ia TULIS. Keduanya adalah "review saya" bagi
     * pemiliknya, tapi satu koleksi Firestore yang sama dibaca dari dua sisi —
     * dan menampilkan yang keliru berarti tab itu selalu kosong bagi separuh
     * pengguna.
     */
    fun myReviews(userId: String, sebagaiCreator: Boolean): Flow<List<ReviewModel>> =
        (if (sebagaiCreator) {
            reviewRepository.streamForCreator(userId)
        } else {
            reviewRepository.streamByCustomer(userId)
        }).catch { emit(emptyList()) }
}
