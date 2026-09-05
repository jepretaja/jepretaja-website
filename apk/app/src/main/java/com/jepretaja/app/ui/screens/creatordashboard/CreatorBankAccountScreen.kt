package com.jepretaja.app.ui.screens.creatordashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

/**
 * Bank yang paling sering dipakai, sebagai pintasan pengisian.
 *
 * Bukan daftar tertutup: kolom namanya tetap bisa diketik bebas, karena
 * memaksa creator memilih dari daftar berarti yang memakai bank daerah atau
 * bank baru tidak bisa mencairkan dananya sama sekali.
 */
private val BANK_UMUM = listOf("BCA", "BRI", "BNI", "Mandiri", "BSI", "CIMB Niaga", "Permata", "Danamon")

/**
 * Rekening tujuan pencairan dana creator.
 *
 * Layar tersendiri, bukan bagian dari form penarikan, karena rekening adalah
 * data yang diisi SEKALI lalu dipakai berkali-kali. Sebelumnya nama bank,
 * nomor, dan nama pemilik harus diketik ulang di setiap pengajuan penarikan —
 * pengulangan yang melelahkan sekaligus tempat paling mudah terjadi salah
 * ketik, dan satu digit yang meleset berarti uang berangkat ke rekening orang
 * lain tanpa cara menariknya kembali.
 *
 * Yang tersimpan di sini dibaca panel web admin dari dokumen creator yang sama,
 * jadi tidak ada dua salinan yang bisa berbeda isinya.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun CreatorBankAccountScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorBankAccountViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid
    val menyimpan by viewModel.menyimpan.collectAsState()
    val error by viewModel.error.collectAsState()
    val tersimpan by viewModel.tersimpan.collectAsState()

    var namaBank by remember { mutableStateOf("") }
    var nomorRekening by remember { mutableStateOf("") }
    var namaPemilik by remember { mutableStateOf("") }
    var pesanValidasi by remember { mutableStateOf<String?>(null) }
    var konfirmasiHapus by remember { mutableStateOf(false) }
    // Menandai form sudah pernah diisi dari server, supaya ketikan creator
    // tidak ditimpa setiap kali snapshot Firestore datang lagi.
    var sudahDiisiDariServer by remember { mutableStateOf(false) }

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Rekening Bank", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (uid == null) {
            // Tidak seharusnya terjadi — layar ini hanya dijangkau dari Creator
            // Studio — tapi menampilkan keterangan jauh lebih baik daripada
            // layar kosong yang terlihat seperti aplikasi menggantung.
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Masuk dulu untuk mengatur rekening.", color = AppColors.TextSecondary)
            }
            return@Scaffold
        }

        val creator by remember(uid) { viewModel.streamCreator(uid) }.collectAsState(initial = null)

        LaunchedEffect(creator) {
            val c = creator
            if (c != null && !sudahDiisiDariServer) {
                namaBank = c.bankCode.orEmpty()
                nomorRekening = c.bankAccountNumber.orEmpty()
                namaPemilik = c.bankAccountName.orEmpty()
                sudahDiisiDariServer = true
            }
        }

        val sudahPunyaRekening = !creator?.bankAccountNumber.isNullOrBlank()

        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(4.dp))

            if (sudahPunyaRekening) {
                PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), elevation = 4.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppColors.Success, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Rekening tersimpan", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                            Text(
                                "${creator?.bankCode.orEmpty()} • ${samarkanNomor(creator?.bankAccountNumber.orEmpty())}",
                                style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                            )
                            Text(
                                "a.n. ${creator?.bankAccountName.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            SectionHeader(
                if (sudahPunyaRekening) "Ubah Rekening" else "Tambah Rekening",
                subtitle = "Dana pencairan hanya dikirim ke rekening ini",
            )
            Spacer(Modifier.height(12.dp))

            Column(Modifier.padding(horizontal = 20.dp)) {
                OutlinedTextField(
                    value = namaBank,
                    onValueChange = { namaBank = it; pesanValidasi = null; viewModel.bersihkanStatus() },
                    label = { Text("Nama Bank") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BANK_UMUM.forEach { bank ->
                        FilterChip(
                            selected = namaBank.equals(bank, ignoreCase = true),
                            onClick = { namaBank = bank; pesanValidasi = null; viewModel.bersihkanStatus() },
                            label = { Text(bank, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = nomorRekening,
                    // Disaring saat diketik, bukan saat disimpan: creator langsung
                    // melihat bentuk akhir nomornya dan tidak perlu menebak apakah
                    // spasi yang ia ketik ikut tersimpan.
                    onValueChange = { baru ->
                        nomorRekening = baru.filter { it.isDigit() }.take(20)
                        pesanValidasi = null; viewModel.bersihkanStatus()
                    },
                    label = { Text("Nomor Rekening") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Angka saja, tanpa spasi atau tanda hubung", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = namaPemilik,
                    onValueChange = { namaPemilik = it; pesanValidasi = null; viewModel.bersihkanStatus() },
                    label = { Text("Nama Pemilik Rekening") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    supportingText = {
                        Text(
                            "Tulis persis seperti di buku tabungan. Nama yang tidak cocok " +
                                "membuat transfer ditolak bank.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                pesanValidasi?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
                if (tersimpan) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Rekening tersimpan dan langsung terlihat di panel web.",
                        color = AppColors.Success, style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(22.dp))
                BigPrimaryButton(
                    text = if (sudahPunyaRekening) "Simpan Perubahan" else "Simpan Rekening",
                    loading = menyimpan,
                    onClick = {
                        val salah = periksaRekening(namaBank, nomorRekening, namaPemilik)
                        if (salah != null) {
                            pesanValidasi = salah
                            return@BigPrimaryButton
                        }
                        pesanValidasi = null
                        viewModel.simpan(uid, namaBank, nomorRekening, namaPemilik)
                    },
                )

                if (sudahPunyaRekening) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { konfirmasiHapus = true },
                        enabled = !menyimpan,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Hapus Rekening", color = AppColors.Danger)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), elevation = 2.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Rekening ini dipakai Admin saat memproses penarikan danamu. " +
                            "JepretAja tidak pernah memindahkan uang otomatis dari aplikasi — " +
                            "setiap pencairan diperiksa manusia lebih dulu.",
                        style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }

        if (konfirmasiHapus) {
            AlertDialog(
                onDismissRequest = { konfirmasiHapus = false },
                title = { Text("Hapus rekening?") },
                text = { Text("Kamu tidak bisa mengajukan penarikan dana sampai mengisi rekening lagi.") },
                confirmButton = {
                    TextButton(onClick = {
                        konfirmasiHapus = false
                        namaBank = ""; nomorRekening = ""; namaPemilik = ""
                        viewModel.hapus(uid)
                    }) { Text("Hapus", color = AppColors.Danger) }
                },
                dismissButton = {
                    TextButton(onClick = { konfirmasiHapus = false }) { Text("Batal") }
                },
            )
        }
    }
}

/**
 * Aturan yang bisa diperiksa tanpa menghubungi bank.
 *
 * Sengaja longgar soal panjang nomor: bank di Indonesia memakai 10 sampai 16
 * digit, dan menolak di luar rentang itu berisiko memblokir rekening yang sah
 * dari bank yang formatnya tidak umum. Yang benar-benar dijaga hanya kesalahan
 * yang pasti — kolom kosong dan nomor yang terlalu pendek untuk menjadi nomor
 * rekening apa pun.
 */
private fun periksaRekening(namaBank: String, nomor: String, namaPemilik: String): String? = when {
    namaBank.isBlank() -> "Nama bank belum diisi."
    nomor.length < 6 -> "Nomor rekening terlalu pendek."
    namaPemilik.trim().length < 3 -> "Nama pemilik rekening belum diisi dengan benar."
    else -> null
}

/** Menyisakan empat digit terakhir — cukup untuk mengenali rekening sendiri,
 * tanpa memampang nomor penuh di layar yang bisa terlihat orang lain. */
private fun samarkanNomor(nomor: String): String =
    if (nomor.length <= 4) nomor else "•••• ${nomor.takeLast(4)}"
