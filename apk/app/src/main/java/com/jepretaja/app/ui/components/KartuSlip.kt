package com.jepretaja.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.IsiSlip
import com.jepretaja.app.core.util.SlipPembayaran
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Slip pembayaran, ditampilkan sebagai gambar sungguhan lalu diunduh/dibagikan.
 *
 * Sebelumnya slip hanya berupa dua tombol, "Unduh" dan "Bagikan". Pengguna
 * diminta menyimpan atau mengirim sesuatu yang belum pernah ia lihat — dan
 * satu-satunya cara memeriksa apakah isinya benar adalah menyimpannya dulu ke
 * galeri lalu membuka aplikasi lain. Untuk berkas yang dipakai sebagai bukti,
 * pratinjau bukan hiasan: nomor booking dan nominalnya perlu bisa dibaca
 * sebelum dikirim ke orang lain.
 *
 * Bitmap 1080 piksel digambar di [Dispatchers.Default] — di HP kelas bawah,
 * menggambarnya di benang utama cukup untuk membuat layar tersendat.
 */
@Composable
fun KartuSlipPembayaran(
    isi: IsiSlip,
    modifier: Modifier = Modifier,
    onPesan: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember(isi) { mutableStateOf<Bitmap?>(null) }
    var sibuk by remember { mutableStateOf(false) }
    var aksiTertunda by remember { mutableStateOf<AksiSlip?>(null) }

    LaunchedEffect(isi) {
        bitmap = runCatching { withContext(Dispatchers.Default) { SlipPembayaran.gambar(isi) } }.getOrNull()
    }

    fun jalankan(aksi: AksiSlip) {
        val gambar = bitmap ?: return
        scope.launch {
            sibuk = true
            val hasil = runCatching {
                when (aksi) {
                    AksiSlip.UNDUH -> {
                        SlipPembayaran.unduh(context, isi, gambar)
                        "Slip disimpan ke galeri, folder JepretAja."
                    }
                    AksiSlip.BAGIKAN -> {
                        val uri = SlipPembayaran.siapkanUntukBagikan(context, isi, gambar)
                        context.startActivity(SlipPembayaran.intentBagikan(isi, uri))
                        null
                    }
                }
            }
            sibuk = false
            hasil
                .onSuccess { pesan -> pesan?.let(onPesan) }
                .onFailure { onPesan(it.message ?: "Slip gagal dibuat.") }
        }
    }

    // Android 10 ke atas menulis ke galeri lewat MediaStore tanpa izin apa pun;
    // yang di bawahnya masih butuh WRITE_EXTERNAL_STORAGE.
    val izinSimpan = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { diberi ->
        val tertunda = aksiTertunda
        aksiTertunda = null
        if (diberi && tertunda != null) jalankan(tertunda)
        else if (!diberi) onPesan("Izin penyimpanan ditolak. Slip masih bisa dibagikan langsung.")
    }

    fun minta(aksi: AksiSlip) {
        val perluIzin = aksi == AksiSlip.UNDUH &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        if (perluIzin) {
            aksiTertunda = aksi
            izinSimpan.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            jalankan(aksi)
        }
    }

    PremiumCard(modifier = modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ReceiptLong, contentDescription = null,
                tint = AppColors.Primary, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Slip Pembayaran", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                Text(
                    "Simpan ke galeri atau kirim ke creator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.SurfaceVariant)
                .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val gambar = bitmap
            if (gambar == null) {
                // Rasio kotaknya dikunci sejak awal supaya kartu tidak melonjak
                // tingginya begitu slip selesai digambar.
                Box(Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp, color = AppColors.Primary)
                }
            } else {
                Image(
                    bitmap = gambar.asImageBitmap(),
                    contentDescription = "Pratinjau slip pembayaran ${isi.nomorBooking}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { minta(AksiSlip.UNDUH) },
                enabled = bitmap != null && !sibuk,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Unduh")
            }
            OutlinedButton(
                onClick = { minta(AksiSlip.BAGIKAN) },
                enabled = bitmap != null && !sibuk,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Bagikan")
            }
        }
    }
}

private enum class AksiSlip { UNDUH, BAGIKAN }
