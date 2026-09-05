package com.jepretaja.app.ui.screens.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.core.util.SlipPembayaran
import com.jepretaja.app.data.model.BookingModel
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.components.InfoRow
import com.jepretaja.app.ui.components.KartuSlipPembayaran
import com.jepretaja.app.ui.components.PremiumCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val LOKAL_ID = Locale("in", "ID")
private val PadTepi = 20.dp

/**
 * Pembayaran lewat TRANSFER MANUAL.
 *
 * Yang berubah di versi ini:
 *
 * 1. **Lima keadaan digambar berbeda**, bukan dua. Sebelumnya layar ini hanya
 *    tahu "instruksi sudah diminta atau belum" plus satu perkecualian untuk
 *    batas waktu. Pembayaran yang sudah disetujui admin dan pembayaran yang
 *    DITOLAK admin tampil persis sama dengan yang belum dibayar sama sekali —
 *    layar terus meminta orang mentransfer uang yang sudah ia kirim.
 * 2. **Status dipantau, bukan dibaca sekali.** Verifikasi terjadi di panel web
 *    admin, jadi perubahannya datang dari luar aplikasi. Tanpa listener,
 *    pengguna yang menunggu di layar ini tidak akan pernah melihat
 *    pembayarannya disetujui.
 * 3. **Rincian harga digambar utuh**, bukan cuma satu angka total. Orang yang
 *    diminta mentransfer sejumlah uang berhak melihat angka itu tersusun dari
 *    apa — terutama karena nominalnya ditambah kode unik yang, tanpa
 *    penjelasan, terlihat seperti selisih yang tidak dijelaskan.
 * 4. **Slip punya pratinjau.** Bukti yang akan dikirim ke orang lain semestinya
 *    bisa dilihat dulu sebelum dikirim.
 */
