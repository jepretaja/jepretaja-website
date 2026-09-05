package com.jepretaja.app.ui.screens.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.premiumShadow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val warna: List<Color>,
)

/**
 * Perkenalan empat langkah.
 *
 * Versi sebelumnya menampilkan ikon Material generik di dalam kotak abu-abu
 * bergradasi tipis — bentuk yang sama persis di keempat halaman, hanya ikonnya
 * berganti. Hasilnya terbaca seperti placeholder yang belum sempat diisi.
 *
 * Sekarang tiap halaman punya panel gradient berwarna khas dengan lingkaran
 * transparan berlapis sebagai kedalaman, dan ikonnya besar di atasnya. Tanpa
 * menambah satu berkas gambar pun, tiap langkah jadi punya identitas sendiri
 * dan urutannya terasa maju — bukan empat salinan layar yang sama.
 */
private val pages = listOf(
    OnboardingPage(
        Icons.Default.Search, "Cari Creator",
        "Temukan fotografer & videografer sesuai kebutuhan, lokasi, dan gaya favoritmu.",
        listOf(Color(0xFF2F6FED), Color(0xFF1E52C4)),
    ),
    OnboardingPage(
        Icons.Default.PhotoLibrary, "Lihat Karya",
        "Jelajahi portfolio nyata lewat Explore sebelum memutuskan booking.",
        listOf(Color(0xFF7B4DED), Color(0xFF4B2CA8)),
    ),
    OnboardingPage(
        Icons.Default.EventAvailable, "Booking Mudah",
        "Pilih paket, tanggal, dan lokasi — semua dalam satu alur booking.",
        listOf(Color(0xFFED8A2F), Color(0xFFC4601E)),
    ),
    OnboardingPage(
        Icons.Default.VerifiedUser, "Transaksi Aman",
        "Dana ditahan sampai pekerjaan selesai dan kamu mengonfirmasinya.",
        listOf(Color(0xFF16A34A), Color(0xFF0B7233)),
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val halamanTerakhir = pagerState.currentPage == pages.size - 1

    Column(Modifier.fillMaxSize().background(AppColors.Background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            // Tombol lewati disembunyikan di halaman terakhir — di situ tombol
            // utamanya sudah "Mulai", jadi menawarkan "Lewati" hanya membuat
            // dua tombol yang artinya sama bersaing.
            AnimatedSkip(visible = !halamanTerakhir, onClick = onFinish)
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val p = pages[page]
            // Selisih posisi halaman terhadap layar, dipakai menggeser isi
            // sedikit lebih lambat dari gulirannya (efek parallax) supaya
            // perpindahan terasa berlapis, bukan datar.
            val selisih = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val jarak = selisih.absoluteValue.coerceIn(0f, 1f)

            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.5f))
                Box(
                    Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .alpha(1f - jarak * 0.45f)
                        .premiumShadow(18.dp, RoundedCornerShape(36.dp))
                        .clip(RoundedCornerShape(36.dp))
                        .background(Brush.linearGradient(p.warna)),
                    contentAlignment = Alignment.Center,
                ) {
                    // Dua lingkaran transparan berlapis: memberi kedalaman pada
                    // panel supaya tidak terlihat seperti blok warna rata.
                    Box(
                        Modifier.fillMaxSize(0.86f).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                    Box(
                        Modifier.fillMaxSize(0.58f).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                    )
                    Icon(
                        p.icon, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(92.dp),
                    )
                }

                Spacer(Modifier.height(40.dp))
                Text(
                    p.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    p.desc,
                    textAlign = TextAlign.Center,
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.weight(1f))
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pages.size) { i ->
                val aktif = pagerState.currentPage == i
                val lebar by animateDpAsState(if (aktif) 26.dp else 8.dp, spring(), label = "dotWidth")
                val warna by animateColorAsState(
                    if (aktif) AppColors.Primary else AppColors.Border,
                    tween(220), label = "dotColor",
                )
                Box(
                    Modifier.padding(horizontal = 4.dp).height(8.dp).width(lebar)
                        .clip(RoundedCornerShape(percent = 50)).background(warna)
                )
            }
        }

        Button(
            onClick = {
                if (halamanTerakhir) onFinish()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 10.dp).height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Primary,
                contentColor = AppColors.OnPrimary,
            ),
        ) {
            Text(
                if (halamanTerakhir) "Mulai Sekarang" else "Lanjut",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
private fun AnimatedSkip(visible: Boolean, onClick: () -> Unit) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (visible) 1f else 0f, tween(200), label = "skipAlpha",
    )
    TextButton(onClick = onClick, enabled = visible, modifier = Modifier.alpha(alpha)) {
        Text("Lewati", color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
    }
}
