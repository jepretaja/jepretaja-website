package com.jepretaja.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors

/**
 * Menu yang butuh akun dan sedang dibuka oleh tamu.
 *
 * Dibuat sebagai enum, bukan String bebas, supaya kalimat ajakannya ditulis
 * satu kali di sini: tiga menu ini butuh akun karena tiga alasan yang berbeda,
 * dan "Kamu harus login" yang seragam tidak menjelaskan apa pun kepada tamu
 * yang baru saja menekan tombolnya.
 */
enum class FiturButuhAkun(
    val judul: String,
    val alasan: String,
) {
    BOOKING(
        judul = "Booking butuh akun",
        alasan = "Pesanan melekat pada satu akun — itu yang dipakai creator " +
            "mengenali kamu, dan yang membuat riwayat serta pembayaranmu bisa " +
            "ditemukan lagi nanti.",
    ),
    CHAT(
        judul = "Chat butuh akun",
        alasan = "Percakapan di JepretAja selalu antara dua orang yang bisa " +
            "saling dikenali. Tanpa akun, tidak ada tempat untuk menyimpan " +
            "pesanmu dan creator tidak tahu harus membalas ke siapa.",
    ),
    NOTIFIKASI(
        judul = "Notifikasi butuh akun",
        alasan = "Notifikasi berisi kabar tentang pesanan, chat, dan karyamu " +
            "sendiri — semuanya menempel pada akun, jadi tidak ada yang bisa " +
            "ditampilkan untuk sesi tamu.",
    ),
}

/**
 * Ajakan masuk atau daftar saat tamu menekan menu yang butuh akun.
 *
 * Sengaja berupa lembar yang bisa ditutup, BUKAN pengalihan langsung ke halaman
 * masuk: tamu yang cuma penasaran menekan tombolnya berhak kembali ke tempat
 * semula tanpa merasa terperangkap, dan tab yang ia tekan pun tidak jadi
 * berpindah di belakang lembar ini.
 */
@Composable
fun LoginRequiredSheet(
    fitur: FiturButuhAkun,
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(AppColors.PrimarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = AppColors.Primary,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                fitur.judul,
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                fitur.alasan,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            BigPrimaryButton(text = "Masuk", onClick = onLogin)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRegister,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Primary),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text("Daftar Akun Baru", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onDismiss) {
                Text("Nanti saja", color = AppColors.TextSecondary)
            }
        }
    }
}
