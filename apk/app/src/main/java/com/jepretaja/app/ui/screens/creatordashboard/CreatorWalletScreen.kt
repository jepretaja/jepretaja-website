package com.jepretaja.app.ui.screens.creatordashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.data.model.WalletTransactionModel
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.GradientHeroCard
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel
import java.text.SimpleDateFormat
import java.util.Locale

private val LOKAL_ID = Locale("in", "ID")
private val TANGGAL_JAM = SimpleDateFormat("d MMM yyyy • HH:mm", LOKAL_ID)

/** Saringan riwayat transaksi. */
private enum class SaringTransaksi(val judul: String, val jenis: Set<String>?) {
    SEMUA("Semua", null),
    MASUK("Dana masuk", setOf("credit")),
    PENARIKAN("Penarikan", setOf("withdrawal")),
    POTONGAN("Potongan", setOf("fee", "adjustment")),
    REFUND("Refund", setOf("refund")),
}

/**
 * Wallet creator.
 *
 * **Yang berubah.** Kartu saldo lama menampilkan tiga angka berlabel Inggris
 * ("Available Balance", "Pending", "Ditarik") tanpa satu pun keterangan apa
 * bedanya — padahal perbedaan antara saldo tersedia dan saldo tertahan adalah
 * hal terpenting yang perlu dimengerti creator di layar ini: yang satu bisa
 * ditarik hari ini, yang satu lagi masih terkunci sampai booking-nya beres.
 *
 * Riwayat transaksi juga tidak punya saringan maupun tanggal. Daftar lima puluh
 * baris bertuliskan "Credit" berulang, tanpa waktu dan tanpa cara menyaring,
 * tidak bisa dipakai menjawab satu pun pertanyaan yang wajar — "penarikan
 * terakhir saya kapan?", "bulan ini masuk berapa?".
 */
