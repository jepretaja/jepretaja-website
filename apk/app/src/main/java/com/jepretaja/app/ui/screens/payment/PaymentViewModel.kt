package com.jepretaja.app.ui.screens.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jepretaja.app.data.local.AppPreferences
import com.jepretaja.app.data.model.BookingModel
import com.jepretaja.app.data.model.BookingStatus
import com.jepretaja.app.data.model.PaymentModel
import com.jepretaja.app.data.repository.BookingRepository
import com.jepretaja.app.data.repository.PaymentRepository
import com.jepretaja.app.services.AnalyticsService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Instruksi transfer manual yang diterbitkan server untuk satu booking. */
data class ManualTransferInfo(
    val paymentId: String,
    val amount: Long,
    val uniqueCode: Long,
    val transferAmount: Long,
    val bankName: String,
    val bankAccountNumber: String,
    val bankAccountName: String,
    val expiresAt: Long,
    val instruction: String,
)

/**
 * Lima keadaan pembayaran yang digambar layar.
 *
 * Sebelumnya layar ini hanya mengenal dua hal — instruksi sudah diminta atau
 * belum — dan satu-satunya keadaan lain yang bisa dibedakannya adalah "batas
 * waktu lewat". Pembayaran yang sudah disetujui admin, dan pembayaran yang
 * DITOLAK admin, tampil persis sama dengan pembayaran yang belum dibayar sama
 * sekali: layar terus meminta pengguna mentransfer uang yang sudah ia kirim,
 * atau yang justru ditolak.
 */
enum class StatusBayar {
    MENUNGGU,
    DIPROSES,
    BERHASIL,
    GAGAL,
    KEDALUWARSA,
}

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _booking = MutableStateFlow<BookingModel?>(null)
    val booking: StateFlow<BookingModel?> = _booking.asStateFlow()
    private val _processing = MutableStateFlow(false)
    val processing: StateFlow<Boolean> = _processing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Dokumen pembayaran dari Firestore — inilah sumber status yang sebenarnya. */
    private val _payment = MutableStateFlow<PaymentModel?>(null)
    val payment: StateFlow<PaymentModel?> = _payment.asStateFlow()

    /** Instruksi hasil panggilan API pada sesi ini (kalau ada). */
    private val _transferApi = MutableStateFlow<ManualTransferInfo?>(null)

    /**
     * Instruksi transfer yang dipakai layar.
     *
     * Diambil dari dokumen Firestore lebih dulu, panggilan API hanya jadi
     * cadangan. Ini yang membuat nomor rekening dan hitung mundurnya tetap ada
     * setelah layar ditutup dan dibuka lagi — sebelumnya keduanya hilang, dan
     * pengguna harus menekan "Tampilkan Cara Bayar" sekali lagi untuk melihat
     * instruksi yang sebetulnya tidak pernah berubah.
     */
    private val _transfer = MutableStateFlow<ManualTransferInfo?>(null)
    val transfer: StateFlow<ManualTransferInfo?> = _transfer.asStateFlow()

    private val _sudahDeklarasi = MutableStateFlow(false)
    val sudahDeklarasi: StateFlow<Boolean> = _sudahDeklarasi.asStateFlow()

    private val _memuat = MutableStateFlow(true)
    val memuat: StateFlow<Boolean> = _memuat.asStateFlow()

    /**
     * Kegagalan memuat booking dulunya ditelan `getOrNull()`, dan layar tetap
     * tampil dengan total "Rp 0" beserta tombol bayar yang aktif — angka yang
     * salah pada layar pembayaran jauh lebih berbahaya daripada pesan error.
     */
    private val _errorMuat = MutableStateFlow<String?>(null)
    val errorMuat: StateFlow<String?> = _errorMuat.asStateFlow()

    private var bookingIdSekarang: String? = null

    fun loadBooking(bookingId: String) {
        val pertamaKali = bookingIdSekarang != bookingId
        bookingIdSekarang = bookingId
        _sudahDeklarasi.value = bookingId in prefs.deklarasiTransfer

        viewModelScope.launch {
            _memuat.value = true; _errorMuat.value = null
            runCatching { bookingRepository.getBooking(bookingId) }
                .onSuccess { hasil ->
                    _booking.value = hasil
                    if (hasil == null) _errorMuat.value = "Booking tidak ditemukan."
                }
                .onFailure {
                    _errorMuat.value = it.message?.takeIf { p -> p.isNotBlank() }
                        ?: "Gagal memuat data booking. Periksa koneksimu."
                }
            _memuat.value = false
        }

        if (!pertamaKali) return

        // Booking dan pembayaran dipantau, bukan dibaca sekali.
        // Verifikasi dilakukan admin di panel web, jadi perubahan statusnya
        // datang dari luar aplikasi; tanpa listener, pengguna yang menunggu di
        // layar ini tidak akan pernah tahu pembayarannya sudah disetujui sampai
        // ia menutup dan membuka layarnya lagi.
        viewModelScope.launch {
            bookingRepository.streamBooking(bookingId).catch { }.collect { b ->
                if (b != null) _booking.value = b
            }
        }
        viewModelScope.launch {
            paymentRepository.streamPaymentForBooking(bookingId).catch { emit(emptyList()) }.collect { daftar ->
                val doc = daftar.firstOrNull()
                _payment.value = doc
                selaraskanInstruksi(doc)
            }
        }
    }

    /** Menyusun instruksi dari dokumen Firestore, kecuali API sudah memberi yang lebih baru. */
    private fun selaraskanInstruksi(doc: PaymentModel?) {
        val dariApi = _transferApi.value
        val dariDoc = doc?.takeIf { it.status == "awaiting_transfer" && it.bankAccountNumber.isNotBlank() }?.let {
            ManualTransferInfo(
                paymentId = it.paymentId,
                amount = it.amount,
                uniqueCode = it.uniqueCode,
                transferAmount = it.transferAmount,
                bankName = it.bankName,
                bankAccountNumber = it.bankAccountNumber,
                bankAccountName = it.bankAccountName,
                expiresAt = it.expiresAt?.toDate()?.time ?: 0L,
                instruction = "Transfer tepat sesuai nominal di atas — termasuk kode unik ${it.uniqueCode} — " +
                    "supaya pembayaranmu bisa dicocokkan.",
            )
        }
        _transfer.value = dariApi ?: dariDoc
    }

    /**
     * Status yang digambar layar, disusun dari tiga sumber.
     *
     * Urutan pemeriksaannya penting: keadaan yang sudah final (berhasil, gagal)
     * mendahului keadaan sementara. Pembayaran yang sudah disetujui tidak boleh
     * berubah jadi "kedaluwarsa" hanya karena batas waktu transfernya lewat
     * beberapa menit setelah admin menyetujuinya.
     */
    /**
     * Pengguna menyatakan sudah mentransfer.
     *
     * Tidak mengubah apa pun di server — memang tidak boleh: kalau aplikasi bisa
     * menandai pembayaran sebagai lunas, verifikasi admin kehilangan gunanya.
     * Yang berubah hanya kalimat yang dibaca pengguna saat ia kembali ke layar
     * ini sebelum admin sempat memeriksa.
     */
    fun tandaiSudahTransfer() {
        val id = bookingIdSekarang ?: return
        prefs.tandaiSudahTransfer(id)
        _sudahDeklarasi.value = true
    }

    /** Instruksi lama dibuang supaya tombol kembali meminta yang baru — dipakai
     * saat batas waktu transfer sudah lewat dan kode uniknya tidak berlaku. */
    fun ulangiInstruksi() {
        val id = bookingIdSekarang
        _transferApi.value = null
        _transfer.value = null
        _error.value = null
        if (id != null) {
            // Deklarasi lama ikut dibuang: yang kedaluwarsa adalah kode unik
            // yang dipakai transfer sebelumnya, jadi "sudah transfer" untuk kode
            // itu tidak lagi berarti apa-apa.
            prefs.lupakanDeklarasiTransfer(id)
            _sudahDeklarasi.value = false
        }
    }

    /**
     * Meminta instruksi transfer ke server.
     *
     * Server sengaja mengembalikan instruksi yang SAMA bila permintaan diulang
     * selama pembayaran masih menunggu, supaya kode uniknya tidak berubah dan
     * nominal transfernya tetap cocok.
     */
    fun createPaymentOrder(bookingId: String) {
        viewModelScope.launch {
            _processing.value = true; _error.value = null
            AnalyticsService.logPaymentStarted(bookingId)
            try {
                val hasil = paymentRepository.createPaymentOrder(bookingId)
                val info = ManualTransferInfo(
                    paymentId = hasil["paymentId"] as? String ?: "",
                    amount = angka(hasil["amount"]),
                    uniqueCode = angka(hasil["uniqueCode"]),
                    transferAmount = angka(hasil["transferAmount"]),
                    bankName = hasil["bankName"] as? String ?: "-",
                    bankAccountNumber = hasil["bankAccountNumber"] as? String ?: "-",
                    bankAccountName = hasil["bankAccountName"] as? String ?: "-",
                    expiresAt = angka(hasil["expiresAt"]),
                    instruction = hasil["instruction"] as? String ?: "",
                )
                _transferApi.value = info
                _transfer.value = info
            } catch (e: Exception) {
                _error.value = e.message ?: "Gagal membuat instruksi pembayaran. Coba lagi."
            } finally {
                _processing.value = false
            }
        }
    }

    private fun angka(v: Any?): Long = when (v) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }

}

