package com.jepretaja.app.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private fun schemeFrom(c: JepretAjaColors) = if (c.isLight) {
    lightColorScheme(
        primary = c.primary,
        onPrimary = c.onPrimary,
        secondary = c.secondary,
        background = c.background,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.textSecondary,
        outline = c.border,
        error = c.danger,
    )
} else {
    darkColorScheme(
        primary = c.primary,
        onPrimary = c.onPrimary,
        secondary = c.secondary,
        background = c.background,
        onBackground = c.textPrimary,
        surface = c.surface,
        onSurface = c.textPrimary,
        surfaceVariant = c.surfaceVariant,
        onSurfaceVariant = c.textSecondary,
        outline = c.border,
        error = c.danger,
    )
}

/** Skala radius sudut yang disengaja & konsisten — sebelumnya nilai radius
 * ditulis ad hoc per layar (4dp s/d 24dp campur-campur). Komponen Material3
 * standar (Card, TextField, Sheet, Dialog, Chip) otomatis memakai skala ini
 * begitu di-set di MaterialTheme, tanpa perlu menyentuh tiap layar. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Mengikuti setelan terang/gelap HP.
 *
 * Sebelumnya fungsi ini MENERIMA parameter `darkTheme` tapi tidak pernah
 * memakainya — nilainya dihitung lalu dibuang, dan skema terang selalu dipaksa.
 * Akibatnya di HP bermode gelap aplikasi tetap putih menyilaukan, tidak nyambung
 * dengan sistem.
 */
@Composable
fun JepretAjaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = schemeFrom(appColors),
            typography = AppTypography,
            shapes = AppShapes,
        ) {
            // MaterialTheme() hanya menyediakan nilai warna lewat
            // CompositionLocal, TIDAK mengecat apa pun. Tanpa Surface ini
            // Compose sepenuhnya bergantung pada android:windowBackground milik
            // Activity — itu sebabnya background bisa "nyangkut" salah warna
            // di semua layar sekaligus.
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}
