package com.jepretaja.app.ui.screens.creatordashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.BookingStatus
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.WorkingHours
import com.jepretaja.app.data.repository.AvailabilityRepository
import com.jepretaja.app.data.repository.BookingRepository
import com.jepretaja.app.data.repository.CreatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class CreatorAvailabilityViewModel @Inject constructor(
    private val repository: AvailabilityRepository,
    private val bookingRepository: BookingRepository,
    private val creatorRepository: CreatorRepository,
) : ViewModel() {

    private val _pesan = MutableStateFlow<String?>(null)
    val pesan: StateFlow<String?> = _pesan.asStateFlow()
    fun pesanDibaca() { _pesan.value = null }

    fun streamBlockedDates(creatorId: String): Flow<List<LocalDate>> =
        repository.streamBlockedDates(creatorId).catch { emit(emptyList()) }

    /**
     * Tanggal yang sudah punya booking berjalan.
     *
     * Kalender lama hanya mengenal dua keadaan — diblokir atau tidak — padahal
     * yang paling menentukan justru keadaan ketiga: tanggal yang SUDAH terjual.
     * Tanpa itu creator tidak bisa melihat kapan ia sebenarnya sudah terikat,
     * dan server akan menolak upayanya menutup tanggal itu dengan pesan yang
     * datang belakangan.
     */
    fun tanggalTerbooking(creatorId: String): Flow<Set<LocalDate>> =
        bookingRepository.streamCreatorBookings(creatorId)
            .map { daftar ->
                daftar.filter { it.status in AKTIF }
                    .mapNotNull { b -> b.date?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate() }
                    .toSet()
            }
            .catch { emit(emptySet()) }

    fun creator(creatorId: String): Flow<CreatorModel?> =
        creatorRepository.streamCreator(creatorId).catch { emit(null) }

    fun blockDate(date: LocalDate) {
        viewModelScope.launch {
            runCatching { repository.blockDate(isoDari(date)) }
                // Server menolak menutup tanggal yang sudah ada booking aktif.
                // Alasannya perlu sampai ke layar — sebelumnya kegagalan ini
                // ditelan `runCatching` tanpa jejak, dan tanggal yang ditekan
                // creator sekadar tidak berubah warna.
                .onFailure { _pesan.value = it.message?.takeIf { p -> p.isNotBlank() } ?: "Tanggal gagal ditutup." }
        }
    }

    fun unblockDate(date: LocalDate) {
        viewModelScope.launch {
            runCatching { repository.unblockDate(isoDari(date)) }
                .onFailure { _pesan.value = "Tanggal gagal dibuka." }
        }
    }

    fun simpanJamKerja(creatorId: String, jam: WorkingHours) {
        viewModelScope.launch {
            runCatching { creatorRepository.updateJamKerja(creatorId, jam) }
                .onSuccess { _pesan.value = "Jam kerja disimpan." }
                .onFailure { _pesan.value = "Jam kerja gagal disimpan." }
        }
    }

    private fun isoDari(date: LocalDate): String =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    private companion object {
        val AKTIF = setOf(
            BookingStatus.PENDING_PAYMENT,
            BookingStatus.PAID,
            BookingStatus.CONFIRMED,
            BookingStatus.UPCOMING,
            BookingStatus.IN_PROGRESS,
        )
    }
}
