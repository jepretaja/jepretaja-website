package com.jepretaja.app.core.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.jepretaja.app.data.model.BookingModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Isi satu slip, sudah dipisahkan dari model supaya penggambarnya tidak perlu
 * tahu dari mana angkanya berasal. */
data class IsiSlip(
    val nomorBooking: String,
    val status: String,
    val paket: String,
    val jadwal: String,
    val lokasi: String,
    val nomorPembayaran: String?,
    val waktuBayar: String?,
    val hargaPaket: Long?,
    val biayaPerjalanan: Long?,
    val diskon: Long?,
    val total: Long,
)

/**
 * Slip pembayaran sebagai gambar yang bisa diunduh dan dibagikan.
 *
 * Dibuat sebagai PNG, bukan PDF: slip ini paling sering dikirim lewat WhatsApp
 * ke creator atau ke admin saat menanyakan status pembayaran, dan gambar bisa
 * langsung terlihat di percakapan tanpa perlu diunduh dan dibuka pakai aplikasi
 * lain. PNG juga tidak menambah satu pun dependensi baru — Canvas sudah ada di
 * Android.
 *
 * Digambar sendiri, bukan menangkap tampilan layar: layar bisa terpotong,
 * mengikuti tema gelap, dan berubah bentuk mengikuti ukuran HP. Slip yang jadi
 * bukti sebaiknya selalu sama bentuknya, siapa pun yang membuatnya.
 */
object SlipPembayaran {

    private const val LEBAR = 1080
    private const val MARGIN = 72f
    private val FOLDER_UNDUHAN = "JepretAja"

