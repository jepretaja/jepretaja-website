package com.jepretaja.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.local.AppPreferences
import com.jepretaja.app.data.model.BookingModel
import com.jepretaja.app.data.model.BookingStatus
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.repository.BookingRepository
import com.jepretaja.app.data.repository.ChatRepository
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.data.repository.ExploreRepository
import com.jepretaja.app.data.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Keadaan satu seksi di Home.
 *
 * Sebelumnya tiap seksi hanya berupa `List<T>?`, dengan `null` berarti "belum
 * dimuat". Yang tidak punya tempat di sana adalah **gagal**: setiap Flow
 * diakhiri `catch { emit(emptyList()) }`, jadi koneksi putus dan izin ditolak
 * sama-sama muncul di layar sebagai "Belum ada creator". Pengguna lalu
 * menyimpulkan aplikasinya kosong, bukan bahwa ada yang perlu dicoba ulang —
 * dan tidak diberi tombol untuk mencoba ulang sekalipun ia menduga.
 */
sealed interface SeksiHome<out T> {
    data object Memuat : SeksiHome<Nothing>
    // Sengaja invarian meski antarmukanya `out`: `copy()` bawaan data class
    // memakai T di posisi masuk, dan itu bentrok dengan `out`. Kovarian di
    // tingkat antarmuka sudah cukup — yang dibutuhkan hanyalah Memuat dan Gagal
    // bisa mengisi slot SeksiHome<List<...>> apa pun.
    data class Isi<T>(val data: T) : SeksiHome<T>
    data class Gagal(val pesan: String) : SeksiHome<Nothing>
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val creatorRepository: CreatorRepository,
    private val exploreRepository: ExploreRepository,
    private val bookingRepository: BookingRepository,
    private val chatRepository: ChatRepository,
    private val notificationRepository: NotificationRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    /**
     * Pemicu muat ulang.
     *
     * Setiap seksi berlangganan lewat `flatMapLatest` pada nilai ini, jadi
     * menaikkannya satu berarti seluruh listener Firestore dipasang ulang dari
     * awal. Ini yang membuat tombol "Coba lagi" di kartu galat benar-benar
     * mencoba lagi — tanpanya, Flow yang sudah telanjur berakhir karena
     * exception tidak akan pernah memancarkan apa pun lagi seumur layar.
     */
    private val pemicuMuatUlang = MutableStateFlow(0)

    fun muatUlang() { pemicuMuatUlang.value += 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    val nearbyCreators: StateFlow<SeksiHome<List<CreatorModel>>> = pemicuMuatUlang
        .flatMapLatest { creatorRepository.streamNearby() }
        .jadiSeksi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeksiHome.Memuat)

