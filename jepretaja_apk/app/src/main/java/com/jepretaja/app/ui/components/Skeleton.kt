package com.jepretaja.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors

/**
 * Placeholder berkilau (shimmer) untuk konten yang sedang dimuat.
 *
 * Sebelumnya semua layar memakai CircularProgressIndicator di tengah layar
 * kosong. Spinner tidak memberi petunjuk apa pun tentang isi yang akan datang,
 * dan membuat perpindahan terasa mengejutkan karena tata letak melompat begitu
 * data tiba. Skeleton menahan bentuk layout sejak awal sehingga terasa lebih
 * cepat dan tenang — pola yang dipakai Instagram, Airbnb, YouTube.
 */
@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val base = AppColors.SurfaceVariant
    val highlight = AppColors.Border
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate - 400f, 0f),
        end = Offset(translate, 0f),
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
) {
    Box(modifier.clip(shape).background(shimmerBrush()))
}

/** Skeleton satu baris daftar: avatar bulat + dua baris teks. */
@Composable
fun SkeletonListItem(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBox(Modifier.size(44.dp), MaterialTheme.shapes.extraLarge)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            SkeletonBox(Modifier.fillMaxWidth(0.55f).height(13.dp))
            Spacer(Modifier.height(7.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.8f).height(11.dp))
        }
    }
}

/** Beberapa baris skeleton sekaligus — pengganti langsung spinner di daftar. */
@Composable
fun SkeletonList(count: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        repeat(count) { SkeletonListItem() }
    }
}

/**
 * Skeleton kartu creator — bentuk & ukurannya harus SAMA PERSIS dengan
 * [com.jepretaja.app.ui.components.CreatorCard] (168dp, rasio 3:4, sudut 18dp),
 * karena seluruh gunanya memang menahan ruang supaya tata letak tidak melompat
 * begitu data tiba. Kalau ukurannya berbeda, skeleton justru menimbulkan
 * lompatan yang seharusnya ia cegah.
 */
@Composable
fun SkeletonCreatorCard(modifier: Modifier = Modifier.width(168.dp)) {
    SkeletonBox(
        modifier.aspectRatio(0.75f),
        androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    )
}

/**
 * Kerangka satu ubin foto di grid Explore/Hasil Pencarian.
 *
 * Rasio dan jarak antar-ubinnya sengaja disamakan dengan mozaik aslinya (3:4,
 * jarak 1dp) — sama seperti [SkeletonCreatorCard], gunanya menahan bentuk, dan
 * kerangka yang bentuknya beda justru menimbulkan lompatan yang ia hindari.
 */
@Composable
fun SkeletonPostTile(modifier: Modifier = Modifier) {
    SkeletonBox(modifier.aspectRatio(0.75f), androidx.compose.ui.graphics.RectangleShape)
}

@Composable
fun SkeletonCreatorRow(count: Int = 3, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) { SkeletonCreatorCard() }
    }
}
