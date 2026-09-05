package com.jepretaja.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class WalletModel(
    @DocumentId val creatorId: String = "",
    val pendingBalance: Long = 0,
    val availableBalance: Long = 0,
    val totalEarnings: Long = 0,
    val withdrawn: Long = 0,
)

data class WalletTransactionModel(
    @DocumentId val ledgerId: String = "",
    val creatorId: String = "",
    val referenceId: String? = null,
    val type: String = "credit", // credit|fee|refund|withdrawal|adjustment
    val amount: Long = 0,
    val balanceBefore: Long? = null,
    val balanceAfter: Long? = null,
    val status: String = "success",
    val note: String? = null,
    val createdAt: Timestamp? = null,
)

data class WithdrawalModel(
    @DocumentId val withdrawalId: String = "",
    val creatorId: String = "",
    val amount: Long = 0,
    val bankCode: String? = null,
    val bankAccountNumber: String? = null,
    val bankAccountName: String? = null,
    val bankReference: String? = null,
    val failureReason: String? = null,
    val status: String = "requested", // requested|processing|success|failed|cancelled
    val createdAt: Timestamp? = null,
)