    @OptIn(ExperimentalCoroutinesApi::class)
    val popularCreators: StateFlow<SeksiHome<List<CreatorModel>>> = pemicuMuatUlang
        .flatMapLatest { creatorRepository.streamPopular() }
        .jadiSeksi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeksiHome.Memuat)

    /**
     * Rekomendasi — creator yang cocok dengan minat pengguna.
     *
     * Diambil dari jaring yang sama dengan "Creator Populer" (40 teratas menurut
     * rating) lalu **diperingkat ulang** menurut kategori yang dipilih pengguna
     * saat pertama masuk. Dua seksi yang isinya sama urutannya cuma berbeda
     * sedikit akan terbaca sebagai pengulangan, jadi bobot minat sengaja jauh
     * lebih besar daripada rating: yang membedakan Rekomendasi dari Populer
     * memang harus minatnya, bukan angka bintangnya.
     *
     * Bagi pengguna yang melewati pemilihan minat, peringkat jatuh ke
     * "siap dipesan" — terverifikasi, menerima booking, punya rekam ulasan —
     * yang tetap merupakan pertanyaan berbeda dari "siapa yang ratingnya
     * tertinggi".
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recommendedCreators: StateFlow<SeksiHome<List<CreatorModel>>> = pemicuMuatUlang
        .flatMapLatest { creatorRepository.streamPopular(limit = 40) }
        .map { daftar -> daftar.sortedByDescending { skorRekomendasi(it) }.take(12) }
        .jadiSeksi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeksiHome.Memuat)

    /** Benar kalau pengguna pernah memilih minat — dipakai untuk subjudul seksi. */
    val punyaMinat: Boolean get() = prefs.interests.isNotEmpty()

    private fun skorRekomendasi(c: CreatorModel): Double {
        val minat = prefs.interests
        var skor = 0.0
        if (minat.isNotEmpty()) skor += c.categories.count { it in minat } * 40.0
        if (c.verified) skor += 10
        if (c.acceptingBookings) skor += 8
        skor += c.rating * 2
        // Diredam akar dengan alasan yang sama seperti di pencarian: creator
        // berulasan 400 lebih terbukti daripada yang berulasan 40, tapi tidak
        // sepuluh kali lebih — tanpa peredaman satu-dua nama lama akan selalu
        // menempati puncak setiap seksi.
        skor += sqrt(c.reviewCount.toDouble())
        return skor
    }

    /** Karya terbaru untuk galeri di Home — isi yang paling menjual di aplikasi fotografi. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val latestWorks: StateFlow<SeksiHome<List<ExplorePostModel>>> = pemicuMuatUlang
        .flatMapLatest { exploreRepository.streamFeed() }
        .map { it.take(10) }
        .jadiSeksi()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeksiHome.Memuat)

    private val _userId = MutableStateFlow<String?>(null)
    fun setUserId(uid: String?) { _userId.value = uid }

    /**
     * Nama tampilan creator untuk header Home.
     *
     * Dibaca dari dokumen creator, bukan dari profil users, karena creator bisa
     * mengganti nama tampilannya di Creator Studio tanpa mengubah nama akun.
     * Null untuk akun konsumen — header lalu memakai nama profil biasa.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val creatorName: StateFlow<String?> = _userId
        .flatMapLatest { uid ->
            if (uid == null) flowOf<String?>(null)
            else flow { emit(runCatching { creatorRepository.getCreator(uid)?.displayName }.getOrNull()) }
        }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Angka untuk badge di tombol Chat dan Notifikasi pada header.
     *
     * Keduanya memakai snapshot listener, jadi badge bertambah begitu pesan
     * atau notifikasi baru masuk tanpa perlu membuka ulang layar, dan hilang
     * sendiri begitu isinya dibaca.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadChats: StateFlow<Int> = _userId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(0) else chatRepository.streamUnreadCount(uid)
        }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val unreadNotifications: StateFlow<Int> = _userId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(0) else notificationRepository.streamUnreadCount(uid)
        }
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Booking yang masih berjalan, untuk kartu pengingat di Home.
     *
     * Statusnya memakai kosakata BookingStatus yang sama dengan panel web,
     * dan yang sudah selesai/batal sengaja tidak ikut — kartu ini gunanya
     * mengingatkan yang masih perlu ditindaklanjuti, bukan jadi riwayat.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeBooking: StateFlow<BookingModel?> = _userId
        .flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            else bookingRepository.streamCustomerBookings(uid)
                .map { list -> list.firstOrNull { it.status in AKTIF } }
                .catch { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private companion object {
        val AKTIF = setOf(
            BookingStatus.PENDING_PAYMENT,
            BookingStatus.PAID,
            BookingStatus.CONFIRMED,
            BookingStatus.UPCOMING,
            BookingStatus.IN_PROGRESS,
            BookingStatus.COMPLETED,
        )
    }
}

private fun <T> Flow<T>.jadiSeksi(): Flow<SeksiHome<T>> =
    map<T, SeksiHome<T>> { SeksiHome.Isi(it) }
        .catch { emit(SeksiHome.Gagal(pesanRamah(it))) }

/**
 * Pesan galat yang bisa dibaca orang.
 *
 * Menampilkan `e.message` apa adanya berarti menaruh "PERMISSION_DENIED:
 * Missing or insufficient permissions" di layar pengguna — kalimat yang benar
 * secara teknis dan tidak berguna sama sekali bagi yang membacanya.
 */
private fun pesanRamah(e: Throwable): String {
    val mentah = e.message.orEmpty()
    return when {
        mentah.contains("PERMISSION_DENIED", ignoreCase = true) ->
            "Data ini sedang tidak bisa diakses."
        mentah.contains("UNAVAILABLE", ignoreCase = true) ||
            mentah.contains("network", ignoreCase = true) ||
            mentah.contains("host", ignoreCase = true) ->
            "Sambungan terputus. Periksa koneksi internetmu."
        else -> "Gagal memuat bagian ini."
    }
}
