package com.jepretaja.app.core.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palet JepretAja — sekarang punya DUA varian (terang & gelap).
 *
 * Sebelumnya `AppColors` adalah object dengan nilai hardcode tunggal, sehingga
 * 43 file layar selalu mendapat warna mode terang apa pun setelan HP: teks
 * #1A1A1A jadi nyaris tak terbaca di atas background gelap, dan Border/Surface
 * putih membentuk kotak menyilaukan. Struktur di bawah membuat nilai yang sama
 * (`AppColors.TextPrimary`, dst) otomatis menyesuaikan mode aktif, TANPA perlu
 * mengubah satu pun dari 43 file itu.
 *
 * Nilai mode gelap tidak dipilih dengan perkiraan — tiap warna diuji rasio
 * kontras WCAG terhadap background gelap. Yang gagal ambang (mis. TextSecondary
 * #6B7280 = 3.91, Danger #DC2626 = 3.91; ambang teks 4.5) diganti versi lebih
 * terang, bukan dipakai ulang begitu saja.
 */
@Immutable
data class JepretAjaColors(
    val primary: Color,
    val primaryDark: Color,
    val primarySoft: Color,
    val onPrimary: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    val exploreBackground: Color,
    val isLight: Boolean,
)

val LightAppColors = JepretAjaColors(
    primary = Color(0xFF2F6FED),
    primaryDark = Color(0xFF1E52C4),
    primarySoft = Color(0xFFEAF1FE),
    onPrimary = Color(0xFFFFFFFF),   // kontras 4.55 di atas primary — lolos
    secondary = Color(0xFF1F2937),
    accent = Color(0xFFFFC107),
    background = Color(0xFFF8F9FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F2F5),
    textPrimary = Color(0xFF1A1A1A),
    // Digelapkan dari #6B7280: warna lama hanya mencapai 4.32 di atas
    // surfaceVariant — di bawah ambang keterbacaan 4.5, padahal ini token
    // teks yang paling sering dipakai (58 tempat). Nilai baru lolos di
    // ketiga permukaan terang (5.09 / 5.36 / 4.79).
    textSecondary = Color(0xFF646B78),
    border = Color(0xFFE5E7EB),
    success = Color(0xFF16A34A),
    warning = Color(0xFFF59E0B),
    danger = Color(0xFFDC2626),
    info = Color(0xFF0891B2),
    exploreBackground = Color(0xFF000000),
    isLight = true,
)

val DarkAppColors = JepretAjaColors(
    // Biru dinaikkan terangnya: #2F6FED hanya 4.15 di atas background gelap,
    // #5B8DEF mencapai 5.85 sehingga tetap terbaca sebagai teks & ikon.
    primary = Color(0xFF5B8DEF),
    primaryDark = Color(0xFF4478DB),
    primarySoft = Color(0xFF1B2740),  // tint biru gelap, pengganti #EAF1FE
    // Di atas biru terang, teks PUTIH justru gagal (3.23) — maka teks gelap.
    onPrimary = Color(0xFF0F1115),
    secondary = Color(0xFFE5E7EB),
    accent = Color(0xFFFFCA28),
    background = Color(0xFF0F1115),
    surface = Color(0xFF171A20),
    surfaceVariant = Color(0xFF1F242C),
    textPrimary = Color(0xFFF2F4F7),
    textSecondary = Color(0xFF9BA3AF),  // 7.43 (yang lama cuma 3.91 — gagal)
    border = Color(0xFF2A2F38),
    success = Color(0xFF22C55E),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),         // 6.83 (yang lama cuma 3.91 — gagal)
    info = Color(0xFF22B8CF),
    exploreBackground = Color(0xFF000000), // feed sinematik: hitam di kedua mode
    isLight = false,
)

/**
 * Biru brand yang TIDAK ikut berubah mengikuti mode.
 *
 * Dipakai untuk permukaan identitas seperti splash screen, yang seharusnya
 * tampil sama persis di HP mode terang maupun gelap. Token `AppColors.Primary`
 * tidak cocok di sini karena di mode gelap ia sengaja dibuat lebih terang
 * (#5B8DEF), dan teks putih di atasnya hanya mencapai kontras 3.23 — gagal.
 */
val BrandBlue = Color(0xFF2F6FED)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

/**
 * Titik akses yang dipakai seluruh layar. Bentuk pemanggilannya sengaja
 * dipertahankan persis seperti sebelumnya (`AppColors.Primary`) supaya seluruh
 * kode lama tetap jalan, tapi nilainya kini dibaca dari tema aktif.
 */
object AppColors {
    val Primary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primary
    val PrimaryDark: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primaryDark
    val PrimarySoft: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.primarySoft
    val OnPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.onPrimary
    val Secondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.secondary
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.accent

    val Background: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surface
    val SurfaceVariant: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.surfaceVariant
    val TextPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textPrimary
    val TextSecondary: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.textSecondary
    val Border: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.border

    val Success: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.success
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.warning
    val Danger: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.danger
    val Info: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.info

    val ExploreBackground: Color @Composable @ReadOnlyComposable get() = LocalAppColors.current.exploreBackground
}
