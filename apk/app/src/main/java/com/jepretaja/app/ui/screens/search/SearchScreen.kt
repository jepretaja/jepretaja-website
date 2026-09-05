package com.jepretaja.app.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.components.SectionHeader

/**
 * Search & Discovery.
 *
 * Yang diperbaiki di layar ini:
 *
 * 1. **Chip filter sekarang benar-benar bekerja.** Sebelumnya "Harga", "Rating",
 *    "Jenis Layanan", dan "Terverifikasi" adalah chip dengan `onClick = {}` —
 *    terlihat seperti tombol, terasa seperti tombol, tapi tidak melakukan apa
 *    pun. Kontrol yang berbohong soal apa yang bisa dilakukannya lebih buruk
 *    daripada tidak ada kontrol sama sekali: pengguna mengira ia sudah menyaring
 *    hasil, lalu menilai hasilnya seolah filternya sudah berlaku.
 * 2. **Chip menampilkan nilainya, bukan cuma namanya.** "Harga" berubah jadi
 *    "Rp1jt – Rp3jt" begitu diisi, jadi keadaan filter terbaca tanpa harus
 *    membuka sheet untuk mengingat apa yang pernah dipilih.
 * 3. **Reset selalu terlihat saat ada yang bisa direset**, di baris ringkasan
 *    tepat di bawah chip.
 * 4. **Isi layar bisa digulir dan tombol "Lihat Hasil" menempel di bawah.**
 *    Sebelumnya Column-nya tidak bisa digulir sama sekali: di HP kecil, dengan
 *    riwayat pencarian dan daftar creator populer terisi, tombol utamanya
 *    terdorong keluar layar dan tidak ada cara mencapainya.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun SearchScreen(
    onBack: () -> Unit,
    onSeeResults: (SearchFilters) -> Unit,
    onNearby: () -> Unit,
    onTagClick: (String) -> Unit = {},
    onCreatorClick: (String) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(SearchFilters()) }
    var sheetFokus by remember { mutableStateOf<BagianFilter?>(null) }
    var sheetTerbuka by remember { mutableStateOf(false) }

    val history by viewModel.history.collectAsState()
    val tagarNaik by viewModel.tagarNaik.collectAsState()
    val creatorNaik by viewModel.creatorNaik.collectAsState()

    // Satu jalur untuk semua cara memulai pencarian (tombol, Enter di papan
    // ketik, ketukan pada riwayat) supaya riwayatnya tidak pernah lupa tercatat
    // di salah satu jalur.
    fun cari(kataKunci: String, filterAktif: SearchFilters = filters) {
        val bersih = kataKunci.trim()
        if (bersih.isNotEmpty()) viewModel.remember(bersih)
        onSeeResults(filterAktif.copy(query = bersih.ifBlank { null }))
    }

    fun bukaSheet(bagian: BagianFilter?) {
        sheetFokus = bagian
        sheetTerbuka = true
    }

    val pill = RoundedCornerShape(percent = 50)

    Scaffold(
        topBar = { AppTopBar(title = "Cari", onBack = onBack) },
        bottomBar = {
            Surface(color = AppColors.Background, tonalElevation = 0.dp) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    BigPrimaryButton(
                        text = if (filters.adaFilterAktif) {
                            "Lihat Hasil (${filters.jumlahFilterAktif} filter)"
                        } else {
                            "Lihat Hasil"
                        },
                        onClick = { cari(query) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                query, { query = it },
                placeholder = { Text("Cari creator, kategori, kota...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Kosongkan pencarian")
                        }
                    }
                },
                singleLine = true,
                shape = pill,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { cari(query) }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(14.dp))

            // ---- Baris kontrol: urutan + filter --------------------------
            // Diletakkan langsung di bawah kotak pencarian, bukan di dasar
            // halaman seperti sebelumnya. Menyaring adalah keputusan yang
            // diambil bersamaan dengan mengetik kata kunci, bukan setelah
            // menggulir melewati seluruh konten penemuan.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ChipFilter(
                        label = filters.sort.label,
                        aktif = filters.sort != SearchSort.RELEVAN,
                        ikon = Icons.Default.Tune,
                        onClick = { bukaSheet(BagianFilter.URUTKAN) },
                        adaMenu = true,
                    )
                }
                item {
                    ChipFilter(
                        label = if (filters.adaFilterAktif) "Filter · ${filters.jumlahFilterAktif}" else "Filter",
                        aktif = filters.adaFilterAktif,
                        ikon = Icons.Default.Tune,
                        onClick = { bukaSheet(null) },
                    )
                }
                item {
                    ChipFilter(
                        label = filters.ringkasanHarga() ?: "Harga",
                        aktif = filters.hargaAktif,
                        onClick = { bukaSheet(BagianFilter.HARGA) },
                        adaMenu = true,
                    )
                }
                item {
                    ChipFilter(
                        label = filters.ringkasanRating() ?: "Rating",
                        aktif = filters.ratingAktif,
                        ikon = Icons.Default.Star,
                        onClick = { bukaSheet(BagianFilter.RATING) },
                        adaMenu = true,
                    )
                }
                item {
                    ChipFilter(
                        label = filters.ringkasanLayanan() ?: "Jenis Layanan",
                        aktif = filters.layananAktif,
                        onClick = { bukaSheet(BagianFilter.LAYANAN) },
                        adaMenu = true,
                    )
                }
                item {
                    // Terverifikasi tidak punya apa pun untuk diatur — ia
                    // hidup atau mati. Membuka sheet untuk satu saklar berarti
                    // tiga ketukan mengganti satu.
                    ChipFilter(
                        label = "Terverifikasi",
                        aktif = filters.verifiedOnly,
                        ikon = Icons.Default.Verified,
                        onClick = { filters = filters.copy(verifiedOnly = !filters.verifiedOnly) },
                    )
                }
                item {
                    AssistChip(
                        onClick = onNearby,
                        label = { Text("Nearby") },
                        shape = pill,
                        leadingIcon = { Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }

            // ---- Ringkasan filter aktif + reset --------------------------
            if (filters.adaFilterAktif) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    filters.ringkasanHarga()?.let {
                        ChipHapus(it) { filters = filters.copy(minPrice = null, maxPrice = null) }
                    }
                    filters.ringkasanRating()?.let {
                        ChipHapus("Rating $it") { filters = filters.copy(minRating = null) }
                    }
                    filters.categories.forEach { kategori ->
                        ChipHapus(kategori) { filters = filters.copy(categories = filters.categories - kategori) }
                    }
                    if (filters.verifiedOnly) {
                        ChipHapus("Terverifikasi") { filters = filters.copy(verifiedOnly = false) }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    TextButton(onClick = { filters = filters.direset() }) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reset semua filter")
                    }
                }
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Pencarian Terakhir",
                        style = MaterialTheme.typography.titleSmall,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.clear() }) { Text("Hapus semua") }
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    history.forEach { kata ->
                        InputChip(
                            selected = false,
                            onClick = { query = kata; cari(kata) },
                            label = { Text(kata) },
                            shape = pill,
                            leadingIcon = {
                                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Hapus $kata dari riwayat",
                                    modifier = Modifier.size(16.dp).clickable { viewModel.remove(kata) },
                                )
                            },
                        )
                    }
                }
            }

            // Discover: sesuatu untuk ditelusuri SEBELUM mengetik apa pun.
            // Kotak pencarian kosong yang hanya menunggu kata kunci menyerahkan
            // seluruh beban kepada pengguna yang belum tahu harus mencari apa.
            if (tagarNaik.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Tagar Sedang Naik",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tagarNaik.forEach { (tagar, jumlah) ->
                        AssistChip(
                            onClick = { onTagClick(tagar) },
                            label = { Text("#$tagar · $jumlah") },
                            shape = pill,
                        )
                    }
                }
            }

            if (creatorNaik.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Creator Populer")
                Spacer(Modifier.height(4.dp))
                creatorNaik.forEach { c ->
                    ListItem(
                        leadingContent = { AppAvatar(url = c.photoUrl, name = c.displayName, size = 40.dp, verified = c.verified) },
                        headlineContent = { Text(c.displayName) },
                        supportingContent = {
                            Text("${c.followerCount} pengikut · ${c.city ?: "-"}", style = MaterialTheme.typography.bodySmall)
                        },
                        colors = ListItemDefaults.colors(containerColor = AppColors.Background),
                        modifier = Modifier.clickable { onCreatorClick(c.creatorId) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (sheetTerbuka) {
        SearchFilterSheet(
            awal = filters,
            fokus = sheetFokus,
            onDismiss = { sheetTerbuka = false },
            onApply = { baru ->
                filters = baru
                sheetTerbuka = false
            },
        )
    }
}

/**
 * Chip filter yang menampilkan keadaannya sendiri.
 *
 * `adaMenu` menambahkan tanda panah kecil untuk chip yang membuka sheet,
 * membedakannya dari chip yang langsung menyalakan/mematikan sesuatu di tempat
 * (Terverifikasi). Tanpa pembeda itu keduanya terlihat identik padahal satu
 * ketukan menghasilkan dua akibat yang sangat berbeda.
 */
@Composable
internal fun ChipFilter(
    label: String,
    aktif: Boolean,
    onClick: () -> Unit,
    ikon: ImageVector? = null,
    adaMenu: Boolean = false,
) {
    val leading: (@Composable () -> Unit)? = when {
        aktif && !adaMenu -> { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } }
        ikon != null -> { { Icon(ikon, contentDescription = null, modifier = Modifier.size(16.dp)) } }
        else -> null
    }
    val trailing: (@Composable () -> Unit)? = if (adaMenu) {
        { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) }
    } else {
        null
    }
    FilterChip(
        selected = aktif,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(percent = 50),
        leadingIcon = leading,
        trailingIcon = trailing,
    )
}

/** Chip ringkasan yang bisa dibuang satu per satu dari baris "filter aktif". */
@Composable
internal fun ChipHapus(label: String, onHapus: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onHapus,
        label = { Text(label, maxLines = 1) },
        shape = RoundedCornerShape(percent = 50),
        trailingIcon = {
            Icon(Icons.Default.Close, contentDescription = "Hapus filter $label", modifier = Modifier.size(16.dp))
        },
    )
}
