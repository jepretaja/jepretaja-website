package com.jepretaja.app.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jepretaja.app.core.util.CloudFunctions
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.core.util.asFlow
import com.jepretaja.app.data.model.WalletModel
import com.jepretaja.app.data.model.WalletTransactionModel
import com.jepretaja.app.data.model.WithdrawalModel
import com.jepretaja.app.data.remote.ApiClient
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ledger wallet append-only. Saldo tidak pernah ditulis dari APK: koleksi
 * `wallets`, `wallet_transactions`, dan `withdrawals` semuanya `allow write:
 * if false` di firestore.rules.
 */
/** Aturan penarikan yang berlaku saat ini. */
data class PengaturanPenarikan(
    val minimum: Long = MIN_PENARIKAN_DEFAULT,
    val biayaAdmin: Long = 0L,
    val estimasiHariKerja: Int = 2,
)

/** Disamakan dengan MIN_WITHDRAWAL_DEFAULT di api/_lib/actions/appWallet.js. */
const val MIN_PENARIKAN_DEFAULT = 100_000L

@Singleton
class WalletRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val api: ApiClient,
) {

    fun streamWallet(creatorId: String) = db.collection(FirestorePaths.WALLETS).document(creatorId).asFlow<WalletModel>()

    /**
     * Riwayat ledger.
     *
     * [limit] bisa dinaikkan pemanggil yang butuh jangkauan waktu lebih panjang
     * — grafik pendapatan enam bulan di dashboard, misalnya, tidak selalu muat
     * dalam 50 transaksi terakhir pada creator yang ramai.
     */
    fun streamLedger(creatorId: String, limit: Long = 50): Flow<List<WalletTransactionModel>> =
        db.collection(FirestorePaths.WALLET_TRANSACTIONS)
            .whereEqualTo("creatorId", creatorId)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).asFlow()

    /**
     * Mengajukan penarikan saldo.
     *
     * Lewat server (/api/app), BUKAN tulis langsung ke Firestore. Versi lama
     * memanggil `withdrawals.add(...)` dari APK dan selalu berakhir
     * PERMISSION_DENIED karena aturan menutup koleksi itu — wajar, karena
     * kalau klien boleh menulis sendiri, nominal dan status "requested" bisa
     * dikarang. Server yang memeriksa saldo tersedia dan batas minimum.
     *
     * `creatorId` tidak dikirim: identitas diambil server dari ID token.
     */
    suspend fun requestWithdrawal(
        creatorId: String,
        amount: Long,
        bankCode: String,
        bankAccountNumber: String,
        bankAccountName: String,
    ): String {
        val data = api.call(
            CloudFunctions.REQUEST_WITHDRAWAL,
            mapOf(
                "amount" to amount,
                "bankCode" to bankCode,
                "bankAccountNumber" to bankAccountNumber,
                "bankAccountName" to bankAccountName,
            )
        )
        return data["withdrawalId"] as? String ?: ""
    }

    /**
     * Aturan penarikan yang berlaku, dibaca dari `settings/general`.
     *
     * Angka minimum SUDAH dipakai server (appWallet.js) untuk menolak pengajuan
     * yang terlalu kecil, tapi aplikasi tidak pernah membacanya — jadi creator
     * baru tahu batasnya setelah pengajuannya ditolak. Nilai default di sini
     * sengaja disamakan dengan MIN_WITHDRAWAL_DEFAULT di server supaya keduanya
     * tidak pernah berselisih ketika dokumen settings belum diisi.
     *
     * Biaya admin dan estimasi belum ada di server; keduanya dibaca kalau
     * fieldnya ada dan jatuh ke nilai aman kalau tidak. Menuliskan angka biaya
     * yang tidak benar-benar dipotong akan membuat saldo yang diterima creator
     * tidak cocok dengan yang dijanjikan layar ini.
     */
    fun streamPengaturanPenarikan(): Flow<PengaturanPenarikan> = callbackFlow {
        val registration = db.collection(FirestorePaths.SETTINGS).document("general")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(PengaturanPenarikan())
                    return@addSnapshotListener
                }
                trySend(
                    PengaturanPenarikan(
                        minimum = snapshot?.getLong("minWithdrawal") ?: MIN_PENARIKAN_DEFAULT,
                        biayaAdmin = snapshot?.getLong("withdrawalFee") ?: 0L,
                        estimasiHariKerja = snapshot?.getLong("withdrawalEtaDays")?.toInt() ?: 2,
                    )
                )
            }
        awaitClose { registration.remove() }
    }

    fun streamWithdrawals(creatorId: String): Flow<List<WithdrawalModel>> =
        db.collection(FirestorePaths.WITHDRAWALS)
            .whereEqualTo("creatorId", creatorId)
            .orderBy("createdAt", Query.Direction.DESCENDING).asFlow()
}
