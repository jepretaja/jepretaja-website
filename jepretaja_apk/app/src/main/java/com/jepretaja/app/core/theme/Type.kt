package com.jepretaja.app.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jepretaja.app.R

// Dua peran font yang disengaja (bukan Roboto polos di semua tempat):
// - Display: Fraunces — serif editorial premium (dipakai New York Times
//   Cooking, Notion, dll untuk kesan majalah/portfolio). SEBELUMNYA memakai
//   FontFamily.Serif bawaan Android, yang di kebanyakan perangkat cuma
//   fallback ke "Noto Serif" generik — jauh dari kesan editorial yang
//   dimaksud komentar aslinya. Diganti font sungguhan yang di-bundle di
//   res/font/ (Google Fonts, lisensi OFL, lihat FRAUNCES_FONT_LICENSE.txt),
//   opsz+WONK dikunci supaya versinya konsisten & tegas — bukan varian
//   "wonky"-nya Fraunces yang lebih playful.
// - Body: sans default sistem — tetap sangat mudah dibaca di ukuran kecil (form, list, angka).
private val DisplayFont = FontFamily(
    Font(R.font.fraunces_semibold, FontWeight.W600),
    Font(R.font.fraunces_bold, FontWeight.W700),
)
private val BodyFont = FontFamily.Default

/** Skala tipografi lengkap (13 role) — sebelumnya hanya 5 role yang
 * dikustomisasi, sisanya diam-diam jatuh ke default Material3 sehingga
 * tampilan tidak konsisten antar layar. */
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.3).sp),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 32.sp, lineHeight = 38.sp),
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 26.sp, lineHeight = 32.sp),

    headlineLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.W700, fontSize = 24.sp, lineHeight = 30.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.W600, fontSize = 21.sp, lineHeight = 27.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.W600, fontSize = 19.sp, lineHeight = 25.sp),

    titleLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.W800, fontSize = 21.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.W700, fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.W700, fontSize = 15.sp, lineHeight = 21.sp),

    bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.W400, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.W400, fontSize = 14.5.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.W400, fontSize = 13.sp, lineHeight = 18.sp),

    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.W700, fontSize = 14.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.W600, fontSize = 12.5.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.W600, fontSize = 11.5.sp, letterSpacing = 0.3.sp),
)
