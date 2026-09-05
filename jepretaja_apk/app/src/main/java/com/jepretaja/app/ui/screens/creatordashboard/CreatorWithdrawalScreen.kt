package com.jepretaja.app.ui.screens.creatordashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.InfoRow
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.StatusBadge
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val LOKAL_ID = Locale("in", "ID")
private val TANGGAL = SimpleDateFormat("d MMM yyyy", LOKAL_ID)

/**
 * Pengajuan penarikan dana.
 *
 * **Yang berubah.** Form lama hanya punya satu kolom nominal berikut satu baris
 * "Tersedia Rp…". Tiga aturan yang menentukan diterima atau tidaknya pengajuan
 * — nominal minimum, saldo tersisa setelah ditarik, dan berapa lama dananya
 * sampai — semuanya tidak pernah disebut. Yang minimum bahkan sudah lama
 * diperiksa server (`appWallet.js`) tapi tak pernah dibaca aplikasi, jadi
 * creator baru tahu batasnya setelah pengajuannya ditolak.
 *
 * Sekarang keempat angka itu digambar sebagai rincian yang berubah mengikuti
 * ketikan, dengan baris terakhir — sisa saldo setelah penarikan — yang paling
 * ditanyakan orang sebelum menekan kirim.
 */
@Composable
fun CreatorWithdrawalScreen(
    onBack: () -> Unit,
    onKelolaRekening: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorWalletViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid
    var nominal by remember { mutableStateOf("") }
    var konfirmasi by remember { mutableStateOf(false) }
    val submitting by viewModel.submitting.collectAsState()
    val submitError by viewModel.error.collectAsState()
    val submitSuccess by viewModel.success.collectAsState()

    LaunchedEffect(submitSuccess) { if (submitSuccess) nominal = "" }

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppColors.Background,
        topBar = { AppTopBar(title = "Tarik Dana", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (uid == null) return@Scaffold

        val wallet by remember(uid) { viewModel.streamWallet(uid) }.collectAsState(initial = null)
        val withdrawals by remember(uid) { viewModel.streamWithdrawals(uid) }.collectAsState(initial = emptyList())
        val creator by remember(uid) { viewModel.streamCreator(uid) }.collectAsState(initial = null)
        val aturan by remember { viewModel.pengaturanPenarikan() }
            .collectAsState(initial = com.jepretaja.app.data.repository.PengaturanPenarikan())

        val namaBank = creator?.bankCode.orEmpty()
        val nomorRekening = creator?.bankAccountNumber.orEmpty()
        val namaPemilik = creator?.bankAccountName.orEmpty()
        val punyaRekening = nomorRekening.isNotBlank() && namaBank.isNotBlank() && namaPemilik.isNotBlank()

        val tersedia = wallet?.availableBalance ?: 0L
        val jumlah = nominal.toLongOrNull() ?: 0L
        val diterima = (jumlah - aturan.biayaAdmin).coerceAtLeast(0L)
        val sisa = (tersedia - jumlah).coerceAtLeast(0L)

        // Satu pengajuan berjalan pada satu waktu — aturan itu ditegakkan
        // server. Ditampilkan di sini juga supaya creator tidak mengisi form
        // panjang lalu ditolak di ujung.
        val adaBerjalan = withdrawals.any { it.status == "requested" || it.status == "processing" }

        val galat: String? = when {
            nominal.isBlank() -> null
            jumlah <= 0 -> "Isi jumlah penarikan."
            jumlah < aturan.minimum -> "Minimal ${Formatters.currency(aturan.minimum)} per penarikan."
            jumlah > tersedia -> "Melebihi saldo tersedia (${Formatters.currency(tersedia)})."
            else -> null
        }
        val bolehKirim = punyaRekening && !adaBerjalan && galat == null && jumlah >= aturan.minimum && jumlah <= tersedia

        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))

            // --- Rekening aktif ---
            SectionHeader("Rekening Tujuan", subtitle = "Dana dikirim ke rekening ini")
            Spacer(Modifier.height(12.dp))
            PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), elevation = 3.dp) {
                if (punyaRekening) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(AppColors.Success.copy(alpha = 0.13f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.CheckCircle, contentDescription = null,
                                tint = AppColors.Success, modifier = Modifier.size(19.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                namaBank.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.TextSecondary,
                            )
                            Text(
                                // Nomor rekening ditulis penuh, tidak disamarkan:
                                // ini layar milik pemiliknya sendiri, dan yang
                                // perlu ia lakukan justru MEMERIKSA digitnya
                                // sebelum uang dikirim ke sana.
                                nomorRekening,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.W700,
                                color = AppColors.TextPrimary,
                            )
                            Text(
                                "a.n. $namaPemilik",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pastikan nama pemilik rekening sama persis dengan data banknya. Transfer ke nama yang tidak cocok akan dikembalikan bank.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onKelolaRekening, contentPadding = PaddingValues(0.dp)) {
                        Text("Ubah rekening")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ErrorOutline, contentDescription = null,
                            tint = AppColors.Warning, modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Belum ada rekening tersimpan",
                            style = MaterialTheme.typography.titleSmall,
                            color = AppColors.TextPrimary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Isi rekening bankmu dulu — tanpa itu admin tidak punya tujuan untuk mengirim dananya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    BigPrimaryButton(text = "Tambah Rekening", onClick = onKelolaRekening)
                }
            }

            if (adaBerjalan) {
                Spacer(Modifier.height(14.dp))
                PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), elevation = 3.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule, contentDescription = null,
                            tint = AppColors.Warning, modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Masih ada penarikan yang sedang diproses. Tunggu sampai selesai sebelum mengajukan lagi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
            }

            // --- Nominal ---
            Spacer(Modifier.height(24.dp))
            SectionHeader("Jumlah Penarikan")
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = nominal,
                    // Hanya angka: "Rp 500.000" yang diketik apa adanya membuat
                    // toLongOrNull() bernilai null, dan pengguna hanya melihat
                    // tombol mati tanpa tahu apa salahnya.
                    onValueChange = { baru -> nominal = baru.filter { it.isDigit() }.take(12) },
                    label = { Text("Jumlah") },
                    prefix = { Text("Rp", color = AppColors.TextSecondary) },
                    singleLine = true,
                    isError = galat != null,
                    supportingText = {
                        Text(
                            galat ?: "Saldo tersedia ${Formatters.currency(tersedia)} · minimal ${Formatters.currency(aturan.minimum)}",
                            color = if (galat != null) AppColors.Danger else AppColors.TextSecondary,
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Pintasan yang menghindarkan salah ketik nol. Tarik seluruh
                // saldo adalah pilihan yang paling sering diambil, dan mengetik
                // "2350000" dengan benar lebih sulit daripada terlihat.
                if (tersedia >= aturan.minimum) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { nominal = aturan.minimum.toString() },
                            label = { Text("Minimum") },
                            shape = RoundedCornerShape(percent = 50),
                        )
                        AssistChip(
                            onClick = { nominal = tersedia.toString() },
                            label = { Text("Semua saldo") },
                            shape = RoundedCornerShape(percent = 50),
                        )
                    }
                }

                // --- Rincian ---
                Spacer(Modifier.height(16.dp))
                PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
                    InfoRow("Jumlah ditarik", Formatters.currency(jumlah))
                    InfoRow(
                        "Biaya admin",
                        // Server tidak memotong biaya apa pun saat ini. Menulis
                        // angka yang tidak benar-benar dipotong akan membuat
                        // uang yang masuk ke rekening tidak cocok dengan yang
                        // dijanjikan layar ini.
                        if (aturan.biayaAdmin == 0L) "Gratis" else "− ${Formatters.currency(aturan.biayaAdmin)}",
                        valueColor = if (aturan.biayaAdmin == 0L) AppColors.Success else AppColors.TextPrimary,
                    )
                    HorizontalDivider(color = AppColors.Border, modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Diterima di rekening", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
                        Text(
                            Formatters.currency(diterima),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.W700,
                            color = AppColors.Primary,
                        )
                    }
                    HorizontalDivider(color = AppColors.Border, modifier = Modifier.padding(vertical = 4.dp))
                    InfoRow("Saldo setelah penarikan", Formatters.currency(sisa))
                    InfoRow(
                        "Estimasi pencairan",
                        "±${aturan.estimasiHariKerja} hari kerja setelah disetujui",
                    )
                }

                submitError?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
                if (submitSuccess) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Permintaan penarikan berhasil diajukan. Statusnya bisa dipantau di bawah.",
                        color = AppColors.Success,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(20.dp))
                BigPrimaryButton(
                    text = if (submitting) "Memproses..." else "Ajukan Penarikan",
                    loading = submitting,
                    enabled = bolehKirim && !submitting,
                    onClick = { konfirmasi = true },
                )
            }

            Spacer(Modifier.height(32.dp))
            SectionHeader("Riwayat Penarikan")
            Spacer(Modifier.height(12.dp))
            if (withdrawals.isEmpty()) {
                EmptyState(
                    title = "Belum ada riwayat",
                    description = "Penarikan yang kamu ajukan akan muncul di sini beserta statusnya.",
                    icon = Icons.Default.AccountBalance,
                )
            } else {
                withdrawals.forEach { w ->
                    PremiumCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp),
                        elevation = 3.dp,
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Formatters.currency(w.amount),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AppColors.TextPrimary,
                                )
                                Text(
                                    listOfNotNull(
                                        w.createdAt?.toDate()?.let { TANGGAL.format(it) },
                                        w.bankAccountNumber?.takeIf { it.isNotBlank() },
                                    ).joinToString(" • ").ifBlank { "Tanggal tidak tercatat" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                )
                            }
                            StatusBadge(status = w.status)
                        }
                        // Alasan kegagalan ditampilkan. Status "failed" tanpa
                        // sebab tidak memberi tahu apakah yang perlu diperbaiki
                        // adalah nomor rekeningnya atau namanya.
                        w.failureReason?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Danger)
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        // Konfirmasi terakhir. Penarikan mengunci saldo sampai admin memproses
        // dan hanya boleh satu berjalan sekaligus, jadi salah tekan berarti
        // menunggu satu putaran penuh sebelum bisa mengajukan yang benar.
        if (konfirmasi) {
            AlertDialog(
                onDismissRequest = { konfirmasi = false },
                icon = { Icon(Icons.Default.AccountBalance, contentDescription = null, tint = AppColors.Primary) },
                title = { Text("Ajukan penarikan?") },
                text = {
                    Column {
                        Text("${Formatters.currency(diterima)} akan dikirim ke:")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${namaBank.uppercase()} $nomorRekening",
                            style = MaterialTheme.typography.titleSmall,
                            color = AppColors.TextPrimary,
                        )
                        Text("a.n. $namaPemilik", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Setelah diajukan, kamu tidak bisa mengajukan penarikan lain sampai yang ini selesai diproses.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        konfirmasi = false
                        viewModel.requestWithdrawal(uid, jumlah, namaBank, nomorRekening, namaPemilik)
                    }) { Text("Ajukan") }
                },
                dismissButton = { TextButton(onClick = { konfirmasi = false }) { Text("Periksa lagi") } },
            )
        }
    }
}
