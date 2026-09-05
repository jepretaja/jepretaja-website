package com.jepretaja.app.ui.screens.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.BookingModel
import com.jepretaja.app.data.model.PaymentModel
import com.jepretaja.app.data.repository.BookingRepository
import com.jepretaja.app.data.repository.ChatRepository
import com.jepretaja.app.data.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookingDetailViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val chatRepository: ChatRepository,
    private val paymentRepository: PaymentRepository,
) : ViewModel() {

    /**
     * Pembayaran milik booking ini — dipakai slip untuk menyebut nomor dan
     * waktu pembayaran.
     *
     * Kalau aturan Firestore menolak atau dokumennya belum ada, alirannya
     * memancarkan null dan slip tetap bisa dibuat dari data booking saja: nomor
     * pembayaran hanya salah satu baris, bukan syarat.
     */
    private val _payment = MutableStateFlow<PaymentModel?>(null)
    val payment: StateFlow<PaymentModel?> = _payment.asStateFlow()

    private val _booking = MutableStateFlow<BookingModel?>(null)
    val booking: StateFlow<BookingModel?> = _booking.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _actionLoading = MutableStateFlow(false)
    val actionLoading: StateFlow<Boolean> = _actionLoading.asStateFlow()
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private var currentBookingId: String? = null

    /** Booking Confirmation butuh SEKALI baca (bukan live stream) — dipakai
     * bareng BookingDetailScreen yang butuh live update lewat streamBooking. */
    fun load(bookingId: String) {
        currentBookingId = bookingId
        viewModelScope.launch {
            _loading.value = true; _error.value = null
            try {
                _booking.value = bookingRepository.getBooking(bookingId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal memuat booking."
            } finally {
                _loading.value = false
            }
        }
    }

    fun observeLive(bookingId: String) {
        currentBookingId = bookingId
        viewModelScope.launch {
            _loading.value = true
            bookingRepository.streamBooking(bookingId)
                .catch { _error.value = it.message ?: "Gagal memuat booking."; _loading.value = false }
                .collect { _booking.value = it; _loading.value = false }
        }
        viewModelScope.launch {
            paymentRepository.streamPaymentForBooking(bookingId)
                .catch { emit(emptyList()) }
                .collect { _payment.value = it.firstOrNull() }
        }
    }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            _actionLoading.value = true; _actionMessage.value = null
            try {
                action()
                _actionMessage.value = "Berhasil diperbarui"
            } catch (e: Exception) {
                _actionMessage.value = "Gagal: ${e.message}"
            } finally {
                _actionLoading.value = false
            }
        }
    }

    fun startService(bookingId: String) = runAction { bookingRepository.startService(bookingId) }
    fun markServiceCompleted(bookingId: String) = runAction { bookingRepository.markServiceCompleted(bookingId) }
    fun confirmCompletion(bookingId: String) = runAction { bookingRepository.confirmCompletion(bookingId) }
    fun cancelBooking(bookingId: String) = runAction { bookingRepository.cancelBooking(bookingId) }
    fun openDispute(bookingId: String, reason: String) = runAction { bookingRepository.openDispute(bookingId, reason) }

    suspend fun openChat(bookingId: String, customerId: String, creatorId: String, packageName: String): String =
        chatRepository.getOrCreateChatForBooking(bookingId, customerId, creatorId, packageName)
}
