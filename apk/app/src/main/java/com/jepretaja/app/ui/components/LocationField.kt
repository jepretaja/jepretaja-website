package com.jepretaja.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.GeocoderHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Kolom alamat dengan DUA cara pengisian sekaligus.
 *
 * Sebelumnya lokasi acara hanya kolom teks kosong — pengguna harus mengetik
 * sendiri alamat lengkapnya, yang di ponsel gampang salah ketik dan sering
 * berakhir tidak jelas ("rumah", "di jakarta"). Padahal aplikasi sudah punya
 * izin lokasi dan pustaka Play Services untuk mengambilnya sendiri.
 *
 * Otomatis TIDAK menggantikan manual, hanya mendahuluinya: hasil deteksi tetap
 * masuk ke kolom yang bisa disunting. Itu penting karena alamat hasil GPS
 * berhenti di tingkat jalan — nomor rumah, patokan, atau nama gedung tetap
 * harus ditambahkan orangnya. Lokasi acara juga belum tentu tempat pengguna
 * berdiri saat memesan.
 */
@Composable
fun LocationField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Lokasi acara",
    placeholder: String = "Ketik alamat, atau ambil otomatis",
    isError: Boolean = false,
    errorText: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mencari by remember { mutableStateOf(false) }
    var pesan by remember { mutableStateOf<String?>(null) }

    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun ambilLokasi() {
        scope.launch {
            mencari = true
            pesan = null
            val hasil = runCatching {
                // getCurrentLocation, bukan lastLocation: lastLocation bisa
                // mengembalikan posisi basi dari jam-jam sebelumnya (atau null
                // di perangkat yang baru menyala), sehingga alamat yang terisi
                // bisa jauh dari tempat pengguna sekarang tanpa tanda apa pun.
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            }.getOrNull()

            if (hasil == null) {
                mencari = false
                pesan = "Lokasi tidak terbaca. Pastikan GPS menyala, atau ketik manual."
                return@launch
            }

            val alamat = GeocoderHelper.alamatDari(context, hasil.latitude, hasil.longitude)
            mencari = false
            if (alamat.isNullOrBlank()) {
                pesan = "Alamat tidak ditemukan. Silakan ketik manual."
            } else {
                onValueChange(alamat)
                pesan = "Alamat terisi otomatis — lengkapi nomor/patokan bila perlu."
            }
        }
    }

    val peminta = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { diizinkan ->
        if (diizinkan) ambilLokasi()
        else pesan = "Izin lokasi ditolak. Alamat bisa diketik manual."
    }

    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            shape = MaterialTheme.shapes.medium,
            minLines = 2,
            isError = isError,
            // Pesan galat menempel di kolomnya sendiri, bukan menumpuk di dasar
            // halaman. Pengguna tidak perlu mencocokkan pesan dengan kolom mana
            // yang dimaksud.
            supportingText = errorText?.let { pesan -> { Text(pesan) } },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Hapus alamat")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val sudahIzin = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                if (sudahIzin) ambilLokasi() else peminta.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            enabled = !mencari,
            shape = RoundedCornerShape(percent = 50),
            modifier = Modifier.height(42.dp),
        ) {
            if (mencari) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Mencari lokasi...", style = MaterialTheme.typography.labelLarge)
            } else {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Gunakan lokasi saya", style = MaterialTheme.typography.labelLarge)
            }
        }

        pesan?.let {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
        }
    }
}
