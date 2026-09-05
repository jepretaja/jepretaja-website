package com.jepretaja.app.ui.screens.booking

// DatePickerDialog milik Android sengaja TIDAK diimpor: namanya bentrok dengan
// DatePickerDialog Material3 yang dipakai di bawah, dan yang bawaan Android
// tidak bisa menonaktifkan tanggal satuan — justru itu yang dibutuhkan di sini.
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.ui.components.LocationField
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SkeletonList
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar

private val PadTepi = 20.dp

/**
 * Form Booking, disusun ulang jadi empat langkah.
 *
 * **Kenapa berlangkah, bukan satu halaman panjang.** Versi sebelumnya menaruh
 * delapan urusan berbeda — paket, tanggal, jam, lokasi, catatan, add-on, biaya
 * perjalanan, voucher, rincian harga — dalam satu kolom yang digulir, dipisah
 * hanya oleh `Spacer` dan beberapa judul. Yang hilang di sana bukan keindahan
 * melainkan orientasi: pengguna tidak pernah tahu tinggal berapa lagi, dan
 * setiap kali menggulir ke bawah ia dihadapkan pada kolom baru yang tidak ia
 * duga. Empat langkah dengan indikator di atas menjawab satu pertanyaan yang
 * selalu dibawa orang ke formulir pembayaran: *masih berapa lama lagi?*
 *
 * **Validasi tampil di kolomnya masing-masing.** Dulu seluruh pemeriksaan baru
 * berjalan saat tombol kirim ditekan, dan hasilnya satu baris merah di dasar
 * halaman — jauh dari kolom yang bermasalah, dan hanya satu pesan sekalipun
 * tiga kolom kurang. Sekarang aturannya hidup di [BookingFormState] sebagai
 * nilai turunan, dipakai bersama oleh pesan di bawah kolom, warna indikator
 * langkah, dan penjagaan tombol Lanjut.
 *
 * Add-on ditaruh di langkah Layanan, bukan Detail: add-on mengubah isi jasa dan
 * harganya, jadi keputusannya satu tarikan napas dengan memilih paket.
 */
