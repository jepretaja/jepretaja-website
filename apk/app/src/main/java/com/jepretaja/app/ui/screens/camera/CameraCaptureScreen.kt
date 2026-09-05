package com.jepretaja.app.ui.screens.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.jepretaja.app.core.util.AppConstants
import com.jepretaja.app.core.util.MediaInfo
import kotlinx.coroutines.delay
import java.util.concurrent.Executor

/**
 * Kamera di dalam aplikasi (CameraX), bukan aplikasi kamera bawaan.
 *
 * Bedanya bukan sekadar tampilan: dengan CameraX, aplikasi yang menentukan
 * kualitas rekaman, batas durasi, dan kamera mana yang aktif. Lewat intent ke
 * aplikasi kamera bawaan, ketiganya diserahkan ke aplikasi pihak lain yang
 * berbeda-beda di tiap merek ponsel — termasuk kemungkinan menghasilkan video
 * 10 menit 4K yang pasti ditolak batas unggah.
 *
 * Perekaman memakai tombol tekan-mulai/tekan-berhenti dengan penghitung waktu,
 * bukan tahan-untuk-merekam. Tahan-untuk-merekam menuntut penanganan gestur
 * yang bertabrakan dengan tombol ganti kamera dan mudah terputus tidak sengaja;
 * mulai/berhenti eksplisit lebih sulit salah pakai untuk sesi pemotretan.
 */
@Composable
fun CameraCaptureScreen(
    onCaptured: (Uri, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor: Executor = remember { ContextCompat.getMainExecutor(context) }

    var punyaIzin by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val mintaIzin = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { hasil ->
        punyaIzin = hasil[Manifest.permission.CAMERA] == true
    }
    LaunchedEffect(Unit) {
        if (!punyaIzin) {
            mintaIzin.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    var modeVideo by remember { mutableStateOf(false) }
    var kameraDepan by remember { mutableStateOf(false) }
    var merekam by remember { mutableStateOf(false) }
    var detik by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val videoCapture = remember {
        VideoCapture.withOutput(
            Recorder.Builder()
                // HD, bukan kualitas tertinggi: 4K menghasilkan berkas yang
                // hampir pasti melewati batas unggah dan tidak menambah apa pun
                // pada feed yang ditonton di layar ponsel.
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
        )
    }
    var recording by remember { mutableStateOf<Recording?>(null) }

    // Kamera diikat ulang setiap izin diberikan atau lensa dibalik.
    LaunchedEffect(punyaIzin, kameraDepan) {
        if (!punyaIzin) return@LaunchedEffect
        val provider = ProcessCameraProvider.getInstance(context).get()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val selector = if (kameraDepan) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture, videoCapture)
        }.onFailure { error = "Kamera tidak bisa dibuka: ${it.message}" }
    }

    // Penghitung durasi sekaligus penegak batas: rekaman berhenti sendiri di
    // batas maksimal, supaya creator tidak baru tahu kelewatan setelah selesai.
    LaunchedEffect(merekam) {
        detik = 0
        while (merekam) {
            delay(1000)
            detik += 1
            if (detik >= AppConstants.MAX_VIDEO_DURATION_SECONDS) {
                recording?.stop()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!punyaIzin) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Izin kamera dibutuhkan", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Beri izin kamera dan mikrofon untuk merekam langsung dari aplikasi.",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    mintaIzin.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                }) { Text("Beri Izin") }
            }
        } else {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }

        // Baris atas: tutup, penghitung durasi, ganti kamera.
        Row(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { recording?.stop(); onClose() }) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            if (merekam) {
                Box(
                    Modifier.clip(RoundedCornerShape(percent = 50)).background(Color(0xFFEF5350))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        "%d:%02d".format(detik / 60, detik % 60),
                        color = Color.White,
                        fontWeight = FontWeight.W700,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(enabled = !merekam, onClick = { kameraDepan = !kameraDepan }) {
                Icon(Icons.Default.Cameraswitch, contentDescription = "Balik kamera", tint = Color.White)
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            error?.let {
                Text(it, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
            }
            if (!merekam) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(false to "Foto", true to "Video").forEach { (nilai, label) ->
                        val aktif = modeVideo == nilai
                        Box(
                            Modifier.clip(RoundedCornerShape(percent = 50))
                                .background(if (aktif) Color.White else Color.White.copy(alpha = 0.2f))
                                .clickable { modeVideo = nilai }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Text(
                                label,
                                color = if (aktif) Color.Black else Color.White,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            Box(
                Modifier.size(74.dp).clip(CircleShape)
                    .background(if (merekam) Color(0xFFEF5350) else Color.White.copy(alpha = 0.25f))
                    .border(4.dp, Color.White, CircleShape)
                    .clickable(enabled = punyaIzin) {
                        if (modeVideo) {
                            if (merekam) {
                                recording?.stop()
                            } else {
                                val nama = "rekaman_${System.currentTimeMillis()}.mp4"
                                val target = MediaInfo.fileTangkapan(context, nama)
                                val opsi = androidx.camera.video.FileOutputOptions.Builder(target).build()
                                recording = videoCapture.output
                                    .prepareRecording(context, opsi)
                                    .apply {
                                        if (ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO,
                                            ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                            withAudioEnabled()
                                        }
                                    }
                                    .start(executor) { peristiwa ->
                                        when (peristiwa) {
                                            is VideoRecordEvent.Start -> merekam = true
                                            is VideoRecordEvent.Finalize -> {
                                                merekam = false
                                                recording = null
                                                if (peristiwa.hasError()) {
                                                    error = "Rekaman gagal (kode ${peristiwa.error})."
                                                } else {
                                                    onCaptured(Uri.fromFile(target), true)
                                                }
                                            }
                                        }
                                    }
                            }
                        } else {
                            val target = MediaInfo.fileTangkapan(context, "jepretan_${System.currentTimeMillis()}.jpg")
                            val opsi = ImageCapture.OutputFileOptions.Builder(target).build()
                            imageCapture.takePicture(
                                opsi, executor,
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(hasil: ImageCapture.OutputFileResults) {
                                        onCaptured(Uri.fromFile(target), false)
                                    }

                                    override fun onError(e: ImageCaptureException) {
                                        error = "Gagal menyimpan foto: ${e.message}"
                                    }
                                },
                            )
                        }
                    },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    merekam -> "Ketuk untuk berhenti"
                    modeVideo -> "Maks ${AppConstants.MAX_VIDEO_DURATION_SECONDS / 60} menit"
                    else -> "Ketuk untuk memotret"
                },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
