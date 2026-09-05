package com.jepretaja.app.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.navigation.Routes
import com.jepretaja.app.core.theme.BrandBlue
import com.jepretaja.app.ui.state.AuthViewModel
import kotlinx.coroutines.delay

/**
 * Splash: memeriksa sesi sambil menampilkan satu momen brand.
 *
 * Dua hal yang membuatnya tidak lagi terasa seperti layar kosong sementara:
 *
 * 1. **Durasi minimum.** Pemeriksaan sesi sering selesai dalam puluhan
 *    milidetik, dan tanpa penahan ini logo hanya berkedip sepersekian detik —
 *    terbaca sebagai gangguan, bukan pembuka. Navigasi baru dijalankan setelah
 *    animasinya sempat selesai DAN sesi sudah diketahui, mana yang lebih lama.
 *
 * 2. **Logo masuk dengan animasi**, bukan muncul mendadak; latar memakai
 *    gradient diagonal, bukan biru rata.
 */
@Composable
fun SplashScreen(
    authViewModel: AuthViewModel,
    onNavigate: (String) -> Unit,
) {
    val state by authViewModel.uiState.collectAsState()

    val skala = remember { Animatable(0.82f) }
    val kaburLogo = remember { Animatable(0f) }
    val kaburTeks = remember { Animatable(0f) }
    var animasiSelesai by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kaburLogo.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        skala.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        kaburTeks.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
        // Jeda kecil supaya nama brand sempat terbaca, bukan langsung berpindah.
        delay(450)
        animasiSelesai = true
    }

    LaunchedEffect(state.loading, state.isLoggedIn, animasiSelesai) {
        if (state.loading || !animasiSelesai) return@LaunchedEffect
        // Urutan pemeriksaan ini yang membuat aplikasi berhenti terasa "baru
        // dipasang" tiap kali dibuka: perkenalan hanya ditampilkan kepada yang
        // memang belum pernah melihatnya, dan pilihan menjadi tamu ikut diingat.
        val target = when {
            state.isLoggedIn -> Routes.HOME
            !authViewModel.onboardingSeen -> Routes.ONBOARDING
            authViewModel.guestMode -> Routes.HOME
            else -> Routes.CHOOSE_ACCESS
        }
        onNavigate(target)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.linearGradient(
                colors = listOf(BrandBlue, Color(0xFF1E52C4), Color(0xFF16307A)),
            )
        ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(skala.value)
                    .alpha(kaburLogo.value)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.CameraAlt, contentDescription = null,
                    tint = BrandBlue, modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(kaburTeks.value),
            ) {
                Text("JepretAja", style = MaterialTheme.typography.displayMedium, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Temukan & booking fotografer terbaik",
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Indikator halus di bawah — menandakan aplikasi sedang bekerja tanpa
        // spinner besar yang membuat pembukaan terasa lambat.
        if (state.loading) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.55f),
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp).size(22.dp),
            )
        }
    }
}