@Composable
fun BookingFormScreen(
    onBack: () -> Unit,
    onSubmitted: (String) -> Unit,
    viewModel: BookingFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var pilihTanggal by remember { mutableStateOf(false) }
    val gulir = rememberScrollState()

    // Setiap pindah langkah, halaman kembali ke atas. Tanpa ini, langkah baru
    // terbuka di posisi gulir langkah sebelumnya dan judulnya tidak terlihat.
    LaunchedEffect(state.langkah) { gulir.animateScrollTo(0) }

    fun showTimePicker() {
        val now = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hour, minute -> viewModel.setTime("%02d:%02d".format(hour, minute)) },
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true,
        ).show()
    }

    if (pilihTanggal) {
        KalenderBooking(
            terpilih = state.date,
            tidakTersedia = state.unavailableDates,
            onDismiss = { pilihTanggal = false },
            onPilih = { tanggal -> viewModel.setDate(tanggal); pilihTanggal = false },
        )
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Form Booking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    titleContentColor = AppColors.TextPrimary,
                    navigationIconContentColor = AppColors.TextPrimary,
                ),
            )
        },
        bottomBar = {
            if (state.pkg != null) {
                BilahLangkah(
                    state = state,
                    onMundur = viewModel::mundur,
                    onLanjut = viewModel::lanjut,
                    onKirim = { viewModel.submit(onSubmitted) },
                )
            }
        },
    ) { padding ->
        if (state.pkgLoading) {
            Box(Modifier.padding(padding).fillMaxSize().padding(PadTepi)) { SkeletonList(count = 5) }
            return@Scaffold
        }
        if (state.pkgError != null || state.pkg == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.pkgError ?: "Paket tidak ditemukan", color = AppColors.Danger)
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize()) {
            IndikatorLangkah(state = state, onPilih = viewModel::keLangkah)
            HorizontalDivider(color = AppColors.Border)

            Column(
                Modifier.weight(1f).verticalScroll(gulir).padding(vertical = 18.dp),
            ) {
                when (state.langkah) {
                    LangkahBooking.LAYANAN -> LangkahLayanan(state, viewModel::toggleAddOn)
                    LangkahBooking.JADWAL -> LangkahJadwal(
                        state = state,
                        onPilihTanggal = { pilihTanggal = true },
                        onPilihJam = { showTimePicker() },
                    )
                    LangkahBooking.DETAIL -> LangkahDetail(
                        state = state,
                        onLokasi = viewModel::setLocation,
                        onCatatan = viewModel::setNote,
                        onBiayaJalan = viewModel::setTravelFee,
                    )
                    LangkahBooking.KONFIRMASI -> LangkahKonfirmasi(
                        state = state,
                        onVoucher = viewModel::setVoucherCode,
                        onTerapkanVoucher = { viewModel.refreshPreview(applyVoucher = true) },
                        onUbahLangkah = viewModel::keLangkah,
                    )
                }

                // Galat tingkat layar (bentrok tanggal, kegagalan server) tetap
                // ada tempatnya sendiri — bedanya sekarang ia hanya menampung
                // hal-hal yang memang tidak melekat pada satu kolom.
                state.error?.let { pesan ->
                    Spacer(Modifier.height(16.dp))
                    PitaGalat(pesan, Modifier.padding(horizontal = PadTepi))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Indikator langkah
 * ----------------------------------------------------------------------- */

/**
 * 1 Layanan → 2 Jadwal → 3 Detail → 4 Konfirmasi.
 *
 * Langkah yang sudah beres diberi centang, yang sedang dikerjakan diberi warna
 * utama, sisanya kelabu. Langkah yang sudah lewat bisa diketuk untuk kembali;
 * melompat maju hanya boleh kalau semua langkah di antaranya sudah beres —
 * indikator yang membiarkan orang melompati langkah kosong akan mengantarnya ke
 * halaman konfirmasi berisi tanggal yang belum diisi.
 */
@Composable
private fun IndikatorLangkah(
    state: BookingFormState,
    onPilih: (LangkahBooking) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        LangkahBooking.entries.forEach { langkah ->
            val sekarang = state.langkah == langkah
            val lewat = langkah.ordinal < state.langkah.ordinal
            val beres = lewat && state.langkahBeres(langkah)
            // Merah hanya untuk langkah yang SUDAH dilewati tapi ternyata belum
            // beres. Langkah di depan belum sempat diisi, dan menandainya salah
            // berarti menyalahkan orang atas sesuatu yang belum dimintanya.
            val bermasalah = lewat && !state.langkahBeres(langkah)

            val warna = when {
                bermasalah -> AppColors.Danger
                sekarang -> AppColors.Primary
                beres -> AppColors.Success
                else -> AppColors.TextSecondary
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPilih(langkah) }
                    .padding(vertical = 2.dp),
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(
                            color = if (sekarang) warna else warna.copy(alpha = 0.14f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (beres) {
                        Icon(
                            Icons.Default.Check, contentDescription = null,
                            tint = warna, modifier = Modifier.size(16.dp),
                        )
                    } else if (bermasalah) {
                        Icon(
                            Icons.Default.ErrorOutline, contentDescription = null,
                            tint = warna, modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text(
                            "${langkah.nomor}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.W700,
                            color = if (sekarang) AppColors.OnPrimary else warna,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    langkah.judul,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (sekarang) AppColors.TextPrimary else AppColors.TextSecondary,
                    maxLines = 1,
                )
            }

            if (langkah.ordinal < LangkahBooking.entries.lastIndex) {
                Box(
                    Modifier
                        .padding(top = 13.dp)
                        .width(14.dp)
                        .height(2.dp)
                        .background(
                            if (langkah.ordinal < state.langkah.ordinal) AppColors.Primary else AppColors.Border
                        ),
                )
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Langkah 1 — Layanan
 * ----------------------------------------------------------------------- */

@Composable
private fun LangkahLayanan(state: BookingFormState, onToggleAddOn: (String) -> Unit) {
    val pkg = state.pkg ?: return

    KartuBagian(judul = "Data Layanan", ikon = Icons.Default.ReceiptLong) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pkg.name, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${pkg.duration} • ${pkg.personnel ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
            Text(
                Formatters.currency(pkg.price),
                color = AppColors.Primary,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        pkg.output?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text("Hasil: $it", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
    }

    Spacer(Modifier.height(14.dp))

    KartuBagian(
        judul = "Add-on",
        ikon = Icons.Default.LocalOffer,
        keterangan = if (state.availableAddOns.isEmpty()) null else "Opsional, harganya ditambahkan ke total.",
    ) {
        if (state.availableAddOns.isEmpty()) {
            Text(
                "Paket ini tidak punya add-on.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
            )
        } else {
            state.availableAddOns.forEachIndexed { index, addOn ->
                Row(
                    Modifier.fillMaxWidth().clickable { onToggleAddOn(addOn.addOnId) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = addOn.addOnId in state.selectedAddOnIds,
                        onCheckedChange = { onToggleAddOn(addOn.addOnId) },
                    )
                    Text(addOn.name, modifier = Modifier.weight(1f), color = AppColors.TextPrimary)
                    Text(
                        "+ ${Formatters.currency(addOn.price)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary,
                    )
                }
                if (index != state.availableAddOns.lastIndex) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Langkah 2 — Jadwal
 * ----------------------------------------------------------------------- */

@Composable
private fun LangkahJadwal(
    state: BookingFormState,
    onPilihTanggal: () -> Unit,
    onPilihJam: () -> Unit,
) {
    KartuBagian(
        judul = "Tanggal",
        ikon = Icons.Default.CalendarToday,
        keterangan = if (state.unavailableDates.isEmpty()) {
            null
        } else {
            "${state.unavailableDates.size} tanggal sudah terisi dan tampil kelabu di kalender."
        },
        galat = state.galatTampil(BidangForm.TANGGAL),
    ) {
        BarisPilih(
            nilai = state.date?.let { Formatters.date(it) },
            placeholder = "Pilih tanggal pemotretan",
            galat = state.galatTampil(BidangForm.TANGGAL) != null,
            onClick = onPilihTanggal,
        )
    }

    Spacer(Modifier.height(14.dp))

    KartuBagian(
        judul = "Jam",
        ikon = Icons.Default.AccessTime,
        keterangan = "Jam mulai pemotretan.",
        galat = state.galatTampil(BidangForm.JAM),
    ) {
        BarisPilih(
            nilai = state.time,
            placeholder = "Pilih jam mulai",
            galat = state.galatTampil(BidangForm.JAM) != null,
            onClick = onPilihJam,
        )
    }
}

/** Baris "kolom" yang isinya dipilih lewat dialog, bukan diketik. */
@Composable
private fun BarisPilih(
    nilai: String?,
    placeholder: String,
    galat: Boolean,
    onClick: () -> Unit,
) {
    val warnaTepi = if (galat) AppColors.Danger else AppColors.Border
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Background)
            .border(1.dp, warnaTepi, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            nilai ?: placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = if (nilai == null) AppColors.TextSecondary else AppColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.ChevronRight, contentDescription = null,
            tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp),
        )
    }
}

/* --------------------------------------------------------------------------
 * Langkah 3 — Detail
 * ----------------------------------------------------------------------- */

@Composable
private fun LangkahDetail(
    state: BookingFormState,
    onLokasi: (String) -> Unit,
    onCatatan: (String) -> Unit,
    onBiayaJalan: (String) -> Unit,
) {
    val galatLokasi = state.galatTampil(BidangForm.LOKASI)

    KartuBagian(judul = "Lokasi", ikon = Icons.Default.Place) {
        LocationField(
            value = state.location,
            onValueChange = onLokasi,
            isError = galatLokasi != null,
            errorText = galatLokasi,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(14.dp))

    KartuBagian(
        judul = "Catatan",
        ikon = Icons.Default.Notes,
        keterangan = "Konsep, jumlah orang, atau permintaan khusus. Opsional.",
    ) {
        OutlinedTextField(
            state.note,
            onCatatan,
            placeholder = { Text("Misal: outdoor, 2 keluarga, butuh 1 jam ekstra") },
            shape = MaterialTheme.shapes.medium,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(14.dp))

    KartuBagian(
        judul = "Biaya Perjalanan",
        ikon = Icons.Default.Route,
        keterangan = "Isi kalau lokasinya di luar kota creator. Kosongkan bila tidak ada.",
    ) {
        OutlinedTextField(
            state.travelFee,
            onBiayaJalan,
            prefix = { Text("Rp", color = AppColors.TextSecondary) },
            supportingText = {
                state.travelFee.toLongOrNull()?.takeIf { it > 0 }?.let {
                    Text(Formatters.currency(it), style = MaterialTheme.typography.bodySmall)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/* --------------------------------------------------------------------------
 * Langkah 4 — Konfirmasi
 * ----------------------------------------------------------------------- */

@Composable
private fun LangkahKonfirmasi(
    state: BookingFormState,
    onVoucher: (String) -> Unit,
    onTerapkanVoucher: () -> Unit,
    onUbahLangkah: (LangkahBooking) -> Unit,
) {
    // Ringkasan apa yang sudah diisi, dengan jalan pulang ke tiap langkah.
    // Halaman konfirmasi tanpa cara mengubah memaksa pengguna menekan tombol
    // kembali berulang kali dan menebak di langkah mana kolomnya berada.
    KartuBagian(judul = "Ringkasan Pesanan", ikon = Icons.Default.ReceiptLong) {
        BarisRingkas("Paket", state.pkg?.name ?: "-") { onUbahLangkah(LangkahBooking.LAYANAN) }
        if (state.selectedAddOnIds.isNotEmpty()) {
            val nama = state.availableAddOns
                .filter { it.addOnId in state.selectedAddOnIds }
                .joinToString(", ") { it.name }
            BarisRingkas("Add-on", nama) { onUbahLangkah(LangkahBooking.LAYANAN) }
        }
        BarisRingkas(
            "Jadwal",
            listOfNotNull(state.date?.let { Formatters.date(it) }, state.time).joinToString(", ").ifBlank { "-" },
        ) { onUbahLangkah(LangkahBooking.JADWAL) }
        BarisRingkas("Lokasi", state.location.ifBlank { "-" }) { onUbahLangkah(LangkahBooking.DETAIL) }
        if (state.note.isNotBlank()) {
            BarisRingkas("Catatan", state.note) { onUbahLangkah(LangkahBooking.DETAIL) }
        }
    }

    Spacer(Modifier.height(14.dp))

    KartuBagian(judul = "Voucher", ikon = Icons.Default.LocalOffer, keterangan = "Opsional.") {
        Row(verticalAlignment = Alignment.Top) {
            OutlinedTextField(
                state.voucherCode,
                onVoucher,
                placeholder = { Text("Kode voucher") },
                isError = state.voucherError != null,
                supportingText = state.voucherError?.let { msg -> { Text(msg) } },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = onTerapkanVoucher,
                enabled = state.voucherCode.isNotBlank() && !state.previewLoading,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.height(56.dp),
            ) { Text("Terapkan") }
        }
    }

    Spacer(Modifier.height(14.dp))

    // PriceBreakdownCard sudah berupa kartu. Membungkusnya lagi dengan
    // KartuBagian akan memberi kartu di dalam kartu — dua tepi dan dua bayangan
    // untuk satu isi.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = PadTepi, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.ReceiptLong, contentDescription = null,
            tint = AppColors.Primary, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text("Ringkasan Harga", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.padding(horizontal = PadTepi)) { PriceBreakdownCard(state) }
}

@Composable
private fun BarisRingkas(label: String, nilai: String, onUbah: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(76.dp),
        )
        Text(
            nilai,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUbah, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Text("Ubah", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/* --------------------------------------------------------------------------
 * Bilah bawah & potongan bersama
 * ----------------------------------------------------------------------- */

@Composable
private fun BilahLangkah(
    state: BookingFormState,
    onMundur: () -> Unit,
    onLanjut: () -> Unit,
    onKirim: () -> Unit,
) {
    val terakhir = state.langkah == LangkahBooking.KONFIRMASI
    val total = (state.priceBreakdown?.get("total") as? Number)?.toLong()

    Surface(color = AppColors.Surface, shadowElevation = 12.dp) {
        Column(Modifier.padding(horizontal = PadTepi, vertical = 12.dp)) {
            // Total ditempel di bilah aksi sejak awal, bukan hanya di langkah
            // terakhir: angka yang berubah begitu add-on dicentang adalah cara
            // paling langsung memberi tahu akibat dari pilihan barusan.
            if (total != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Total sementara",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Formatters.currency(total),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.W700,
                        color = AppColors.Primary,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(visible = state.langkah != LangkahBooking.LAYANAN) {
                    Row {
                        OutlinedButton(
                            onClick = onMundur,
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier.height(52.dp),
                        ) { Text("Kembali") }
                        Spacer(Modifier.width(10.dp))
                    }
                }
                Button(
                    onClick = if (terakhir) onKirim else onLanjut,
                    // Tombolnya TIDAK dimatikan saat ada kolom yang kurang.
                    // Tombol mati tidak pernah menjelaskan apa yang kurang;
                    // tombol hidup yang menandai kolomnya justru memberi tahu.
                    enabled = !state.submitting && !(terakhir && state.previewLoading),
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Primary,
                        contentColor = AppColors.OnPrimary,
                    ),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Text(
                        when {
                            state.submitting -> "Memproses..."
                            terakhir -> "Buat Booking"
                            else -> "Lanjut"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

/**
 * Pembungkus satu bagian form.
 *
 * Semua bagian memakai bentuk yang sama — ikon, judul, satu kalimat penjelas,
 * lalu isinya — supaya delapan urusan yang berbeda terbaca sebagai delapan
 * kotak yang setara, bukan sebagai tumpukan kolom yang batasnya harus ditebak.
 */
@Composable
private fun KartuBagian(
    judul: String,
    ikon: ImageVector,
    keterangan: String? = null,
    galat: String? = null,
    isi: @Composable ColumnScope.() -> Unit,
) {
    PremiumCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PadTepi),
        elevation = 3.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ikon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(judul, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
        }
        keterangan?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        Spacer(Modifier.height(12.dp))
        isi()
        galat?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.ErrorOutline, contentDescription = null,
                    tint = AppColors.Danger, modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Danger)
            }
        }
    }
}

@Composable
private fun PitaGalat(pesan: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Danger.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.ErrorOutline, contentDescription = null,
            tint = AppColors.Danger, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(pesan, style = MaterialTheme.typography.bodySmall, color = AppColors.Danger)
    }
}

/**
 * Kalender yang benar-benar MEMATIKAN tanggal yang tidak tersedia.
 *
 * Sebelumnya layar ini memakai DatePickerDialog bawaan Android, yang tidak bisa
 * menonaktifkan tanggal satuan: seluruh kalender terlihat bisa dipilih, dan
 * tanggal yang tertutup baru ditolak diam-diam setelah pengguna menekannya.
 * `SelectableDates` milik Material3 memindahkan aturan itu ke dalam kalender,
 * jadi tanggal yang sudah dipesan orang lain memang tampak kelabu dan tidak
 * bisa ditekan sama sekali.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KalenderBooking(
    terpilih: LocalDate?,
    tidakTersedia: Set<LocalDate>,
    onDismiss: () -> Unit,
    onPilih: (LocalDate) -> Unit,
) {
    val hariIni = remember { LocalDate.now() }
    val aturan = remember(tidakTersedia, hariIni) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                // utcTimeMillis selalu tengah malam UTC, jadi dikonversi lewat
                // UTC juga — memakai zona perangkat menggeser tanggalnya satu
                // hari untuk pengguna di timur Greenwich, termasuk Indonesia.
                val tanggal = runCatching {
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                }.getOrNull() ?: return false
                return !tanggal.isBefore(hariIni) && tanggal !in tidakTersedia
            }

            override fun isSelectableYear(year: Int): Boolean = year >= hariIni.year
        }
    }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = terpilih?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = aturan,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        val tanggal = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onPilih(tanggal)
                    } else {
                        onDismiss()
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("Pilih") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    ) {
        DatePicker(
            state = state,
            title = { Text("Tanggal Pemotretan", modifier = Modifier.padding(start = 24.dp, top = 20.dp)) },
            headline = {
                Text(
                    if (tidakTersedia.isEmpty()) "Pilih tanggal"
                    else "Tanggal kelabu sudah terisi",
                    modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
    }
}
