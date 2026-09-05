package com.jepretaja.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.local.AppPreferences
import com.jepretaja.app.data.model.UserModel
import com.jepretaja.app.data.repository.AuthRepository
import com.jepretaja.app.data.repository.CreatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val loading: Boolean = true,
    val isLoggedIn: Boolean = false,
    val uid: String? = null,
    val emailVerified: Boolean = true,
    val profile: UserModel? = null,
) {
    val isCreator get() = profile?.role == "creator"
    val isCustomer get() = profile?.role == "customer"
}

/** State autentikasi global — setara AuthState (Provider) di versi Flutter.
 * Dikonsumsi lewat hiltViewModel() (di-scope ke Activity lewat NavGraph)
 * supaya satu instance dipakai di seluruh app. */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferences: AppPreferences,
    private val creatorRepository: CreatorRepository,
) : ViewModel() {

    /**
     * Menandai creator ini sedang aktif, untuk titik hijau di profil publiknya.
     *
     * Dibatasi sekali per 5 menit lewat penanda lokal. Kehadiran bukan hal yang
     * layak dibayar dengan satu tulisan Firestore setiap kali layar dibuka,
     * dan selisih beberapa menit tidak mengubah arti "sedang online".
     *
     * Hanya untuk akun creator: profil konsumen tidak punya halaman publik yang
     * menampilkan kehadiran, jadi menulisnya hanya menambah biaya tanpa guna.
     */
    fun tandaiKehadiran() {
        val state = uiState.value
        val uid = state.uid ?: return
        if (!state.isCreator) return

        val sekarang = System.currentTimeMillis()
        if (sekarang - preferences.lastPresenceWrite < 5 * 60 * 1000) return
        preferences.lastPresenceWrite = sekarang

        viewModelScope.launch { runCatching { creatorRepository.touchPresence(uid) } }
    }

    /** Penanda yang menentukan layar pembuka — dibaca Splash. */
    val onboardingSeen: Boolean get() = preferences.onboardingSeen
    val guestMode: Boolean get() = preferences.guestMode

    fun markOnboardingSeen() { preferences.onboardingSeen = true }
    fun enterGuestMode() { preferences.guestMode = true }

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        // Dibaca LANGSUNG sebelum mulai mendengarkan aliran status.
        //
        // FirebaseAuth memulihkan sesi tersimpan dari disk saat diinisialisasi,
        // tapi listener status bisa memancarkan null lebih dulu sebelum sesi itu
        // selesai dipulihkan. Splash hanya menavigasi SEKALI — begitu ia melihat
        // "belum login", pengguna dilempar ke Onboarding dan emisi berikutnya
        // yang berisi user asli tidak lagi mengubah apa pun. Itulah sebabnya
        // pengguna yang sudah login kadang tetap disambut layar perkenalan
        // seolah aplikasi baru dipasang.
        authRepository.currentUser?.let { user ->
            _uiState.value = AuthUiState(
                loading = true, isLoggedIn = true, uid = user.uid,
                emailVerified = user.isEmailVerified,
            )
        }

        viewModelScope.launch {
            authRepository.authStateFlow()
                // Tanpa catch di sini, kegagalan apa pun pada aliran status auth
                // akan lolos keluar dari coroutine dan MEMATIKAN APLIKASI.
                .catch { _uiState.value = AuthUiState(loading = false, isLoggedIn = false) }
                .collect { user ->
                    if (user == null) {
                        _uiState.value = AuthUiState(loading = false, isLoggedIn = false)
                    } else {
                        // Blok ini berjalan tepat pada saat login/registrasi berhasil.
                        // Sebelumnya pemanggilan getCurrentUserProfile() TIDAK dibungkus
                        // try/catch: begitu pembacaan Firestore gagal — misalnya
                        // PERMISSION_DENIED karena firestore.rules belum di-deploy, atau
                        // koneksi putus — exception-nya tidak tertangkap dan aplikasi
                        // langsung tertutup persis setelah tombol Masuk/Daftar ditekan.
                        //
                        // Sekarang kegagalan diperlakukan sebagai "profil belum termuat":
                        // sesi tetap dianggap login (pengguna memang sudah terautentikasi
                        // di Firebase Auth), hanya detail profilnya kosong.
                        val profile = runCatching { authRepository.getCurrentUserProfile() }.getOrNull()
                        _uiState.value = AuthUiState(
                            loading = false, isLoggedIn = true, uid = user.uid,
                            emailVerified = user.isEmailVerified, profile = profile,
                        )
                    }
                }
        }
    }

    suspend fun refreshProfile() {
        val profile = runCatching { authRepository.getCurrentUserProfile() }.getOrNull()
        _uiState.value = _uiState.value.copy(profile = profile)
    }

    /** Simpan perubahan profil sendiri, lalu segarkan state supaya seluruh
     * layar (header profil, nama di chat, dst) langsung ikut berubah. */
    fun updateProfile(name: String, phone: String?, photoUrl: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { authRepository.updateMyProfile(name, phone, photoUrl) }
            if (result.isSuccess) refreshProfile()
            onResult(result.exceptionOrNull()?.message)
        }
    }

    /**
     * Hapus akun sendiri.
     *
     * Firebase menolak penghapusan akun yang sesi loginnya sudah lama
     * ("requires recent login"), dan pesan mentahnya tidak berarti apa-apa bagi
     * pengguna — jadi diterjemahkan menjadi instruksi yang bisa ditindaklanjuti.
     */
    /**
     * Hasil percobaan hapus akun.
     *
     * [perluMasukUlang] dipisahkan dari pesannya karena layar perlu BERTINDAK
     * berbeda, bukan sekadar menulis kalimat berbeda: kegagalan jenis ini punya
     * satu jalan keluar yang pasti (keluar lalu masuk lagi), dan menyuruh
     * pengguna mencari sendiri tombol keluar di daftar setelan setelah ia baru
     * saja mengetik kata konfirmasi adalah jalan buntu yang tidak perlu.
     */
    data class HasilHapusAkun(
        val pesan: String?,
        val perluMasukUlang: Boolean = false,
    )

    fun deleteAccount(onResult: (HasilHapusAkun) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { authRepository.deleteAccount() }
            val error = result.exceptionOrNull()
            onResult(
                when {
                    error == null -> HasilHapusAkun(null)
                    error.message?.contains("recent login", ignoreCase = true) == true -> HasilHapusAkun(
                        pesan = "Demi keamanan, akun hanya bisa dihapus tepat setelah login.",
                        perluMasukUlang = true,
                    )
                    else -> HasilHapusAkun(error.message ?: "Gagal menghapus akun.")
                }
            )
        }
    }

    /** Kirim ulang email verifikasi ke alamat yang dipakai mendaftar. */
    fun resendVerificationEmail(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val hasil = runCatching { authRepository.sendEmailVerification() }
            onResult(
                when {
                    hasil.isSuccess -> "Email verifikasi dikirim. Cek kotak masuk dan folder spam."
                    // Firebase membatasi frekuensi pengiriman; pesan mentahnya
                    // tidak menjelaskan bahwa ini cuma perlu ditunggu.
                    hasil.exceptionOrNull()?.message?.contains("too-many-requests", true) == true ->
                        "Terlalu sering meminta. Tunggu beberapa menit lalu coba lagi."
                    else -> hasil.exceptionOrNull()?.message ?: "Gagal mengirim email verifikasi."
                }
            )
        }
    }

    /**
     * Dipanggil setelah pengguna menekan tautan di emailnya. Tanpa langkah ini
     * status tersimpan di cache perangkat dan tidak pernah berubah sendiri.
     */
    fun refreshEmailVerified(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val hasil = runCatching { authRepository.refreshEmailVerified() }
            val terverifikasi = hasil.getOrDefault(false)
            if (terverifikasi) {
                _uiState.value = _uiState.value.copy(emailVerified = true)
            }
            onResult(
                terverifikasi,
                if (terverifikasi) "Email kamu sudah terverifikasi."
                else "Belum terverifikasi. Buka email lalu tekan tautannya, kemudian coba lagi.",
            )
        }
    }

    fun logout() {
        // Sesi tamu ikut dibersihkan: setelah keluar, pengguna harus kembali ke
        // halaman pilih-akses, bukan diam-diam masuk lagi sebagai tamu.
        preferences.clearGuestMode()
        authRepository.logout()
    }
}
