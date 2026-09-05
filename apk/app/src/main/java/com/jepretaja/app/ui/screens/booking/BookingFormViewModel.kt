package com.jepretaja.app.ui.screens.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.data.model.PackageAddOnModel
import com.jepretaja.app.data.model.PackageModel
import com.jepretaja.app.data.repository.AvailabilityRepository
import com.jepretaja.app.data.repository.BookingRepository
import com.jepretaja.app.data.repository.CreatorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject

/** Bidang yang punya aturan validasi sendiri. */
enum class BidangForm { TANGGAL, JAM, LOKASI }

/** Empat langkah pengisian. Urutannya dipakai indikator langkah di layar. */
enum class LangkahBooking(val nomor: Int, val judul: String) {
    LAYANAN(1, "Layanan"),
    JADWAL(2, "Jadwal"),
    DETAIL(3, "Detail"),
    KONFIRMASI(4, "Konfirmasi"),
}

data class BookingFormState(
    val pkg: PackageModel? = null,
    val pkgLoading: Boolean = true,
    val pkgError: String? = null,
    val date: LocalDate? = null,
    val time: String? = null,
    val location: String = "",
    val note: String = "",
    val travelFee: String = "0",
    val voucherCode: String = "",
    val voucherError: String? = null,
    val selectedAddOnIds: Set<String> = emptySet(),
    val availableAddOns: List<PackageAddOnModel> = emptyList(),
    /** Tanggal yang ditutup creator lewat Ketersediaan. */
    val blockedDates: Set<LocalDate> = emptySet(),
    /** Tanggal yang sudah terpakai booking pelanggan lain. */
    val bookedDates: Set<LocalDate> = emptySet(),
    val priceBreakdown: Map<String, Any?>? = null,
    val previewLoading: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val langkah: LangkahBooking = LangkahBooking.LAYANAN,
    /**
     * Bidang yang sudah pernah disentuh pengguna.
     *
     * Pesan galat hanya tampil untuk bidang yang ada di sini. Tanpa penjagaan
     * itu, form yang baru dibuka akan langsung merah di semua kolom — memarahi
     * orang karena belum sempat mengisi apa pun, dan membuat warna merah
     * kehilangan artinya begitu ia benar-benar dibutuhkan.
     */
    val disentuh: Set<BidangForm> = emptySet(),
) {
    /**
     * Semua tanggal yang tidak bisa dipilih, dari dua sebab yang digabung.
     *
     * Kalender hanya perlu tahu "boleh atau tidak"; alasannya baru dijelaskan
     * saat pengguna benar-benar menekan tanggal yang tertutup.
     */
    val unavailableDates: Set<LocalDate> get() = blockedDates + bookedDates

    /** Alasan sebuah tanggal tertutup, untuk dijelaskan ke pengguna. */
    fun alasanTertutup(date: LocalDate): String? = when (date) {
        in bookedDates -> "Tanggal itu sudah dipesan orang lain. Pilih tanggal lain, ya."
        in blockedDates -> "Creator menutup tanggal itu. Pilih tanggal lain, ya."
        else -> null
    }

    /* ---- Validasi per bidang -------------------------------------------
     *
     * Aturannya hidup di sini, bukan di dalam submit(). Sebelumnya seluruh
     * pemeriksaan baru berjalan saat tombol kirim ditekan dan hasilnya muncul
     * sebagai satu baris merah di dasar halaman — jauh dari kolom yang
     * bermasalah, dan hanya satu pesan sekalipun ada tiga kolom yang kurang.
     * Sebagai nilai turunan, aturan yang sama sekarang bisa dipakai tiga hal
     * sekaligus: pesan di bawah kolom, keadaan indikator langkah, dan
     * penjagaan tombol Lanjut.
     */

    val galatTanggal: String? get() = when {
        date == null -> "Pilih tanggal pemotretan."
        date.isBefore(LocalDate.now()) -> "Tanggal itu sudah lewat."
        else -> alasanTertutup(date)
    }

    val galatJam: String? get() = if (time.isNullOrBlank()) "Pilih jam pemotretan." else null

    val galatLokasi: String? get() = when {
        location.isBlank() -> "Isi lokasi pemotretan."
        // Alamat sependek "rumah" atau "jkt" tidak cukup untuk dituju siapa
        // pun. Ditolak sekarang, bukan setelah creator kebingungan di hari H.
        location.trim().length < 8 -> "Tulis alamatnya lebih lengkap, minimal nama jalan atau gedung."
        else -> null
    }

    /** Galat yang BOLEH ditampilkan: hanya untuk bidang yang sudah disentuh. */
    fun galatTampil(bidang: BidangForm): String? = when (bidang) {
        BidangForm.TANGGAL -> galatTanggal
        BidangForm.JAM -> galatJam
        BidangForm.LOKASI -> galatLokasi
    }?.takeIf { bidang in disentuh }

    /** Bidang yang divalidasi pada satu langkah. */
    fun bidangLangkah(l: LangkahBooking): Set<BidangForm> = when (l) {
        LangkahBooking.LAYANAN -> emptySet()
        LangkahBooking.JADWAL -> setOf(BidangForm.TANGGAL, BidangForm.JAM)
        LangkahBooking.DETAIL -> setOf(BidangForm.LOKASI)
        LangkahBooking.KONFIRMASI -> setOf(BidangForm.TANGGAL, BidangForm.JAM, BidangForm.LOKASI)
    }

    /** Galat pertama pada satu langkah, atau null kalau langkah itu beres. */
    fun galatLangkah(l: LangkahBooking): String? = bidangLangkah(l).firstNotNullOfOrNull { bidang ->
        when (bidang) {
            BidangForm.TANGGAL -> galatTanggal
            BidangForm.JAM -> galatJam
            BidangForm.LOKASI -> galatLokasi
        }
    }

    fun langkahBeres(l: LangkahBooking): Boolean = galatLangkah(l) == null
}

