package com.jepretaja.app.ui.screens.interests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.AppConstants
import com.jepretaja.app.core.util.FirestorePaths
import com.jepretaja.app.data.local.AppPreferences
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.state.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class InterestsViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val db: FirebaseFirestore,
) : ViewModel() {
    val terpilihAwal: List<String> get() = prefs.interests

    /**
     * Minat disimpan di DUA tempat, dan itu disengaja.
     *
     * Salinan lokal dipakai peringkat feed setiap kali daftar dihitung ulang —
     * membaca Firestore untuk itu berarti satu pembacaan tiap kali feed
     * bergerak. Salinan di dokumen users membuat minat ikut pindah ketika
     * pengguna berganti perangkat.
     */
    suspend fun simpan(uid: String?, pilihan: List<String>) {
        prefs.interests = pilihan
        prefs.markInterestsChosen()
        if (uid != null) {
            runCatching {
                db.collection(FirestorePaths.USERS).document(uid)
                    .set(mapOf("interests" to pilihan), SetOptions.merge()).await()
            }
        }
    }

    fun lewati() = prefs.markInterestsChosen()
}

/**
 * Pemilih minat saat pertama masuk.
 *
 * Tanpa ini, akun baru mendapat feed yang identik dengan semua orang: peringkat
 * "For You" tidak punya satu pun sinyal tentang orangnya sampai ia menyukai
 * sesuatu, dan sinyal pertama itu baru ada setelah ia bertahan cukup lama —
 * padahal justru menit-menit pertama yang menentukan ia bertahan atau tidak.
 */
@Composable
fun InterestsScreen(
    onDone: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: InterestsViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val terpilih = remember { mutableStateListOf<String>().apply { addAll(viewModel.terpilihAwal) } }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("Apa yang ingin kamu lihat?", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Pilih minimal tiga. Feed For You akan lebih sering menampilkan yang kamu pilih — dan bisa diubah kapan saja lewat Profil.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(24.dp))

            Column(Modifier.weight(1f)) {
                AppConstants.SERVICE_CATEGORIES.chunked(2).forEach { baris ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        baris.forEach { kategori ->
                            val aktif = kategori in terpilih
                            Box(
                                Modifier.weight(1f).height(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (aktif) AppColors.Primary else AppColors.SurfaceVariant)
                                    .clickable {
                                        if (aktif) terpilih.remove(kategori) else terpilih.add(kategori)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    kategori,
                                    color = if (aktif) AppColors.OnPrimary else AppColors.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                        if (baris.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            BigPrimaryButton(
                text = if (terpilih.size < 3) "Pilih ${3 - terpilih.size} lagi" else "Selesai",
                enabled = terpilih.size >= 3,
                onClick = {
                    scope.launch {
                        viewModel.simpan(authState.uid, terpilih.toList())
                        onDone()
                    }
                },
            )
            TextButton(
                onClick = { viewModel.lewati(); onDone() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Lewati") }
        }
    }
}
