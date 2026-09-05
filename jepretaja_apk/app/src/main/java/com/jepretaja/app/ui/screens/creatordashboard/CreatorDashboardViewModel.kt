package com.jepretaja.app.ui.screens.creatordashboard

import androidx.lifecycle.ViewModel
import com.jepretaja.app.data.model.BookingModel
import com.jepretaja.app.data.model.BookingStatus
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.model.WalletModel
import com.jepretaja.app.data.model.WalletTransactionModel
import com.jepretaja.app.data.repository.BookingRepository
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.data.repository.ExploreRepository
import com.jepretaja.app.data.repository.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

/** Satu batang pada grafik pendapatan. */
data class BulanPendapatan(val label: String, val nilai: Long)

@HiltViewModel
class CreatorDashboardViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val exploreRepository: ExploreRepository,
    private val bookingRepository: BookingRepository,
    private val creatorRepository: CreatorRepository,
) : ViewModel() {

    fun wallet(creatorId: String): Flow<WalletModel?> =
        walletRepository.streamWallet(creatorId).catch { emit(null) }

    fun creator(creatorId: String): Flow<CreatorModel?> =
        creatorRepository.streamCreator(creatorId).catch { emit(null) }

    /** Karya sendiri — dipakai menghitung ringkasan performa di Creator Studio. */
    fun myPosts(creatorId: String): Flow<List<ExplorePostModel>> =
        exploreRepository.streamByCreator(creatorId).catch { emit(emptyList()) }

    fun bookings(creatorId: String): Flow<List<BookingModel>> =
        bookingRepository.streamCreatorBookings(creatorId).catch { emit(emptyList()) }

    /**
     * Pendapatan per bulan untuk grafik.
     *
     * Sumbernya ledger wallet, bukan nilai booking. Dua angka itu berbeda dan
     * yang kedua menyesatkan: total booking adalah yang dibayar PELANGGAN,
     * sedangkan yang masuk ke creator sudah dipotong biaya layanan. Grafik yang
     * memakai total booking akan menjanjikan pendapatan yang tidak pernah ia
     * terima.
     *
     * Hanya transaksi bertipe `credit` yang dihitung; penarikan dan potongan
     * bukan pendapatan, hanya perpindahan uang yang sudah pernah dicatat.
     */
    fun pendapatanBulanan(creatorId: String, jumlahBulan: Int = 6): Flow<List<BulanPendapatan>> =
        walletRepository.streamLedger(creatorId, limit = 300)
            .map { ledger -> susunBulanan(ledger, jumlahBulan) }
            .catch { emit(susunBulanan(emptyList(), jumlahBulan)) }

    private fun susunBulanan(
        ledger: List<WalletTransactionModel>,
        jumlahBulan: Int,
    ): List<BulanPendapatan> {
        val kunciKe = mutableMapOf<String, Long>()
        ledger.filter { it.type == "credit" && it.status == "success" }.forEach { tx ->
            val waktu = tx.createdAt?.toDate() ?: return@forEach
            val kal = Calendar.getInstance().apply { time = waktu }
            val kunci = "${kal.get(Calendar.YEAR)}-${kal.get(Calendar.MONTH)}"
            kunciKe[kunci] = (kunciKe[kunci] ?: 0L) + tx.amount
        }

        // Bulan tanpa pemasukan tetap digambar sebagai batang nol. Melompatinya
        // akan membuat dua bulan yang berjauhan terlihat bersebelahan, dan
        // grafik yang sumbu waktunya bolong berbohong tentang tren.
        val hasil = mutableListOf<BulanPendapatan>()
        val kal = Calendar.getInstance()
        kal.add(Calendar.MONTH, -(jumlahBulan - 1))
        repeat(jumlahBulan) {
            val kunci = "${kal.get(Calendar.YEAR)}-${kal.get(Calendar.MONTH)}"
            hasil += BulanPendapatan(
                label = NAMA_BULAN[kal.get(Calendar.MONTH)],
                nilai = kunciKe[kunci] ?: 0L,
            )
            kal.add(Calendar.MONTH, 1)
        }
        return hasil
    }

    companion object {
        private val NAMA_BULAN = listOf(
            "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
            "Jul", "Agu", "Sep", "Okt", "Nov", "Des",
        )

        /**
         * Booking yang menunggu tindakan creator.
         *
         * `paid` berarti uang pelanggan sudah masuk tapi creator belum
         * mengonfirmasi — inilah satu-satunya keadaan di mana pelanggan sedang
         * menunggu jawaban, dan karena itu angka yang paling pantas ditaruh
         * paling depan di dashboard.
         */
        val BARU = setOf(BookingStatus.PAID)

        /** Sudah dikonfirmasi dan belum selesai — pekerjaan yang berjalan. */
        val AKTIF = setOf(
            BookingStatus.CONFIRMED,
            BookingStatus.UPCOMING,
            BookingStatus.IN_PROGRESS,
        )
    }
}
