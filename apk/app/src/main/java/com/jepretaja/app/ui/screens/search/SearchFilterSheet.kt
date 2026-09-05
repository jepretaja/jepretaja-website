package com.jepretaja.app.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.AppConstants
import com.jepretaja.app.core.util.Formatters

/** Bagian yang bisa dijadikan tujuan saat sheet dibuka dari chip tertentu. */
enum class BagianFilter { URUTKAN, HARGA, RATING, LAYANAN, VERIFIKASI }

private val presetHarga: List<Triple<String, Long?, Long?>> = listOf(
    Triple("< Rp1jt", null, 1_000_000L),
    Triple("Rp1jt – Rp3jt", 1_000_000L, 3_000_000L),
    Triple("Rp3jt – Rp5jt", 3_000_000L, 5_000_000L),
    Triple("Rp5jt – Rp10jt", 5_000_000L, 10_000_000L),
    Triple("> Rp10jt", 10_000_000L, null),
)

private val presetRating: List<Double> = listOf(3.0, 3.5, 4.0, 4.5)

/**
 * Satu sheet untuk seluruh penyaringan & pengurutan.
 *
 * Alasan memakai bottom sheet, bukan chip yang langsung mengubah hasil:
 * menyaring hampir tidak pernah satu keputusan. Pengguna yang menaikkan ambang
 * rating biasanya juga ingin melonggarkan batas harga di napas yang sama; kalau
 * setiap ketukan langsung menembak query, ia melihat daftar berkedip dan
 * sesekali kosong di tengah jalan padahal belum selesai memutuskan.
 *
 * Karena itu sheet ini bekerja di atas **salinan** ([draf]). Apa pun yang
 * disentuh di sini baru berlaku setelah "Terapkan"; menutup sheet lewat tombol
 * kembali atau menggeser ke bawah membatalkan seluruh perubahan — perilaku yang
 * dijanjikan tombol batal di mana pun, dan yang membuat pengguna berani
 * bereksperimen dengan filter.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterSheet(
    awal: SearchFilters,
    onDismiss: () -> Unit,
    onApply: (SearchFilters) -> Unit,
    fokus: BagianFilter? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draf by remember(awal) { mutableStateOf(awal) }

    // Teks harga disimpan terpisah dari nilainya. Kalau field dibaca langsung
    // dari `draf.minPrice`, menghapus satu digit terakhir akan mengubah nilai
    // jadi null dan field ikut kosong sendiri di bawah jari pengguna.
    var teksMin by remember(awal) { mutableStateOf(awal.minPrice?.toString().orEmpty()) }
    var teksMaks by remember(awal) { mutableStateOf(awal.maxPrice?.toString().orEmpty()) }

    val min = teksMin.toLongOrNull()
    val maks = teksMaks.toLongOrNull()
    val rentangTerbalik = min != null && maks != null && min > maks

    val scroll = rememberScrollState()
    val posisi = remember { mutableStateMapOf<BagianFilter, Int>() }

    // Tinggi area gulir diikat ke tinggi layar, bukan angka dp tetap. Nilai
    // tetap yang pas di HP 6 inci akan mendorong tombol Terapkan keluar layar
    // di perangkat kecil — dan tombol itu satu-satunya cara menerapkan filter.
    val tinggiMaksIsi = (LocalConfiguration.current.screenHeightDp * 0.55f).dp

    // Chip "Harga" membuka sheet yang bagian harganya sudah terlihat. Tanpa ini
    // pengguna menekan chip bernama Harga lalu harus mencari sendiri bagian
    // harga di dalam sheet — ketukan yang menjanjikan sesuatu lalu menyerahkan
    // pekerjaannya kembali.
    LaunchedEffect(fokus, posisi.size) {
        val y = fokus?.let { posisi[it] } ?: return@LaunchedEffect
        if (y > 0) scroll.animateScrollTo(y)
    }

    // Penjagaan `!=` di sini bukan optimasi kecil: onGloballyPositioned dipanggil
    // ulang setiap kali layout diukur, dan menulis nilai yang sama ke
    // SnapshotStateMap tetap dihitung sebagai perubahan state. Tanpa penjagaan
    // itu, tulis -> recompose -> ukur -> tulis lagi berputar tanpa henti.
    fun Modifier.tandai(bagian: BagianFilter) = this.onGloballyPositioned { koordinat ->
        val y = koordinat.positionInRoot().y.toInt()
        if (posisi[bagian] != y) posisi[bagian] = y
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(Modifier.fillMaxWidth()) {

            // ---- Kepala: judul + reset ----------------------------------
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Filter & Urutkan", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
                    Text(
                        if (draf.adaFilterAktif) "${draf.jumlahFilterAktif} filter aktif" else "Belum ada filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                    )
                }
                // Reset hanya hidup kalau memang ada yang bisa direset — tombol
                // yang selalu bisa ditekan tapi kadang tidak melakukan apa-apa
                // membuat pengguna ragu apakah ketukannya terbaca.
                TextButton(
                    onClick = { draf = draf.direset(); teksMin = ""; teksMaks = "" },
                    enabled = draf.adaFilterAktif,
                ) { Text("Reset") }
            }
            HorizontalDivider(color = AppColors.Border)

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = tinggiMaksIsi)
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                // ---- Urutkan --------------------------------------------
                JudulBagian("Urutkan", Icons.Default.Sort, Modifier.tandai(BagianFilter.URUTKAN))
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SearchSort.entries.forEach { urutan ->
                        ChipPilihan(
                            label = urutan.label,
                            terpilih = draf.sort == urutan,
                            onClick = { draf = draf.copy(sort = urutan) },
                            ikon = if (urutan == SearchSort.TERDEKAT) Icons.Default.NearMe else null,
                        )
                    }
                }
                if (draf.sort == SearchSort.TERDEKAT) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Butuh izin lokasi. Creator yang belum mengisi area layanan tidak muncul di urutan ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                    )
                }

                PemisahBagian()

                // ---- Harga ----------------------------------------------
                JudulBagian("Rentang Harga", Icons.Default.AttachMoney, Modifier.tandai(BagianFilter.HARGA))
                Text(
                    "Dihitung dari paket termurah tiap creator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    presetHarga.forEach { (label, bawah, atas) ->
                        val terpilih = draf.minPrice == bawah && draf.maxPrice == atas
                        ChipPilihan(
                            label = label,
                            terpilih = terpilih,
                            onClick = {
                                // Menekan preset yang sedang aktif membatalkannya.
                                val kosongkan = terpilih
                                draf = draf.copy(
                                    minPrice = if (kosongkan) null else bawah,
                                    maxPrice = if (kosongkan) null else atas,
                                )
                                teksMin = if (kosongkan) "" else bawah?.toString().orEmpty()
                                teksMaks = if (kosongkan) "" else atas?.toString().orEmpty()
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KolomHarga(
                        nilai = teksMin,
                        label = "Minimum",
                        galat = rentangTerbalik,
                        modifier = Modifier.weight(1f),
                        onChange = { baru ->
                            teksMin = baru
                            draf = draf.copy(minPrice = baru.toLongOrNull())
                        },
                    )
                    KolomHarga(
                        nilai = teksMaks,
                        label = "Maksimum",
                        galat = rentangTerbalik,
                        modifier = Modifier.weight(1f),
                        onChange = { baru ->
                            teksMaks = baru
                            draf = draf.copy(maxPrice = baru.toLongOrNull())
                        },
                    )
                }
                if (rentangTerbalik) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Harga minimum lebih besar dari maksimum — tidak akan ada hasil yang cocok.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Danger,
                    )
                }

                PemisahBagian()

                // ---- Rating ---------------------------------------------
                JudulBagian("Rating Minimum", Icons.Default.Star, Modifier.tandai(BagianFilter.RATING))
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChipPilihan(
                        label = "Semua",
                        terpilih = draf.minRating == null,
                        onClick = { draf = draf.copy(minRating = null) },
                    )
                    presetRating.forEach { ambang ->
                        ChipPilihan(
                            label = "${formatRating(ambang)}+",
                            terpilih = draf.minRating == ambang,
                            onClick = { draf = draf.copy(minRating = if (draf.minRating == ambang) null else ambang) },
                            ikon = Icons.Default.Star,
                        )
                    }
                }

                PemisahBagian()

                // ---- Jenis layanan --------------------------------------
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.tandai(BagianFilter.LAYANAN)) {
                    JudulBagian("Jenis Layanan", Icons.Default.Category, Modifier.weight(1f))
                    if (draf.layananAktif) {
                        TextButton(onClick = { draf = draf.copy(categories = emptyList()) }) { Text("Bersihkan") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppConstants.SERVICE_CATEGORIES.forEach { kategori ->
                        val terpilih = kategori in draf.categories
                        ChipPilihan(
                            label = kategori,
                            terpilih = terpilih,
                            onClick = {
                                draf = draf.copy(
                                    categories = if (terpilih) draf.categories - kategori else draf.categories + kategori,
                                )
                            },
                        )
                    }
                }

                PemisahBagian()

                // ---- Terverifikasi --------------------------------------
                Row(
                    Modifier.fillMaxWidth().tandai(BagianFilter.VERIFIKASI),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = AppColors.Info, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Hanya Terverifikasi", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                        Text(
                            "Identitas dan portofolionya sudah dicek tim JepretAja.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                        )
                    }
                    Switch(
                        checked = draf.verifiedOnly,
                        onCheckedChange = { draf = draf.copy(verifiedOnly = it) },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // ---- Kaki: reset + terapkan ---------------------------------
            HorizontalDivider(color = AppColors.Border)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(AppColors.Surface)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { draf = draf.direset(); teksMin = ""; teksMaks = "" },
                    enabled = draf.adaFilterAktif,
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Reset") }
                Button(
                    onClick = { onApply(draf) },
                    enabled = !rentangTerbalik,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                    modifier = Modifier.weight(1.6f).height(52.dp),
                ) {
                    Text(
                        if (draf.adaFilterAktif) "Terapkan (${draf.jumlahFilterAktif})" else "Terapkan",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun JudulBagian(teks: String, ikon: ImageVector, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(ikon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(teks, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
    }
}

@Composable
private fun PemisahBagian() {
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = AppColors.Border)
    Spacer(Modifier.height(18.dp))
}

/**
 * Chip pilihan dengan tanda centang saat aktif.
 *
 * Centangnya bukan hiasan: FilterChip Material membedakan terpilih dan tidak
 * hanya lewat warna latar yang tipis bedanya, dan itu satu-satunya penanda bagi
 * pengguna yang kesulitan membedakan warna.
 */
@Composable
private fun ChipPilihan(
    label: String,
    terpilih: Boolean,
    onClick: () -> Unit,
    ikon: ImageVector? = null,
) {
    val leading: (@Composable () -> Unit)? = when {
        terpilih -> { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } }
        ikon != null -> { { Icon(ikon, contentDescription = null, modifier = Modifier.size(16.dp)) } }
        else -> null
    }
    FilterChip(
        selected = terpilih,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(percent = 50),
        leadingIcon = leading,
    )
}

@Composable
private fun KolomHarga(
    nilai: String,
    label: String,
    galat: Boolean,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = nilai,
        // Hanya digit. "Rp 1.500.000" yang diketik apa adanya akan gagal
        // dibaca `toLongOrNull()` dan diam-diam berlaku seperti tanpa batas.
        onValueChange = { baru -> onChange(baru.filter { it.isDigit() }.take(12)) },
        label = { Text(label) },
        prefix = { Text("Rp", color = AppColors.TextSecondary) },
        placeholder = { Text("bebas") },
        singleLine = true,
        isError = galat,
        supportingText = {
            nilai.toLongOrNull()?.let {
                Text(Formatters.currency(it), style = MaterialTheme.typography.bodySmall)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    )
}
