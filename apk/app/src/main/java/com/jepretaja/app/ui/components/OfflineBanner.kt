package com.jepretaja.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors
import kotlinx.coroutines.delay

/**
 * Pita pemberitahuan keadaan koneksi, ditempel di bawah status bar.
 *
 * Muncul saat internet putus dan TIDAK hilang sendiri selama masih putus —
 * pesan sekejap (toast/snackbar) mudah terlewat, padahal selama offline hampir
 * semua layar akan terlihat kosong dan pengguna perlu tahu alasannya.
 *
 * Saat koneksi kembali, pita berubah hijau "Kembali online" sebentar lalu
 * menutup sendiri. Tanpa penutup itu, pengguna tidak pernah mendapat kepastian
 * bahwa keadaan sudah pulih dan aman untuk mencoba lagi.
 */
@Composable
fun OfflineBanner(isOnline: Boolean, modifier: Modifier = Modifier) {
    // null = belum pernah tahu keadaan sebelumnya, jadi pemulihan tidak
    // ditampilkan saat aplikasi baru dibuka dalam keadaan online.
    var pernahOffline by rememberSaveable { mutableStateOf(false) }
    var tampilkanPulih by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            pernahOffline = true
            tampilkanPulih = false
        } else if (pernahOffline) {
            tampilkanPulih = true
            delay(2500)
            tampilkanPulih = false
        }
    }

    val terlihat = !isOnline || tampilkanPulih

    AnimatedVisibility(
        visible = terlihat,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val warna = if (isOnline) AppColors.Success else AppColors.Danger
        Row(
            Modifier
                .fillMaxWidth()
                .background(warna)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (isOnline) Icons.Default.CloudDone else Icons.Default.WifiOff,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isOnline) "Kembali online" else "Tidak ada koneksi internet",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
