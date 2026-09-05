package com.jepretaja.app.ui.screens.reviews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.ReviewModel
import com.jepretaja.app.data.repository.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WriteReviewViewModel @Inject constructor(
    private val repository: ReviewRepository,
) : ViewModel() {

    private val _existing = MutableStateFlow<ReviewModel?>(null)
    val existing: StateFlow<ReviewModel?> = _existing.asStateFlow()

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun clearMessage() { _message.value = null }

    fun load(bookingId: String) {
        viewModelScope.launch {
            _existing.value = runCatching { repository.forBooking(bookingId) }.getOrNull()
        }
    }

    fun submit(
        bookingId: String,
        customerId: String,
        customerName: String,
        creatorId: String,
        rating: Int,
        text: String,
    ) {
        viewModelScope.launch {
            _submitting.value = true
            val hasil = runCatching {
                repository.submitReview(bookingId, customerId, customerName, creatorId, rating, text)
            }
            _submitting.value = false
            _message.value = if (hasil.isSuccess) {
                "Ulasan terkirim. Terima kasih!"
            } else {
                hasil.exceptionOrNull()?.message ?: "Gagal mengirim ulasan."
            }
        }
    }
}
