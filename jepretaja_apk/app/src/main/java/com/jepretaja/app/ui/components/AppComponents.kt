package com.jepretaja.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors

/**
 * Bahasa visual bersama untuk seluruh layar ("premium editorial"): kartu
 * memakai shadow lembut alih-alih border 1dp datar, section header pakai
 * skala tipografi Fraunces yang sudah ada di [com.jepretaja.app.core.theme.AppTypography],
 * dan komponen ringkasan (hero/stat) berbagi satu tampilan alih-alih tiap
 * layar menggambar ulang pola yang sama dengan sedikit perbedaan.
 *
 * Sebelumnya nyaris semua kartu di 65+ layar memakai `Card(border = 1dp
 * AppColors.Border)` — flat dan tidak berjenjang. Shadow bertingkat kecil di
 * sini (bukan elevation Material default yang keabu-abuan) memberi kesan
 * "diangkat" dari background tanpa terasa berat.
 */

/** Shadow lembut bersama — dipakai [PremiumCard] & [GradientHeroCard], dan bisa
 * dipakai langsung di komponen lain (mis. [CreatorCard]) untuk kartu non-Column. */
fun Modifier.premiumShadow(elevation: androidx.compose.ui.unit.Dp, shape: Shape) = this.shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = Color.Black.copy(alpha = 0.10f),
    spotColor = Color.Black.copy(alpha = 0.14f),
)

/** Kartu dasar dipakai di seluruh app menggantikan `Card(border = ...)` datar. */
@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    elevation: androidx.compose.ui.unit.Dp = 6.dp,
    color: Color = AppColors.Surface,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .premiumShadow(elevation, shape)
        .clip(shape)
        .background(color)
    Column(
        modifier = if (onClick != null) base.clickable(onClick = onClick) else base,
        content = { Column(Modifier.padding(contentPadding), content = content) },
    )
}

/** Judul section konsisten (Fraunces via headlineSmall) + aksi "Lihat semua" opsional. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Kartu ringkasan gradient (saldo wallet, ringkasan dashboard, header profil).
 * Generalisasi dari kartu saldo yang sebelumnya cuma ada di CreatorDashboardScreen. */
@Composable
fun GradientHeroCard(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(AppColors.Primary, AppColors.PrimaryDark),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier
            .premiumShadow(14.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(colors, start = Offset(0f, 0f), end = Offset(1000f, 1000f)))
            .padding(20.dp),
        content = content,
    )
}

/** Satu ubin statistik (ikon + angka besar + label) untuk dashboard/ringkasan. */
@Composable
fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = AppColors.Primary,
) {
    PremiumCard(modifier = modifier, elevation = 3.dp, contentPadding = PaddingValues(14.dp)) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
    }
}

/** Avatar bulat dengan fallback inisial saat tidak ada foto, dan lencana verified opsional. */
@Composable
fun AppAvatar(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    verified: Boolean = false,
) {
    Box(modifier.size(size)) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape),
            )
        } else {
            Box(
                Modifier.matchParentSize().clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryDark))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = AppColors.OnPrimary, fontWeight = FontWeight.W700,
                    fontSize = (size.value * 0.4).sp,
                )
            }
        }
        if (verified) {
            // Lencana terverifikasi memakai ikon Verified, bukan bintang —
            // bintang di aplikasi ini sudah berarti rating, dua makna berbeda
            // untuk satu ikon membingungkan.
            Icon(
                Icons.Default.Verified, contentDescription = "Terverifikasi", tint = AppColors.Info,
                modifier = Modifier.align(Alignment.BottomEnd).size(size * 0.34f)
                    .clip(CircleShape).background(AppColors.Surface),
            )
        }
    }
}

/** Baris label-nilai konsisten untuk layar detail/settings — pengganti Row ad hoc. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppColors.TextPrimary,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, color = valueColor)
    }
}

/** Tombol CTA penuh-lebar, pill, dipakai untuk aksi utama tiap layar. */
@Composable
fun BigPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(percent = 50),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
        modifier = modifier.fillMaxWidth().height(54.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AppColors.OnPrimary, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/**
 * Tombol ikon dengan badge jumlah di pojok kanan atas.
 *
 * Badge hanya digambar saat [count] > 0 — lingkaran merah permanen berisi "0"
 * melatih mata mengabaikannya, sehingga saat benar-benar ada yang baru masuk
 * ia tidak lagi menarik perhatian. Angka di atas 99 dipendekkan jadi "99+"
 * supaya lebarnya tidak mendorong ikon di sebelahnya.
 */
@Composable
fun BadgedIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    count: Int,
    onClick: () -> Unit,
    tint: Color = AppColors.TextPrimary,
) {
    IconButton(onClick = onClick) {
        BadgedBox(
            badge = {
                if (count > 0) {
                    Badge(
                        containerColor = AppColors.Danger,
                        contentColor = Color.White,
                    ) {
                        Text(
                            badgeLabel(count),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.W700,
                        )
                    }
                }
            },
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

/** "5", "42", lalu "99+" — dipakai bersama oleh semua badge jumlah. */
fun badgeLabel(count: Int): String = if (count > 99) "99+" else count.toString()
