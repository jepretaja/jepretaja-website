package com.jepretaja.app.ui.screens.creatordashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.data.model.WorkingHours
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.state.AuthViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LOKAL_ID = Locale("in", "ID")
private val BULAN = DateTimeFormatter.ofPattern("MMMM yyyy", LOKAL_ID)
private val HARI_PENDEK = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

/** Tiga keadaan satu tanggal. Urutannya = urutan prioritas saat digambar. */
private enum class KeadaanTanggal { TERBOOKING, DIBLOKIR, TERSEDIA, LAMPAU }

/**
 * Ketersediaan creator.
 *
 * **Apa yang berubah.** Layar ini sebelumnya bukan kalender sama sekali: ia
 * daftar tanggal yang diblokir, ditambah tombol + yang membuka DatePicker
 * bawaan Android. Bentuk itu hanya bisa menjawab satu pertanyaan — "tanggal apa
 * saja yang saya tutup" — sedangkan pertanyaan yang sebenarnya dibawa creator
 * ke sini adalah kebalikannya: *bulan depan saya kosong di tanggal berapa?*
 * Daftar tidak pernah bisa menjawab itu; hanya kisi tanggal yang bisa.
 *
 * Sekarang kalender bulanan dengan tiga warna berbeda: tersedia, diblokir, dan
 * **sudah dibooking** — keadaan ketiga yang sebelumnya tidak dikenali layar ini
 * sama sekali, padahal justru yang paling menentukan. Server memang menolak
 * menutup tanggal yang sudah ada booking aktif, tapi penolakan itu baru sampai
 * setelah creator menekannya.
 */