/** Status booking yang berarti uangnya memang belum masuk. */
private val MENUNGGU_BAYAR = setOf(BookingStatus.DRAFT, BookingStatus.PENDING_PAYMENT)

/**
 * Status yang digambar layar, disusun dari tiga sumber.
 *
 * Fungsi murni, bukan pembacaan dari dalam ViewModel: layar sudah mengamati
 * booking, pembayaran, dan deklarasi sebagai state Compose, dan fungsi yang
 * diam-diam membaca `_state.value` di dalam ViewModel tidak akan memicu
 * penggambaran ulang saat salah satunya berubah.
 *
 * Urutan pemeriksaannya penting: keadaan final (berhasil, gagal) mendahului
 * keadaan sementara. Pembayaran yang sudah disetujui tidak boleh berubah jadi
 * "kedaluwarsa" hanya karena batas waktu transfernya lewat beberapa menit
 * setelah admin menyetujuinya.
 */
fun statusBayar(
    booking: BookingModel?,
    payment: PaymentModel?,
    sudahLewatBatas: Boolean,
    sudahDeklarasi: Boolean,
): StatusBayar = when {
    payment?.status == "paid" -> StatusBayar.BERHASIL
    booking != null && booking.status !in MENUNGGU_BAYAR -> StatusBayar.BERHASIL
    payment?.status == "rejected" -> StatusBayar.GAGAL
    sudahLewatBatas -> StatusBayar.KEDALUWARSA
    sudahDeklarasi -> StatusBayar.DIPROSES
    else -> StatusBayar.MENUNGGU
}