@HiltViewModel
class BookingFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val db: FirebaseFirestore,
    private val bookingRepository: BookingRepository,
    private val creatorRepository: CreatorRepository,
    private val availabilityRepository: AvailabilityRepository,
) : ViewModel() {
    val packageId: String = checkNotNull(savedStateHandle["packageId"])

    private val _state = MutableStateFlow(BookingFormState())
    val state: StateFlow<BookingFormState> = _state.asStateFlow()

    init { loadPackage() }

    private fun loadPackage() {
        viewModelScope.launch {
            _state.value = _state.value.copy(pkgLoading = true, pkgError = null)
            try {
                val snap = db.collection(FirestorePaths.PACKAGES).document(packageId).get().await()
                val pkg = snap.toObject(PackageModel::class.java)
                if (pkg == null) {
                    _state.value = _state.value.copy(pkgLoading = false, pkgError = "Paket tidak ditemukan")
                    return@launch
                }
                _state.value = _state.value.copy(pkg = pkg, pkgLoading = false)
                initForCreator(pkg.creatorId)
                refreshPreview()
            } catch (e: Exception) {
                _state.value = _state.value.copy(pkgLoading = false, pkgError = e.message ?: "Gagal memuat paket.")
            }
        }
    }

    private fun initForCreator(creatorId: String) {
        viewModelScope.launch {
            availabilityRepository.streamBlockedDates(creatorId).catch { emit(emptyList()) }.collect { dates ->
                _state.value = _state.value.copy(blockedDates = dates.toSet())
            }
        }
        viewModelScope.launch {
            // Gagal membaca (mis. aturan Firestore menutup booking orang lain)
            // tidak boleh mengosongkan form: kalender tetap jalan, hanya tanpa
            // lapisan pencegahan bentrok ini, dan server tetap menolak di ujung.
            bookingRepository.streamBookedDates(creatorId).catch { emit(emptySet()) }.collect { dates ->
                _state.value = _state.value.copy(bookedDates = dates)
            }
        }
        viewModelScope.launch {
            creatorRepository.streamAddOns(packageId).catch { emit(emptyList()) }.collect { addOns ->
                _state.value = _state.value.copy(availableAddOns = addOns)
            }
        }
    }

    /**
     * Tanggal yang tertutup ditolak DENGAN alasan.
     *
     * Sebelumnya layar memanggil `if (picked !in blockedDates) setDate(picked)`:
     * memilih tanggal yang tertutup tidak menghasilkan apa pun — tidak ada
     * perubahan, tidak ada pesan — dan dari sisi pengguna form booking terlihat
     * rusak, bukan sedang menolak.
     */
    fun setDate(date: LocalDate) {
        val s = _state.value.sentuh(BidangForm.TANGGAL)
        val alasan = s.alasanTertutup(date)
        if (alasan != null) {
            _state.value = s.copy(error = alasan)
            return
        }
        if (date.isBefore(LocalDate.now())) {
            _state.value = s.copy(error = "Tanggal itu sudah lewat.")
            return
        }
        // Jam yang sudah terlanjur dipilih ikut diperiksa ulang: pindah dari
        // besok ke hari ini bisa membuat jam yang tadinya sah jadi jam lampau.
        val jamBatal = s.time != null && date == LocalDate.now() && sudahLewat(s.time)
        _state.value = s.copy(
            date = date,
            time = if (jamBatal) null else s.time,
            error = if (jamBatal) "Jam yang kamu pilih sudah lewat hari ini. Pilih jam lagi." else null,
        )
    }

    fun setTime(time: String) {
        val s = _state.value.sentuh(BidangForm.JAM)
        if (s.date == LocalDate.now() && sudahLewat(time)) {
            _state.value = s.copy(error = "Jam itu sudah lewat hari ini.")
            return
        }
        _state.value = s.copy(time = time, error = null)
    }

    private fun BookingFormState.sentuh(vararg bidang: BidangForm) =
        copy(disentuh = disentuh + bidang)

    /* ---- Perpindahan langkah ------------------------------------------ */

    /**
     * Maju satu langkah, kalau langkah sekarang sudah beres.
     *
     * Kalau belum, seluruh bidang langkah itu ditandai "disentuh" sehingga
     * pesan galatnya muncul serentak di bawah kolomnya masing-masing. Ini
     * sengaja berbeda dari perilaku lama, yang menampilkan satu pesan saja di
     * dasar halaman meski ada tiga kolom yang kurang — pengguna memperbaiki
     * satu, menekan tombol lagi, lalu diberi tahu kekurangan berikutnya.
     */
    fun lanjut() {
        val s = _state.value
        val galat = s.galatLangkah(s.langkah)
        if (galat != null) {
            _state.value = s.copy(disentuh = s.disentuh + s.bidangLangkah(s.langkah), error = null)
            return
        }
        val berikut = LangkahBooking.entries.getOrNull(s.langkah.ordinal + 1) ?: return
        _state.value = s.copy(langkah = berikut, error = null)
        if (berikut == LangkahBooking.KONFIRMASI) refreshPreview()
    }

    fun mundur() {
        val s = _state.value
        val sebelum = LangkahBooking.entries.getOrNull(s.langkah.ordinal - 1) ?: return
        _state.value = s.copy(langkah = sebelum, error = null)
    }

    /**
     * Lompat langsung ke satu langkah lewat indikator di atas layar.
     *
     * Mundur selalu boleh. Maju hanya boleh kalau semua langkah yang dilewati
     * sudah beres — indikator yang bisa melompati langkah yang belum diisi
     * akan mengantar pengguna ke halaman konfirmasi berisi tanggal kosong.
     */
    fun keLangkah(tujuan: LangkahBooking) {
        val s = _state.value
        if (tujuan.ordinal <= s.langkah.ordinal) {
            _state.value = s.copy(langkah = tujuan, error = null)
            return
        }
        val terhalang = LangkahBooking.entries
            .filter { it.ordinal < tujuan.ordinal }
            .firstOrNull { !s.langkahBeres(it) }
        if (terhalang != null) {
            _state.value = s.copy(
                langkah = terhalang,
                disentuh = s.disentuh + s.bidangLangkah(terhalang),
                error = null,
            )
            return
        }
        _state.value = s.copy(langkah = tujuan, error = null)
        if (tujuan == LangkahBooking.KONFIRMASI) refreshPreview()
    }

    /** True bila "HH:mm" sudah terlewat pada hari ini. */
    private fun sudahLewat(time: String): Boolean = runCatching {
        java.time.LocalTime.parse(time).isBefore(java.time.LocalTime.now())
    }.getOrDefault(false)
    // Lokasi ditandai tersentuh sejak ketukan pertama, jadi alamat yang terlalu
    // pendek langsung diberi tahu sambil diketik — bukan setelah pengguna
    // menekan tombol di ujung dan dilempar balik ke atas.
    fun setLocation(v: String) {
        _state.value = _state.value.copy(location = v, disentuh = _state.value.disentuh + BidangForm.LOKASI)
    }
    fun setNote(v: String) { _state.value = _state.value.copy(note = v) }
    /** Angka saja — "Rp 50.000" yang diketik apa adanya sebelumnya jatuh ke
     * `toLongOrNull() ?: 0` dan biaya perjalanannya hilang tanpa pemberitahuan. */
    fun setTravelFee(v: String) {
        _state.value = _state.value.copy(travelFee = v.filter { it.isDigit() }.take(10))
        refreshPreview()
    }
    fun setVoucherCode(v: String) { _state.value = _state.value.copy(voucherCode = v) }

    fun toggleAddOn(id: String) {
        val current = _state.value.selectedAddOnIds
        _state.value = _state.value.copy(selectedAddOnIds = if (id in current) current - id else current + id)
        refreshPreview()
    }

    fun refreshPreview(applyVoucher: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(previewLoading = true)
            try {
                val s = _state.value
                val result = bookingRepository.previewPrice(
                    packageId = packageId,
                    addOnIds = s.selectedAddOnIds.toList(),
                    travelFee = s.travelFee.toLongOrNull() ?: 0,
                    voucherCode = s.voucherCode.ifBlank { null },
                )
                @Suppress("UNCHECKED_CAST")
                val breakdown = result["breakdown"] as? Map<String, Any?>
                val voucherApplied = breakdown?.get("voucherCode")
                _state.value = _state.value.copy(
                    priceBreakdown = result,
                    previewLoading = false,
                    voucherError = if (applyVoucher && s.voucherCode.isNotBlank() && voucherApplied == null) "Kode voucher tidak valid atau sudah tidak berlaku" else null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(previewLoading = false, error = e.message ?: "Gagal menghitung harga.")
            }
        }
    }

    fun submit(onSuccess: (String) -> Unit) {
        val s = _state.value
        // Aturan yang sama dengan yang menggerakkan pesan di bawah tiap kolom,
        // jadi tombol kirim tidak bisa punya pendapat sendiri tentang apa yang
        // sah. Kalau ada yang kurang, pengguna dikembalikan ke LANGKAH-nya —
        // bukan sekadar diberi pesan di halaman yang kolomnya tidak terlihat.
        val langkahBermasalah = LangkahBooking.entries.firstOrNull { !s.langkahBeres(it) }
        if (langkahBermasalah != null || s.date == null || s.time == null) {
            _state.value = s.copy(
                langkah = langkahBermasalah ?: s.langkah,
                disentuh = s.disentuh + s.bidangLangkah(langkahBermasalah ?: s.langkah),
                error = langkahBermasalah?.let { s.galatLangkah(it) },
            )
            return
        }
        // Diperiksa lagi tepat sebelum kirim: kalender bisa berubah selama form
        // ini terbuka — orang lain mungkin baru saja memesan tanggal yang sama.
        s.alasanTertutup(s.date)?.let {
            _state.value = s.copy(date = null, error = it)
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(submitting = true, error = null)
            try {
                val result = bookingRepository.createBooking(
                    packageId = packageId,
                    date = s.date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toString(),
                    time = s.time,
                    location = s.location.trim(),
                    note = s.note.ifBlank { null },
                    addOnIds = s.selectedAddOnIds.toList(),
                    travelFee = s.travelFee.toLongOrNull() ?: 0,
                    voucherCode = s.voucherCode.ifBlank { null },
                )
                // `as String` sebelumnya: respons tanpa bookingId melempar
                // ClassCastException yang muncul ke pengguna sebagai pesan
                // internal Kotlin, bukan keterangan yang bisa ditindaklanjuti.
                val bookingId = result["bookingId"] as? String
                if (bookingId.isNullOrBlank()) {
                    _state.value = _state.value.copy(
                        submitting = false,
                        error = "Server tidak mengembalikan nomor booking. Coba lagi sebentar lagi.",
                    )
                    return@launch
                }
                _state.value = _state.value.copy(submitting = false)
                onSuccess(bookingId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(submitting = false, error = e.message ?: "Booking gagal dibuat.")
            }
        }
    }

    fun bersihkanError() { _state.value = _state.value.copy(error = null) }
}