@Composable
fun CreatorAvailabilityScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorAvailabilityViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid

    var bulan by remember { mutableStateOf(YearMonth.now()) }
    var dialogJam by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val pesan by viewModel.pesan.collectAsState()
    LaunchedEffect(pesan) { pesan?.let { snackbarHostState.showSnackbar(it); viewModel.pesanDibaca() } }

    Scaffold(
        containerColor = AppColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { AppTopBar(title = "Ketersediaan", onBack = onBack) },
    ) { padding ->
        if (uid == null) return@Scaffold

        val diblokir by remember(uid) { viewModel.streamBlockedDates(uid) }.collectAsState(initial = emptyList())
        val terbooking by remember(uid) { viewModel.tanggalTerbooking(uid) }.collectAsState(initial = emptySet())
        val creator by remember(uid) { viewModel.creator(uid) }.collectAsState(initial = null)

        val setDiblokir = remember(diblokir) { diblokir.toSet() }
        val hariIni = remember { LocalDate.now() }

        fun keadaan(tanggal: LocalDate): KeadaanTanggal = when {
            tanggal in terbooking -> KeadaanTanggal.TERBOOKING
            tanggal.isBefore(hariIni) -> KeadaanTanggal.LAMPAU
            tanggal in setDiblokir -> KeadaanTanggal.DIBLOKIR
            else -> KeadaanTanggal.TERSEDIA
        }

        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            Spacer(Modifier.height(8.dp))

            // --- Kalender ---
            PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), elevation = 4.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { bulan = bulan.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Bulan sebelumnya")
                    }
                    Text(
                        bulan.atDay(1).format(BULAN),
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { bulan = bulan.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Bulan berikutnya")
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    HARI_PENDEK.forEach { h ->
                        Text(
                            h,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                // Kisi digambar sebagai Column berisi Row, bukan LazyVerticalGrid:
                // grid malas di dalam halaman yang menggulir butuh tinggi yang
                // dipatok, dan jumlah barisnya berubah antar-bulan (28 hari yang
                // jatuh pas di Senin hanya butuh empat baris, Maret bisa enam).
                val jumlahHari = bulan.lengthOfMonth()
                // ISO: Senin = 1. Kolom pertama juga Senin, jadi tidak perlu
                // pergeseran seperti pada kalender yang mulai dari Minggu.
                val geser = bulan.atDay(1).dayOfWeek.value - 1
                val sel: List<LocalDate?> = List(geser) { null } + (1..jumlahHari).map { bulan.atDay(it) }

                sel.chunked(7).forEach { baris ->
                    Row(Modifier.fillMaxWidth()) {
                        baris.forEach { tanggal ->
                            if (tanggal == null) {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                SelTanggal(
                                    tanggal = tanggal,
                                    keadaan = keadaan(tanggal),
                                    hariIni = tanggal == hariIni,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        when (keadaan(tanggal)) {
                                            KeadaanTanggal.TERSEDIA -> viewModel.blockDate(tanggal)
                                            KeadaanTanggal.DIBLOKIR -> viewModel.unblockDate(tanggal)
                                            else -> Unit
                                        }
                                    },
                                )
                            }
                        }
                        repeat(7 - baris.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = AppColors.Border)
                Spacer(Modifier.height(10.dp))

                // Legenda. Tiga warna tanpa keterangan hanyalah tiga warna —
                // dan salah satunya berarti "ketukanmu di sini tidak akan
                // melakukan apa pun", yang mustahil ditebak.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legenda(AppColors.Success, "Tersedia")
                    Legenda(AppColors.Danger, "Diblokir")
                    Legenda(AppColors.Primary, "Dibooking")
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Ketuk tanggal untuk menutup atau membukanya. Tanggal yang sudah dibooking tidak bisa ditutup.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary,
                )
            }

            // --- Ringkasan bulan ini ---
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val hariBulanIni = (1..bulan.lengthOfMonth()).map { bulan.atDay(it) }
                RingkasBulan(
                    "${hariBulanIni.count { keadaan(it) == KeadaanTanggal.TERSEDIA }}",
                    "Masih kosong", AppColors.Success, Modifier.weight(1f),
                )
                RingkasBulan(
                    "${hariBulanIni.count { keadaan(it) == KeadaanTanggal.TERBOOKING }}",
                    "Dibooking", AppColors.Primary, Modifier.weight(1f),
                )
                RingkasBulan(
                    "${hariBulanIni.count { keadaan(it) == KeadaanTanggal.DIBLOKIR }}",
                    "Diblokir", AppColors.Danger, Modifier.weight(1f),
                )
            }

            // --- Jam kerja ---
            Spacer(Modifier.height(26.dp))
            SectionHeader("Jam Kerja", subtitle = "Ditampilkan di profilmu supaya pelanggan tahu kapan bisa dijadwalkan")
            Spacer(Modifier.height(12.dp))
            val jam = creator?.workingHours
            PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), elevation = 3.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(MaterialTheme.shapes.small).background(AppColors.PrimarySoft),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Schedule, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (jam == null) "Belum diatur" else "${jam.start} – ${jam.end}",
                            style = MaterialTheme.typography.titleSmall,
                            color = AppColors.TextPrimary,
                        )
                        Text(
                            if (jam == null) {
                                "Tanpa jam kerja, pelanggan menebak sendiri dan sering mengajukan jam yang tidak bisa kamu ambil."
                            } else {
                                ringkasHari(jam.days)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
                jam?.note?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { dialogJam = true }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (jam == null) "Atur jam kerja" else "Ubah jam kerja")
                }
            }

            Spacer(Modifier.height(40.dp))
        }

        if (dialogJam) {
            DialogJamKerja(
                awal = creator?.workingHours ?: WorkingHours(),
                onBatal = { dialogJam = false },
                onSimpan = { baru ->
                    viewModel.simpanJamKerja(uid, baru)
                    dialogJam = false
                },
            )
        }
    }
}

/* --------------------------------------------------------------------------
 * Potongan kalender
 * ----------------------------------------------------------------------- */

@Composable
private fun SelTanggal(
    tanggal: LocalDate,
    keadaan: KeadaanTanggal,
    hariIni: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val warna = when (keadaan) {
        KeadaanTanggal.TERBOOKING -> AppColors.Primary
        KeadaanTanggal.DIBLOKIR -> AppColors.Danger
        KeadaanTanggal.TERSEDIA -> AppColors.Success
        KeadaanTanggal.LAMPAU -> AppColors.TextSecondary
    }
    // Tanggal yang sudah dibooking dan yang sudah lewat sengaja tidak bisa
    // ditekan. Tombol yang bisa ditekan tapi tidak melakukan apa-apa membuat
    // pengguna menekannya berulang kali sambil menduga aplikasinya macet.
    val bolehDitekan = keadaan == KeadaanTanggal.TERSEDIA || keadaan == KeadaanTanggal.DIBLOKIR
    val terisi = keadaan == KeadaanTanggal.TERBOOKING || keadaan == KeadaanTanggal.DIBLOKIR

    Box(
        modifier.aspectRatio(1f).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(if (terisi) warna.copy(alpha = 0.16f) else Color.Transparent)
                .then(
                    if (hariIni) Modifier.border(1.5.dp, AppColors.Primary, RoundedCornerShape(10.dp))
                    else Modifier
                )
                .clickable(enabled = bolehDitekan, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${tanggal.dayOfMonth}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (terisi || hariIni) FontWeight.W700 else FontWeight.Normal,
                    color = when (keadaan) {
                        KeadaanTanggal.LAMPAU -> AppColors.TextSecondary.copy(alpha = 0.5f)
                        KeadaanTanggal.TERSEDIA -> AppColors.TextPrimary
                        else -> warna
                    },
                )
                // Titik kecil di bawah angka: penanda kedua selain warna latar,
                // supaya keadaannya tetap terbaca oleh yang sulit membedakan
                // warna.
                if (keadaan != KeadaanTanggal.LAMPAU) {
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.size(4.dp).clip(CircleShape).background(warna))
                }
            }
        }
    }
}

