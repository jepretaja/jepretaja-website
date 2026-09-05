package com.jepretaja.app.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.jepretaja.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Aksi autentikasi one-shot (login/register/google/reset) — terpisah dari
 * AuthViewModel yang menyimpan state sesi berjalan, supaya tidak saling
 * ganggu lifecycle-nya. */
@HiltViewModel
class AuthActionsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    fun login(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.login(email, password)
                onSuccess()
            } catch (e: Exception) {
                onError("Login gagal. Periksa email/password Anda.")
            }
        }
    }

    fun registerCustomer(name: String, email: String, password: String, phone: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.registerCustomer(name, email, password, phone)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Registrasi gagal. Coba lagi.")
            }
        }
    }

    fun registerCreator(name: String, email: String, password: String, city: String, phone: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.registerCreator(name, email, password, city, phone)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Registrasi gagal. Coba lagi.")
            }
        }
    }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.signInWithGoogleIdToken(idToken)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Google Sign-In gagal.")
            }
        }
    }

    /**
     * Mengirim tautan pembuatan password baru ke email.
     *
     * Versi lama memakai `runCatching { ... }` lalu memanggil onDone() apa pun
     * hasilnya, dan satu-satunya pemanggil mengirim lambda kosong. Artinya:
     * berhasil atau gagal, layar tidak berubah sedikit pun — tombol "Lupa
     * password?" terlihat mati total padahal kadang emailnya benar-benar
     * terkirim. Sekarang hasilnya dilaporkan balik ke UI.
     */
    fun resetPassword(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                authRepository.resetPassword(email)
                onSuccess()
            } catch (e: Exception) {
                onError(pesanAuth(e))
            }
        }
    }

    /** Menerjemahkan kegagalan Firebase Auth jadi kalimat yang bisa ditindaklanjuti. */
    private fun pesanAuth(e: Throwable): String = when (e) {
        is FirebaseAuthInvalidCredentialsException -> "Format email tidak valid."
        is FirebaseAuthInvalidUserException -> "Email ini belum terdaftar di JepretAja."
        is FirebaseNetworkException -> "Tidak ada koneksi internet. Sambungkan dulu lalu coba lagi."
        is FirebaseTooManyRequestsException -> "Terlalu banyak percobaan. Tunggu beberapa menit lalu coba lagi."
        else -> e.message ?: "Gagal mengirim email pemulihan. Coba lagi."
    }
}
