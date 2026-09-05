package com.jepretaja.app.ui.screens.creatordashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.WalletModel
import com.jepretaja.app.data.model.WalletTransactionModel
import com.jepretaja.app.data.model.WithdrawalModel
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.data.repository.PengaturanPenarikan
import com.jepretaja.app.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreatorWalletViewModel @Inject constructor(
    private val repository: WalletRepository,
    private val creatorRepository: CreatorRepository,
) : ViewModel() {
    fun streamWallet(creatorId: String): Flow<WalletModel?> = repository.streamWallet(creatorId).catch { emit(null) }

    /** Rekening tersimpan — form penarikan mengisi sendiri dari sini. */
    fun streamCreator(creatorId: String): Flow<CreatorModel?> =
        creatorRepository.streamCreator(creatorId).catch { emit(null) }
    fun streamLedger(creatorId: String): Flow<List<WalletTransactionModel>> =
        repository.streamLedger(creatorId, limit = 200).catch { emit(emptyList()) }

    /** Minimum, biaya admin, dan estimasi pencairan yang berlaku. */
    fun pengaturanPenarikan(): Flow<PengaturanPenarikan> =
        repository.streamPengaturanPenarikan().catch { emit(PengaturanPenarikan()) }
    fun streamWithdrawals(creatorId: String): Flow<List<WithdrawalModel>> = repository.streamWithdrawals(creatorId).catch { emit(emptyList()) }

    private val _submitting = MutableStateFlow(false)
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun requestWithdrawal(creatorId: String, amount: Long, bankCode: String, accountNumber: String, accountName: String) {
        viewModelScope.launch {
            _submitting.value = true; _error.value = null; _success.value = false
            try {
                repository.requestWithdrawal(creatorId, amount, bankCode, accountNumber, accountName)
                _success.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal mengajukan withdrawal."
            } finally {
                _submitting.value = false
            }
        }
    }

    fun resetSuccess() { _success.value = false }
}