@Composable
private fun Legenda(warna: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(warna))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
    }
}

@Composable
private fun RingkasBulan(nilai: String, label: String, warna: Color, modifier: Modifier = Modifier) {
    PremiumCard(modifier = modifier, elevation = 3.dp, contentPadding = PaddingValues(12.dp)) {
        Text(nilai, style = MaterialTheme.typography.titleLarge, color = warna, fontWeight = FontWeight.W700)
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
    }
}

private fun ringkasHari(days: List<Int>): String {
    if (days.isEmpty()) return "Belum ada hari kerja yang dipilih"
    if (days.size == 7) return "Setiap hari"
    return days.sorted().joinToString(", ") { HARI_PENDEK.getOrElse(it - 1) { "?" } }
}

/* --------------------------------------------------------------------------
 * Dialog jam kerja
 * ----------------------------------------------------------------------- */

@Composable
private fun DialogJamKerja(
    awal: WorkingHours,
    onBatal: () -> Unit,
    onSimpan: (WorkingHours) -> Unit,
) {
    val context = LocalContext.current
    var hari by remember { mutableStateOf(awal.days.toSet()) }
    var mulai by remember { mutableStateOf(awal.start) }
    var selesai by remember { mutableStateOf(awal.end) }
    var catatan by remember { mutableStateOf(awal.note.orEmpty()) }

    // Perbandingan string "HH:mm" cukup karena keduanya selalu dua digit
    // berpadding nol — "09:00" < "17:00" secara leksikografis maupun waktu.
    val jamTerbalik = mulai >= selesai
    val tanpaHari = hari.isEmpty()

    fun pilihJam(sekarang: String, onPilih: (String) -> Unit) {
        val jam = sekarang.substringBefore(':').toIntOrNull() ?: 9
        val menit = sekarang.substringAfter(':').toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            context,
            { _, h, m -> onPilih("%02d:%02d".format(h, m)) },
            jam, menit, true,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Jam Kerja") },
        text = {
            Column {
                Text("Hari kerja", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HARI_PENDEK.forEachIndexed { i, nama ->
                        val nomor = i + 1
                        val aktif = nomor in hari
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (aktif) AppColors.Primary else AppColors.SurfaceVariant)
                                .clickable { hari = if (aktif) hari - nomor else hari + nomor },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                nama.take(1),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (aktif) AppColors.OnPrimary else AppColors.TextSecondary,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { pilihJam(mulai) { mulai = it } },
                        modifier = Modifier.weight(1f),
                    ) { Text("Mulai $mulai") }
                    OutlinedButton(
                        onClick = { pilihJam(selesai) { selesai = it } },
                        modifier = Modifier.weight(1f),
                    ) { Text("Selesai $selesai") }
                }

                if (jamTerbalik) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Jam selesai harus setelah jam mulai.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Danger,
                    )
                }
                if (tanpaHari) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pilih minimal satu hari kerja.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Danger,
                    )
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    catatan, { catatan = it },
                    label = { Text("Catatan (opsional)") },
                    placeholder = { Text("Misal: di luar jam ini bisa nego") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !jamTerbalik && !tanpaHari,
                onClick = {
                    onSimpan(
                        WorkingHours(
                            days = hari.sorted(),
                            start = mulai,
                            end = selesai,
                            note = catatan.trim().ifBlank { null },
                        )
                    )
                },
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal") } },
    )
}