    fun isiDari(booking: BookingModel, nomorPembayaran: String?, waktuBayarMillis: Long?): IsiSlip {
        val tanggal = booking.date?.toDate()?.let {
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale("in", "ID")).format(it)
        }
        return IsiSlip(
            nomorBooking = "#${booking.bookingId.take(8).uppercase()}",
            status = labelStatus(booking.status),
            paket = booking.packageName.ifBlank { "Paket pemotretan" },
            jadwal = listOfNotNull(tanggal, booking.time.ifBlank { null }).joinToString(" • ").ifBlank { "-" },
            lokasi = booking.location.ifBlank { "-" },
            nomorPembayaran = nomorPembayaran?.takeIf { it.isNotBlank() },
            waktuBayar = waktuBayarMillis?.let {
                SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("in", "ID")).format(Date(it))
            },
            hargaPaket = booking.priceBreakdown?.packagePrice,
            biayaPerjalanan = booking.priceBreakdown?.travelFee,
            diskon = booking.priceBreakdown?.discount?.takeIf { it > 0 },
            total = booking.total,
        )
    }

    private fun labelStatus(status: String): String = when (status) {
        "pending_payment" -> "MENUNGGU PEMBAYARAN"
        "paid" -> "SUDAH DIBAYAR"
        "cancelled" -> "DIBATALKAN"
        "refund_requested" -> "PENGEMBALIAN DIAJUKAN"
        "disputed" -> "SENGKETA"
        else -> "PEMBAYARAN TERVERIFIKASI"
    }

    // --- Penggambaran ------------------------------------------------------

    fun gambar(isi: IsiSlip): Bitmap {
        val judul = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 54f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subJudul = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D6E2FF"); textSize = 32f }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#6B7280"); textSize = 30f }
        val nilai = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111827"); textSize = 32f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val garis = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E5E7EB"); strokeWidth = 2f }
        val catatan = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9CA3AF"); textSize = 26f }

        val baris = buildList {
            add("Nomor Booking" to isi.nomorBooking)
            isi.nomorPembayaran?.let { add("Nomor Pembayaran" to it) }
            isi.waktuBayar?.let { add("Waktu" to it) }
            add("Paket" to isi.paket)
            add("Jadwal" to isi.jadwal)
            add("Lokasi" to isi.lokasi)
            add("Metode" to "Transfer Bank")
        }
        val rincian = buildList {
            isi.hargaPaket?.let { add("Harga Paket" to Formatters.currency(it)) }
            isi.biayaPerjalanan?.takeIf { it > 0 }?.let { add("Biaya Perjalanan" to Formatters.currency(it)) }
            isi.diskon?.let { add("Diskon" to "- ${Formatters.currency(it)}") }
        }

        val tinggiKepala = 260f
        val tinggiBaris = 86f
        // Tinggi dihitung dari isinya, bukan dipatok: slip dengan lokasi panjang
        // atau tanpa rincian harga tetap pas, tidak terpotong dan tidak
        // menyisakan ruang kosong lebar di bawah.
        val tinggi = (
            tinggiKepala + baris.size * tinggiBaris +
                (if (rincian.isEmpty()) 0f else 60f + rincian.size * tinggiBaris) +
                180f + 140f
            ).toInt()

        val bitmap = Bitmap.createBitmap(LEBAR, tinggi, Bitmap.Config.ARGB_8888)
        val kanvas = Canvas(bitmap)
        kanvas.drawColor(Color.WHITE)

        // Kepala
        kanvas.drawRect(0f, 0f, LEBAR.toFloat(), tinggiKepala, Paint().apply { color = Color.parseColor("#2563EB") })
        kanvas.drawText("JepretAja", MARGIN, 96f, judul)
        kanvas.drawText("Slip Pembayaran", MARGIN, 148f, subJudul)
        kanvas.drawText(isi.status, MARGIN, 206f, Paint(subJudul).apply {
            color = Color.WHITE; textSize = 28f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })

        var y = tinggiKepala + 70f
        baris.forEach { (kiri, kanan) ->
            kanvas.drawText(kiri, MARGIN, y, label)
            gambarKanan(kanvas, potong(kanan, nilai), nilai, y)
            y += tinggiBaris
        }

        if (rincian.isNotEmpty()) {
            y += 12f
            kanvas.drawLine(MARGIN, y, LEBAR - MARGIN, y, garis)
            y += 48f
            rincian.forEach { (kiri, kanan) ->
                kanvas.drawText(kiri, MARGIN, y, label)
                gambarKanan(kanvas, kanan, nilai, y)
                y += tinggiBaris
            }
        }

        y += 12f
        kanvas.drawLine(MARGIN, y, LEBAR - MARGIN, y, garis)
        y += 74f
        kanvas.drawText("TOTAL", MARGIN, y, Paint(label).apply {
            textSize = 34f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        gambarKanan(
            kanvas, Formatters.currency(isi.total),
            Paint(nilai).apply { textSize = 46f; color = Color.parseColor("#2563EB") }, y,
        )

        y += 90f
        kanvas.drawText("Slip ini dibuat otomatis oleh aplikasi JepretAja.", MARGIN, y, catatan)
        kanvas.drawText("Bukan bukti transfer bank — simpan juga struk dari bankmu.", MARGIN, y + 38f, catatan)
        return bitmap
    }

    private fun gambarKanan(kanvas: Canvas, teks: String, paint: Paint, y: Float) {
        kanvas.drawText(teks, LEBAR - MARGIN - paint.measureText(teks), y, paint)
    }

    /** Memendekkan teks yang tidak muat supaya tidak menabrak labelnya di kiri. */
    private fun potong(teks: String, paint: Paint): String {
        val maksimum = LEBAR - MARGIN * 2 - 320f
        if (paint.measureText(teks) <= maksimum) return teks
        var hasil = teks
        while (hasil.length > 4 && paint.measureText("$hasil…") > maksimum) {
            hasil = hasil.dropLast(1)
        }
        return "$hasil…"
    }

    // --- Menyimpan & membagikan --------------------------------------------

    private fun namaBerkas(isi: IsiSlip) =
        "slip-jepretaja-${isi.nomorBooking.removePrefix("#").lowercase()}-${System.currentTimeMillis()}.png"

    /**
     * Menyimpan ke galeri perangkat.
     *
     * Sejak Android 10 berkas ditulis lewat MediaStore dan sama sekali tidak
     * butuh izin — aplikasi hanya menulis ke koleksinya sendiri. Di bawah itu
     * belum ada scoped storage, jadi jalurnya berbeda dan izin
     * WRITE_EXTERNAL_STORAGE memang masih diperlukan; pemanggil yang memutuskan
     * kapan memintanya, karena hanya layar yang tahu bagaimana menjelaskannya.
     */
    suspend fun unduh(context: Context, isi: IsiSlip, bitmap: Bitmap): Uri = withContext(Dispatchers.IO) {
        val nama = namaBerkas(isi)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val nilai = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, nama)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER_UNDUHAN")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, nilai)
                ?: throw IOException("Galeri menolak menyimpan berkas.")
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                ?: throw IOException("Berkas slip gagal ditulis.")
            // IS_PENDING dilepas SETELAH isinya lengkap: tanpa langkah ini
            // berkasnya tetap tak terlihat di galeri selamanya.
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER_UNDUHAN)
            if (!dir.exists() && !dir.mkdirs()) throw IOException("Folder unduhan tidak bisa dibuat.")
            val berkas = File(dir, nama)
            berkas.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Tanpa pemindaian, berkasnya ada di kartu memori tapi tidak pernah
            // muncul di aplikasi Galeri — dari sisi pengguna itu sama saja
            // dengan gagal diunduh.
            MediaScannerConnection.scanFile(context, arrayOf(berkas.absolutePath), arrayOf("image/png"), null)
            Uri.fromFile(berkas)
        }
    }

    /**
     * Menyiapkan berkas untuk dibagikan lewat FileProvider.
     *
     * Sengaja ditulis ke cache, bukan ke galeri: membagikan seharusnya tidak
     * diam-diam menambah berkas ke galeri pengguna, dan sistem boleh membersihkan
     * cache sendiri saat ruang menipis.
     */
    suspend fun siapkanUntukBagikan(context: Context, isi: IsiSlip, bitmap: Bitmap): Uri =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "slip").apply { mkdirs() }
            val berkas = File(dir, namaBerkas(isi))
            berkas.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", berkas)
        }

    fun intentBagikan(isi: IsiSlip, uri: Uri): Intent {
        val kirim = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "Slip pembayaran JepretAja ${isi.nomorBooking} — ${isi.paket}, ${Formatters.currency(isi.total)}.",
            )
            // Wajib: tanpa ini aplikasi penerima tidak punya izin membaca URI
            // FileProvider dan hanya menampilkan "gambar tidak bisa dibuka".
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(kirim, "Bagikan slip pembayaran")
    }
}
