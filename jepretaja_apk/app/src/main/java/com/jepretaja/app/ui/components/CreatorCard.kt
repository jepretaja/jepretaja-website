package com.jepretaja.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.data.model.CreatorModel

/**
 * Kartu creator — karyanya yang jadi bintang, bukan teksnya.
 *
 * Versi sebelumnya berbentuk foto kecil kotak di atas tiga baris teks di atas
 * permukaan putih. Untuk aplikasi fotografi itu terbalik: yang paling ingin
 * dilihat calon pelanggan justru hasil jepretannya, sementara nama dan kota
 * hanya keterangan. Sekarang foto mengisi seluruh kartu dan keterangan
 * diletakkan di atasnya dengan scrim gelap bertingkat — pola yang dipakai
 * marketplace visual (Airbnb, Behance) dan satu-satunya perubahan yang paling
 * terasa mengubah kesan "polos" jadi "premium" tanpa menambah data apa pun.
 *
 * Rasio 3:4 dipilih (bukan bujur sangkar) karena potret vertikal adalah bentuk
 * paling umum hasil foto orang, jadi lebih sedikit bagian gambar yang terpotong.
 *
 * [modifier] ada supaya kartu yang SAMA bisa dipakai di dua tata letak: lebar
 * tetap 168dp saat digulir menyamping di Home, dan `fillMaxWidth()` saat mengisi
 * sel grid di Hasil Pencarian dan Favorit. Sebelumnya lebarnya dipaku di dalam
 * komponen, jadi di grid dua kolom kartunya berhenti di 168dp dan menyisakan
 * celah kosong di kanan tiap sel — dua layar yang seharusnya memakai kartu yang
 * sama justru terlihat seperti dua komponen berbeda.
 */
@Composable
fun CreatorCard(
    creator: CreatorModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.width(168.dp),
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .aspectRatio(0.75f)
            .premiumShadow(8.dp, shape)
            .clip(shape)
            .background(AppColors.SurfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (!creator.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = creator.coverUrl,
                contentDescription = creator.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Tanpa foto sampul, kartu diberi gradient brand + inisial supaya
            // tetap terlihat disengaja, bukan seperti gambar yang gagal dimuat.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryDark))
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.CameraAlt, contentDescription = null,
                    tint = AppColors.OnPrimary.copy(alpha = 0.55f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        // Scrim tiga tingkat: transisi ke gelap yang halus, supaya teks putih
        // tetap terbaca di atas foto seterang apa pun tanpa terlihat seperti
        // kotak hitam yang ditempel.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.78f),
                        )
                    )
                ),
        )

        // Rating dipisah ke pojok atas sebagai lencana — di bawah ia berebut
        // perhatian dengan nama, di atas ia langsung terbaca saat menyapu daftar.
        if (creator.reviewCount > 0) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(3.dp))
                Text(
                    "%.1f".format(creator.rating),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    creator.displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (creator.verified) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Verified, contentDescription = "Terverifikasi",
                        tint = Color(0xFF64B5F6), modifier = Modifier.size(14.dp),
                    )
                }
            }
            creator.city?.takeIf { it.isNotBlank() }?.let { kota ->
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn, contentDescription = null,
                        tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        kota,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
