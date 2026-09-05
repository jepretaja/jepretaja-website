package com.jepretaja.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR dari sebuah teks (di sini: tautan profil).
 *
 * Matriksnya dihitung zxing lalu digambar sendiri ke Bitmap. Ukurannya dipatok
 * pada jumlah modul QR, bukan pada piksel layar, supaya tiap modul jatuh persis
 * di batas piksel — QR yang modulnya "setengah piksel" sering gagal dipindai.
 */
@Composable
fun QrCode(
    content: String,
    modifier: Modifier = Modifier,
    foreground: Color = Color.Black,
    background: Color = Color.White,
) {
    val bitmap: ImageBitmap? = remember(content, foreground, background) {
        runCatching {
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
            val lebar = matrix.width
            val tinggi = matrix.height
            val fg = foreground.toArgb()
            val bg = background.toArgb()
            val piksel = IntArray(lebar * tinggi)
            for (y in 0 until tinggi) {
                for (x in 0 until lebar) {
                    piksel[y * lebar + x] = if (matrix.get(x, y)) fg else bg
                }
            }
            Bitmap.createBitmap(piksel, lebar, tinggi, Bitmap.Config.ARGB_8888).asImageBitmap()
        }.getOrNull()
    }

    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "Kode QR profil",
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}