@Composable
fun PaymentScreen(
    bookingId: String,
    onBack: () -> Unit,
    onTransferDone: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel(),
) {
    LaunchedEffect(bookingId) { viewModel.loadBooking(bookingId) }
    val booking by viewModel.booking.collectAsState()
    val payment by viewModel.payment.collectAsState()
    val processing by viewModel.processing.collectAsState()
    val error by viewModel.error.collectAsState()
    val info by viewModel.transfer.collectAsState()
    val sudahDeklarasi by viewModel.sudahDeklarasi.collectAsState()
    val memuat by viewModel.memuat.collectAsState()
    val errorMuat by viewModel.errorMuat.collectAsState()

    val clipboard = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Hitung mundur batas transfer. Tanggal mati yang diam ("bayar sebelum
    // 12:30") tidak memberi tahu apa pun tentang berapa waktu yang tersisa,
    // dan kode unik yang kedaluwarsa membuat transfer tidak bisa dicocokkan —
    // itu justru kegagalan pembayaran yang paling sering terjadi.
    var sisaMillis by remember(info?.paymentId) { mutableLongStateOf(0L) }
    var jamBerjalan by remember(info?.paymentId) { mutableStateOf(false) }
    LaunchedEffect(info?.expiresAt) {
        val batas = info?.expiresAt ?: 0L
        if (batas <= 0L) { sisaMillis = 0L; jamBerjalan = false; return@LaunchedEffect }
        jamBerjalan = true
        while (true) {
            sisaMillis = (batas - System.currentTimeMillis()).coerceAtLeast(0L)
            if (sisaMillis == 0L) break
            delay(1000)
        }
    }
    val lewatBatas = jamBerjalan && sisaMillis == 0L

    val status = statusBayar(
        booking = booking,
        payment = payment,
        sudahLewatBatas = lewatBatas,
        sudahDeklarasi = sudahDeklarasi,
    )

    fun salin(teks: String, pesan: String) {
        clipboard.setText(AnnotatedString(teks))
        scope.launch { snackbarHostState.showSnackbar(pesan) }
    }

    Scaffold(
        containerColor = AppColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Pembayaran") },
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
    ) { padding ->
        if (memuat && booking == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
            return@Scaffold
        }
        errorMuat?.let { pesan ->
            Box(Modifier.padding(padding).fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pesan, color = AppColors.Danger, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.loadBooking(bookingId) }) { Text("Coba Lagi") }
                }
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(PadTepi)) {

                KartuStatus(
                    status = status,
                    jumlahTransfer = info?.transferAmount ?: booking?.total ?: 0L,
                    kodeUnik = info?.uniqueCode,
                    sisaMillis = sisaMillis,
                    batasMillis = info?.expiresAt ?: 0L,
                    mulaiMillis = payment?.createdAt?.toDate()?.time,
                    alasanDitolak = payment?.rejectedReason,
                    onSalinNominal = {
                        info?.let { salin(it.transferAmount.toString(), "Nominal transfer disalin") }
                    },
                )

                Spacer(Modifier.height(14.dp))

                booking?.let { b ->
                    RingkasanPesanan(b)
                    Spacer(Modifier.height(14.dp))
                    RincianHarga(
                        booking = b,
                        kodeUnik = info?.uniqueCode,
                        jumlahTransfer = info?.transferAmount,
                    )
                    Spacer(Modifier.height(14.dp))
                }

                // Rekening hanya masuk akal selama uangnya memang belum masuk.
                // Menampilkannya pada pembayaran yang sudah disetujui mengundang
                // orang mentransfer dua kali.
                if (status == StatusBayar.MENUNGGU || status == StatusBayar.DIPROSES) {
                    val instruksi = info
                    if (instruksi == null) {
                        KartuCatatan(
                            ikon = Icons.Default.Info,
                            warna = AppColors.Primary,
                            teks = "Pembayaran dilakukan lewat transfer bank. Tekan tombol di bawah untuk " +
                                "menampilkan nomor rekening, nominal, dan kode uniknya.",
                        )
                    } else {
                        KartuRekening(
                            bankName = instruksi.bankName,
                            nomor = instruksi.bankAccountNumber,
                            atasNama = instruksi.bankAccountName,
                            onSalin = { salin(instruksi.bankAccountNumber, "Nomor rekening disalin") },
                        )
                        Spacer(Modifier.height(14.dp))
                        KartuCatatan(
                            ikon = Icons.Default.Info,
                            warna = AppColors.Primary,
                            teks = instruksi.instruction.ifBlank {
                                "Transfer tepat sesuai nominal di atas agar pembayaran bisa dicocokkan."
                            },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }

                // Slip baru ada artinya setelah ada uang yang benar-benar masuk.
                if (status == StatusBayar.BERHASIL) {
                    booking?.let { b ->
                        KartuSlipPembayaran(
                            isi = SlipPembayaran.isiDari(
                                booking = b,
                                nomorPembayaran = payment?.paymentId,
                                waktuBayarMillis = (payment?.paidAt ?: payment?.createdAt)?.toDate()?.time,
                            ),
                            onPesan = { pesan -> scope.launch { snackbarHostState.showSnackbar(pesan) } },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }

                KartuCatatan(
                    ikon = Icons.Default.Lock,
                    warna = AppColors.Info,
                    teks = "Dana akan berstatus \"ditahan\" (held funds) sampai kondisi pelepasan dana " +
                        "terpenuhi sesuai kebijakan JepretAja.",
                )

                error?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
            }

            Column(Modifier.padding(horizontal = PadTepi, vertical = 12.dp)) {
                BigPrimaryButton(
                    text = when {
                        processing -> "Memproses..."
                        status == StatusBayar.BERHASIL -> "Lihat Detail Booking"
                        status == StatusBayar.GAGAL -> "Minta Instruksi Baru"
                        status == StatusBayar.KEDALUWARSA -> "Minta Instruksi Baru"
                        status == StatusBayar.DIPROSES -> "Lihat Status Booking"
                        info == null -> "Tampilkan Cara Bayar"
                        else -> "Saya Sudah Transfer"
                    },
                    onClick = {
                        when (status) {
                            StatusBayar.BERHASIL, StatusBayar.DIPROSES -> onTransferDone()
                            // Instruksi lama dibuang dulu, baru diminta ulang —
                            // menekan "sudah transfer" pada kode unik yang sudah
                            // kedaluwarsa hanya menghasilkan pembayaran yang
                            // tidak akan pernah bisa dicocokkan admin.
                            StatusBayar.GAGAL, StatusBayar.KEDALUWARSA -> {
                                viewModel.ulangiInstruksi()
                                viewModel.createPaymentOrder(bookingId)
                            }
                            StatusBayar.MENUNGGU -> {
                                if (info == null) {
                                    viewModel.createPaymentOrder(bookingId)
                                } else {
                                    viewModel.tandaiSudahTransfer()
                                    onTransferDone()
                                }
                            }
                        }
                    },
                    enabled = !processing,
                    loading = processing,
                )
                // Jalan mundur untuk yang salah tekan. Tanpa ini, satu ketukan
                // keliru mengunci layar pada "sedang diperiksa" sampai admin
                // menyentuhnya — padahal tidak ada apa pun yang dikirim.
                if (status == StatusBayar.DIPROSES) {
                    TextButton(
                        onClick = { viewModel.ulangiInstruksi() },
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) { Text("Saya belum transfer", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Kartu status
 * ----------------------------------------------------------------------- */

private data class TampilanStatus(
    val judul: String,
    val keterangan: String,
    val ikon: ImageVector,
    val warna: Color,
)

@Composable
private fun tampilanDari(status: StatusBayar, alasanDitolak: String?): TampilanStatus = when (status) {
    StatusBayar.MENUNGGU -> TampilanStatus(
        judul = "Menunggu pembayaran",
        keterangan = "Transfer tepat sejumlah nominal di bawah, lalu tekan \"Saya Sudah Transfer\".",
        ikon = Icons.Default.HourglassTop,
        warna = AppColors.Warning,
    )
    StatusBayar.DIPROSES -> TampilanStatus(
        judul = "Pembayaran diproses",
        keterangan = "Transfermu sedang dicek admin JepretAja. Biasanya selesai dalam beberapa jam kerja.",
        ikon = Icons.Default.Sync,
        warna = AppColors.Info,
    )
    StatusBayar.BERHASIL -> TampilanStatus(
        judul = "Pembayaran berhasil",
        keterangan = "Pembayaranmu sudah diverifikasi. Booking diteruskan ke creator.",
        ikon = Icons.Default.CheckCircle,
        warna = AppColors.Success,
    )
    StatusBayar.GAGAL -> TampilanStatus(
        judul = "Pembayaran gagal",
        // Alasan dari admin ditampilkan apa adanya kalau ada. "Gagal" tanpa
        // sebab tidak memberi tahu apa yang harus diperbaiki, dan orang yang
        // uangnya sudah keluar berhak tahu apa yang terjadi padanya.
        keterangan = alasanDitolak?.takeIf { it.isNotBlank() }
            ?: "Transfermu tidak bisa dicocokkan. Minta instruksi baru, atau hubungi bantuan bila dananya sudah terpotong.",
        ikon = Icons.Default.HighlightOff,
        warna = AppColors.Danger,
    )
    StatusBayar.KEDALUWARSA -> TampilanStatus(
        judul = "Batas waktu habis",
        keterangan = "Kode unik yang lama tidak bisa dipakai lagi. Minta instruksi baru sebelum transfer.",
        ikon = Icons.Default.TimerOff,
        warna = AppColors.Danger,
    )
}

@Composable
private fun KartuStatus(
    status: StatusBayar,
    jumlahTransfer: Long,
    kodeUnik: Long?,
    sisaMillis: Long,
    batasMillis: Long,
    mulaiMillis: Long?,
    alasanDitolak: String?,
    onSalinNominal: () -> Unit,
) {
    val t = tampilanDari(status, alasanDitolak)

    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(t.warna.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(t.ikon, contentDescription = null, tint = t.warna, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.judul, style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(t.keterangan, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }

        // Nominal hanya ditonjolkan selama ia masih harus ditransfer. Setelah
        // berhasil, angka besar yang sama terbaca seperti tagihan baru.
        if (status == StatusBayar.MENUNGGU || status == StatusBayar.DIPROSES) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = AppColors.Border)
            Spacer(Modifier.height(12.dp))
            Text("Transfer tepat sejumlah", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Formatters.currency(jumlahTransfer),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.W700,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                // Nominal justru angka yang PALING fatal kalau salah ketik —
                // beda satu rupiah membuat pembayaran tidak bisa dicocokkan.
                if (kodeUnik != null) {
                    IconButton(onClick = onSalinNominal) {
                        Icon(
                            Icons.Default.ContentCopy, contentDescription = "Salin nominal transfer",
                            tint = AppColors.Primary,
                        )
                    }
                }
            }
            kodeUnik?.let {
                Text(
                    "Termasuk kode unik $it. Jangan dibulatkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
        }

        if (status == StatusBayar.MENUNGGU && batasMillis > 0L) {
            Spacer(Modifier.height(14.dp))
            HitungMundur(sisaMillis = sisaMillis, batasMillis = batasMillis, mulaiMillis = mulaiMillis)
        }
    }
}

/**
 * Hitung mundur batas transfer.
 *
 * Angkanya diberi bilah kemajuan supaya "sisa 3 jam" bisa dinilai tanpa harus
 * mengingat jendelanya 24 jam. Warnanya berubah jadi merah di bawah satu jam:
 * pada titik itu keputusannya bukan lagi "nanti saja", melainkan "sekarang atau
 * minta kode baru".
 */
@Composable
private fun HitungMundur(sisaMillis: Long, batasMillis: Long, mulaiMillis: Long?) {
    val mendesak = sisaMillis in 1 until 60 * 60 * 1000L
    val warna = if (mendesak) AppColors.Danger else AppColors.Warning
    val total = mulaiMillis?.let { (batasMillis - it).coerceAtLeast(1L) }
    val rasio = total?.let { (sisaMillis.toFloat() / it).coerceIn(0f, 1f) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(warna.copy(alpha = 0.08f))
            .border(1.dp, warna.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.HourglassTop, contentDescription = null, tint = warna, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Sisa waktu transfer", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                Text(
                    "Bayar sebelum ${SimpleDateFormat("d MMM yyyy, HH:mm", LOKAL_ID).format(Date(batasMillis))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
            Text(
                hitungMundur(sisaMillis),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = warna,
            )
        }
        if (rasio != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { rasio },
                color = warna,
                trackColor = warna.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(percent = 50)),
            )
        }
    }
}

/* --------------------------------------------------------------------------
 * Ringkasan & rincian
 * ----------------------------------------------------------------------- */

@Composable
private fun RingkasanPesanan(b: BookingModel) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        Text("Pesananmu", style = MaterialTheme.typography.labelLarge, color = AppColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(
            b.packageName.ifBlank { "Paket pemotretan" },
            style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary,
        )
        Spacer(Modifier.height(4.dp))
        val tanggal = b.date?.toDate()?.let { SimpleDateFormat("EEEE, d MMMM yyyy", LOKAL_ID).format(it) }
        Text(
            listOfNotNull(tanggal, b.time.ifBlank { null }).joinToString(" • ").ifBlank { "Jadwal belum tercatat" },
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
        )
        if (b.location.isNotBlank()) {
            Text(b.location, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
    }
}

/**
 * Rincian harga.
 *
 * Sebelumnya layar ini hanya menampilkan satu angka besar. Orang yang diminta
 * mentransfer sejumlah uang berhak melihat angka itu tersusun dari apa —
 * terutama di sini, karena nominal yang ditransfer BUKAN total booking
 * melainkan total ditambah kode unik, dan selisih tiga digit yang tidak
 * dijelaskan adalah persis bentuk kejanggalan yang membuat orang membatalkan
 * pembayaran.
 */
@Composable
private fun RincianHarga(booking: BookingModel, kodeUnik: Long?, jumlahTransfer: Long?) {
    val pb = booking.priceBreakdown

    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ReceiptLong, contentDescription = null,
                tint = AppColors.Primary, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Rincian Harga", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
        }
        Spacer(Modifier.height(4.dp))

        if (pb == null) {
            InfoRow("Total booking", Formatters.currency(booking.total))
        } else {
            InfoRow("Harga paket", Formatters.currency(pb.packagePrice))
            if (pb.addOnsTotal > 0) InfoRow("Add-on", Formatters.currency(pb.addOnsTotal))
            if (pb.travelFee > 0) InfoRow("Biaya perjalanan", Formatters.currency(pb.travelFee))
            if (pb.discount > 0) {
                InfoRow(
                    pb.voucherCode?.takeIf { it.isNotBlank() }?.let { "Diskon ($it)" } ?: "Diskon",
                    "- ${Formatters.currency(pb.discount)}",
                    valueColor = AppColors.Success,
                )
            }
            InfoRow("Biaya layanan (${pb.platformFeePercent}%)", Formatters.currency(pb.platformFee))
            HorizontalDivider(color = AppColors.Border, modifier = Modifier.padding(vertical = 6.dp))
            InfoRow("Total booking", Formatters.currency(booking.total))
        }

        if (kodeUnik != null && jumlahTransfer != null) {
            InfoRow("Kode unik", "+ ${Formatters.currency(kodeUnik)}", valueColor = AppColors.Primary)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.PrimarySoft)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Jumlah yang ditransfer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    Formatters.currency(jumlahTransfer),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W700,
                    color = AppColors.Primary,
                )
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Potongan kecil
 * ----------------------------------------------------------------------- */

@Composable
private fun KartuRekening(
    bankName: String,
    nomor: String,
    atasNama: String,
    onSalin: () -> Unit,
) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AccountBalance, contentDescription = null,
                tint = AppColors.Primary, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Rekening Tujuan", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
        }
        Spacer(Modifier.height(10.dp))
        Text(bankName, style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                nomor,
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.TextPrimary,
                fontWeight = FontWeight.W700,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSalin) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Salin nomor rekening", tint = AppColors.Primary)
            }
        }
        Text("a.n. $atasNama", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
    }
}

@Composable
private fun KartuCatatan(ikon: ImageVector, warna: Color, teks: String) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(ikon, contentDescription = null, tint = warna, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(teks, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
    }
}

/** "01:59:03" atau "12:45" — satuan jam disembunyikan saat tidak diperlukan
 * supaya angka yang tersisa lebih cepat terbaca. */
private fun hitungMundur(millis: Long): String {
    val detikTotal = millis / 1000
    val jam = detikTotal / 3600
    val menit = (detikTotal % 3600) / 60
    val detik = detikTotal % 60
    return if (jam > 0) "%d:%02d:%02d".format(jam, menit, detik) else "%02d:%02d".format(menit, detik)
}