@Composable
fun CreatorWalletScreen(
    onBack: () -> Unit,
    onWithdraw: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorWalletViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid
    var saring by remember { mutableStateOf(SaringTransaksi.SEMUA) }

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppColors.Background,
        topBar = { AppTopBar(title = "Wallet", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (uid == null) return@Scaffold

        val wallet by remember(uid) { viewModel.streamWallet(uid) }.collectAsState(initial = null)
        val ledger by remember(uid) { viewModel.streamLedger(uid) }.collectAsState(initial = emptyList())

        val tersaring = remember(ledger, saring) {
            val jenis = saring.jenis
            ledger.filter { jenis == null || it.type in jenis }
        }

        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(4.dp))

            GradientHeroCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "Saldo Tersedia",
                    color = AppColors.OnPrimary.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    Formatters.currency(wallet?.availableBalance ?: 0),
                    color = AppColors.OnPrimary,
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Bisa ditarik sekarang.",
                    color = AppColors.OnPrimary.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onWithdraw,
                    enabled = (wallet?.availableBalance ?: 0) > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.OnPrimary,
                        contentColor = AppColors.Primary,
                    ),
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Tarik Dana") }
            }

            // Tiga angka lain diberi kartu sendiri berikut satu kalimat
            // penjelas. "Pending" tanpa keterangan terbaca seperti kesalahan,
            // bukan seperti uang yang memang belum waktunya cair.
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BarisSaldo(
                    ikon = Icons.Default.HourglassTop,
                    warna = AppColors.Warning,
                    label = "Saldo tertahan",
                    nilai = Formatters.currency(wallet?.pendingBalance ?: 0),
                    keterangan = "Dana booking yang belum dilepas. Cair setelah pemotretan selesai dan pelanggan konfirmasi.",
                )
                BarisSaldo(
                    ikon = Icons.Default.TrendingUp,
                    warna = AppColors.Success,
                    label = "Total pendapatan",
                    nilai = Formatters.currency(wallet?.totalEarnings ?: 0),
                    keterangan = "Seluruh dana yang pernah masuk, setelah biaya layanan.",
                )
                BarisSaldo(
                    ikon = Icons.Default.AccountBalanceWallet,
                    warna = AppColors.Info,
                    label = "Sudah ditarik",
                    nilai = Formatters.currency(wallet?.withdrawn ?: 0),
                    keterangan = "Total yang sudah dikirim ke rekeningmu.",
                )
            }

            Spacer(Modifier.height(26.dp))
            SectionHeader(
                "Transaksi Terakhir",
                subtitle = if (ledger.isEmpty()) null else "${tersaring.size} dari ${ledger.size} transaksi",
            )
            Spacer(Modifier.height(10.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SaringTransaksi.entries) { s ->
                    val jumlah = if (s.jenis == null) ledger.size else ledger.count { it.type in s.jenis }
                    FilterChip(
                        selected = saring == s,
                        onClick = { saring = s },
                        label = { Text(if (jumlah > 0) "${s.judul} · $jumlah" else s.judul) },
                        shape = RoundedCornerShape(percent = 50),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (tersaring.isEmpty()) {
                EmptyState(
                    title = if (ledger.isEmpty()) "Belum ada transaksi" else "Tidak ada transaksi ${saring.judul.lowercase()}",
                    description = if (ledger.isEmpty()) {
                        "Setiap dana masuk dari booking dan setiap penarikan akan tercatat di sini."
                    } else {
                        "Ganti saringan di atas untuk melihat transaksi lainnya."
                    },
                    icon = Icons.Default.ReceiptLong,
                )
            } else {
                tersaring.forEach { tx -> BarisTransaksi(tx) }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BarisSaldo(
    ikon: ImageVector,
    warna: Color,
    label: String,
    nilai: String,
    keterangan: String,
) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(warna.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) { Icon(ikon, contentDescription = null, tint = warna, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                Text(
                    nilai,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W700,
                    color = AppColors.TextPrimary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(keterangan, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

/** Satu baris ledger: jenis, catatan, waktu, dan nominal bertanda. */
@Composable
private fun BarisTransaksi(tx: WalletTransactionModel) {
    val masuk = tx.type == "credit" || tx.type == "refund"
    val ikon = when (tx.type) {
        "credit" -> Icons.Default.ArrowDownward
        "withdrawal" -> Icons.Default.ArrowUpward
        "refund" -> Icons.Default.Undo
        else -> Icons.Default.Remove
    }
    val warna = when {
        tx.status != "success" -> AppColors.Warning
        masuk -> AppColors.Success
        else -> AppColors.TextSecondary
    }
    val judul = when (tx.type) {
        "credit" -> "Dana masuk"
        "withdrawal" -> "Penarikan"
        "refund" -> "Refund"
        "fee" -> "Biaya layanan"
        else -> "Penyesuaian"
    }

    PremiumCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        elevation = 3.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(warna.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) { Icon(ikon, contentDescription = null, tint = warna, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(judul, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                Text(
                    // Waktu ditampilkan, bukan disembunyikan. Riwayat keuangan
                    // tanpa tanggal tidak bisa dicocokkan dengan mutasi bank
                    // ketika ada selisih — dan itulah satu-satunya saat orang
                    // benar-benar membuka halaman ini.
                    tx.note?.takeIf { it.isNotBlank() }
                        ?: tx.createdAt?.toDate()?.let { TANGGAL_JAM.format(it) }
                        ?: "Waktu tidak tercatat",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tx.note?.isNotBlank() == true) {
                    tx.createdAt?.toDate()?.let {
                        Text(
                            TANGGAL_JAM.format(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.TextSecondary,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (masuk) "+ " else "− ") + Formatters.currency(tx.amount),
                    color = if (masuk) AppColors.Success else AppColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (tx.status != "success") {
                    Text(
                        tx.status.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Warning,
                    )
                }
            }
        }
    }
}
