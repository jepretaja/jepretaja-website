package com.jepretaja.app.ui.screens.reviews

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

/**
 * Tulis ulasan untuk satu booking yang sudah selesai.
 *
 * Layar ini sebelumnya tidak ada. Aplikasi menampilkan ulasan di profil creator
 * dan punya halaman "Review Saya", tapi tidak pernah punya cara MEMBUAT ulasan —
 * sehingga daftar ulasan hanya bisa terisi lewat panel admin atau langsung dari
 * database.
 */
@Composable
fun WriteReviewScreen(
    bookingId: String,
    creatorId: String,
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: WriteReviewViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val existing by viewModel.existing.collectAsState()
    val submitting by viewModel.submitting.collectAsState()
    val message by viewModel.message.collectAsState()

    var rating by remember { mutableStateOf(0) }
    var teks by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(bookingId) { viewModel.load(bookingId) }

    // Kalau booking ini sudah pernah diulas, isian diisi ulang supaya pengguna
    // bisa memperbaikinya — bukan disuguhi form kosong yang seolah belum pernah
    // menulis apa pun.
    LaunchedEffect(existing) {
        existing?.let {
            rating = it.rating.toInt()
            teks = it.text
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
            if (it.contains("terkirim", ignoreCase = true)) onSubmitted()
        }
    }

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = if (existing != null) "Ubah Ulasan" else "Tulis Ulasan",
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Bagaimana hasil kerjanya?",
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Penilaianmu membantu pelanggan lain memilih dengan yakin.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { bintang ->
                    val terpilih = bintang <= rating
                    val skala by animateFloatAsState(
                        targetValue = if (terpilih) 1.12f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "starScale",
                    )
                    Icon(
                        if (terpilih) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Beri $bintang bintang",
                        tint = if (terpilih) AppColors.Accent else AppColors.Border,
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer { scaleX = skala; scaleY = skala }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { rating = bintang },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when (rating) {
                    1 -> "Sangat mengecewakan"
                    2 -> "Kurang memuaskan"
                    3 -> "Cukup"
                    4 -> "Bagus"
                    5 -> "Sangat memuaskan"
                    else -> "Ketuk bintang untuk menilai"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (rating == 0) AppColors.TextSecondary else AppColors.TextPrimary,
            )

            Spacer(Modifier.height(28.dp))
            PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
                OutlinedTextField(
                    value = teks,
                    onValueChange = { if (it.length <= 500) teks = it },
                    label = { Text("Ceritakan pengalamanmu (opsional)") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${teks.length}/500",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            Spacer(Modifier.height(28.dp))
            BigPrimaryButton(
                text = if (existing != null) "Perbarui Ulasan" else "Kirim Ulasan",
                loading = submitting,
                // Bintang wajib; teks boleh kosong. Memaksa menulis paragraf
                // hanya membuat orang mengetik "bagus" asal-asalan.
                enabled = rating > 0 && authState.uid != null,
                onClick = {
                    val uid = authState.uid ?: return@BigPrimaryButton
                    viewModel.submit(
                        bookingId = bookingId,
                        customerId = uid,
                        customerName = authState.profile?.name ?: "Pengguna",
                        creatorId = creatorId,
                        rating = rating,
                        text = teks.trim(),
                    )
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
