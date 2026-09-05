package com.jepretaja.app.ui.screens.creatordashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.data.repository.AuthRepository
import com.jepretaja.app.data.repository.CreatorRepository
import com.jepretaja.app.services.StorageService
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.LocationField
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel
import kotlinx.coroutines.launch

@dagger.hilt.android.lifecycle.HiltViewModel
class CreatorSettingsViewModel @javax.inject.Inject constructor(
    val creatorRepository: CreatorRepository,
    val authRepository: AuthRepository,
    val storageService: StorageService,
) : androidx.lifecycle.ViewModel()

/** Creator Settings (section 8 & 14) — edit profil + upload dokumen
 * verifikasi + logout. */
@Composable
fun CreatorSettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorSettingsViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(authState.profile?.name ?: "") }
    var bio by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var uploadingDoc by remember { mutableStateOf(false) }

    // Saklar menerima booking. Dimuat dari dokumen creator supaya posisinya
    // benar setelah aplikasi dibuka ulang, bukan selalu kembali ke "menerima".
    var menerimaBooking by remember { mutableStateOf(true) }
    var liburSampai by remember { mutableStateOf<Long?>(null) }
    var catatanLibur by remember { mutableStateOf("") }
    var pilihTanggal by remember { mutableStateOf(false) }

    LaunchedEffect(uid) {
        if (uid != null) {
            runCatching { viewModel.creatorRepository.getCreator(uid) }.getOrNull()?.let { c ->
                menerimaBooking = c.acceptingBookings
                liburSampai = c.awayUntil?.toDate()?.time
                catatanLibur = c.awayNote.orEmpty()
                if (bio.isBlank()) bio = c.bio.orEmpty()
                if (city.isBlank()) city = c.city.orEmpty()
            }
        }
    }

    val pickDoc = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && uid != null) {
            uploadingDoc = true
            scope.launch {
                runCatching {
                    val url = viewModel.storageService.uploadVerificationDocument(uid, uri, "jpg")
                    viewModel.creatorRepository.submitVerificationDocument(uid, url, "ktp")
                }
                uploadingDoc = false
                message = "Dokumen terkirim, menunggu verifikasi Admin"
            }
        }
    }

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Pengaturan", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))
            SectionHeader("Ketersediaan")
            Spacer(Modifier.height(12.dp))
            PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Menerima booking", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                        Text(
                            // Dijelaskan bedanya dari titik hijau, karena dua
                            // hal ini paling sering dikira sama.
                            "Berbeda dari titik hijau \"online\" yang muncul otomatis saat kamu membuka aplikasi. " +
                                "Saklar ini keputusanmu: kalau dimatikan, pesanan baru ditolak server, bukan sekadar tombolnya disembunyikan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                        )
                    }
                    Switch(
                        checked = menerimaBooking,
                        onCheckedChange = { aktif ->
                            menerimaBooking = aktif
                            if (aktif) { liburSampai = null; catatanLibur = "" }
                            if (uid != null) {
                                scope.launch {
                                    runCatching {
                                        viewModel.creatorRepository.setAcceptingBookings(
                                            uid, aktif, if (aktif) null else liburSampai,
                                            catatanLibur.ifBlank { null },
                                        )
                                    }
                                    message = if (aktif) "Kamu menerima booking lagi." else "Booking baru dihentikan sementara."
                                }
                            }
                        },
                    )
                }
                if (!menerimaBooking) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { pilihTanggal = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            liburSampai?.let { "Kembali menerima: ${java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("in", "ID")).format(java.util.Date(it))}" }
                                ?: "Pilih tanggal kembali (opsional)",
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        catatanLibur, { catatanLibur = it },
                        label = { Text("Catatan untuk pelanggan (opsional)") },
                        placeholder = { Text("Contoh: sedang di luar kota sampai akhir bulan") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (uid == null) return@Button
                            scope.launch {
                                runCatching {
                                    viewModel.creatorRepository.setAcceptingBookings(
                                        uid, false, liburSampai, catatanLibur.ifBlank { null },
                                    )
                                }
                                message = "Status libur diperbarui."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Simpan Status Libur") }
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader("Profil")
            Spacer(Modifier.height(12.dp))
            PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama / Nama Studio") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(bio, { bio = it }, label = { Text("Bio") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                LocationField(
                    value = city,
                    onValueChange = { city = it },
                    label = "Kota",
                    placeholder = "Ketik kota, atau ambil otomatis",
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let { Spacer(Modifier.height(10.dp)); Text(it, color = AppColors.Success, style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (uid == null) return@Button
                        saving = true
                        scope.launch {
                            runCatching { viewModel.creatorRepository.updateProfile(uid, name.trim(), bio.trim().ifBlank { null }, city.trim().ifBlank { null }, emptyList()) }
                            saving = false
                            message = "Profil diperbarui"
                        }
                    },
                    enabled = !saving,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(if (saving) "Menyimpan..." else "Simpan Profil") }
            }

            Spacer(Modifier.height(28.dp))
            SectionHeader("Verifikasi Akun", subtitle = "Upload KTP/dokumen identitas untuk mendapatkan badge terverifikasi.")
            Spacer(Modifier.height(12.dp))
            PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                OutlinedButton(
                    onClick = { pickDoc.launch("image/*") },
                    enabled = !uploadingDoc,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null); Spacer(Modifier.width(8.dp))
                    Text(if (uploadingDoc) "Mengunggah..." else "Upload Dokumen Verifikasi")
                }
            }

            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = { viewModel.authRepository.logout(); onLoggedOut() },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Danger),
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 20.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Keluar") }
            Spacer(Modifier.height(20.dp))
        }
    }
        if (pilihTanggal) {
            val statePicker = rememberDatePickerState()
            DatePickerDialog(
                onDismissRequest = { pilihTanggal = false },
                confirmButton = {
                    TextButton(onClick = {
                        liburSampai = statePicker.selectedDateMillis
                        pilihTanggal = false
                    }) { Text("Pilih") }
                },
                dismissButton = { TextButton(onClick = { pilihTanggal = false }) { Text("Batal") } },
            ) { DatePicker(state = statePicker) }
        }

}
