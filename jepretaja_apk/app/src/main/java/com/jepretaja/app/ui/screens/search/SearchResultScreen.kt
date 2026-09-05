package com.jepretaja.app.ui.screens.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterAltOff
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.AppConstants
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.CreatorCard
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.SkeletonCreatorCard
import com.jepretaja.app.ui.components.SkeletonPostTile

/**
 * Hasil pencarian dalam tiga tab: Creator, Karya, Kategori.
 *
 * Tiga hal yang membedakan versi ini:
 *
 * 1. **Creator dan Karya tidak lagi terlihat seperti dua daftar yang sama.**
 *    Sebelumnya keduanya hanya berbeda jumlah kolom, dan tab yang bertuliskan
 *    "Creator 12 / Karya 30" tidak menjelaskan apa bedanya. Sekarang tiap tab
 *    punya ikon dan satu kalimat yang menyatakan isinya, ubin Karya menempelkan
 *    nama creator di atas fotonya, dan ketukannya jelas berbeda tujuan: kartu
 *    Creator membuka profil, ubin Karya membuka karyanya.
 * 2. **Kerangka pemuatan, bukan layar kosong.** Flow-nya dimulai dari `null`,
 *    bukan daftar kosong, supaya "sedang dimuat" bisa dibedakan dari "memang
 *    tidak ada". Sebelumnya keduanya tampil identik, jadi selama satu-dua detik
 *    pertama pengguna membaca "tidak ada creator yang cocok" untuk pencarian
 *    yang sebenarnya berhasil.
 * 3. **Tiga keadaan kosong yang berbeda**, karena tindakan yang benar untuk
 *    masing-masing juga berbeda: tidak ada creator (longgarkan filter), tidak
 *    ada karya (kata kuncinya dicocokkan ke caption, bukan ke nama), dan sama
 *    sekali tidak ditemukan (periksa ejaan atau cari yang lain).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultScreen(
    filters: SearchFilters,
    onBack: () -> Unit,
    onCreatorClick: (String) -> Unit,
    onPostClick: (String) -> Unit = {},
    viewModel: SearchResultViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    // Filter bisa berubah dari dalam layar ini (lewat sheet atau tab Kategori),
    // jadi disimpan sebagai state, bukan dipakai langsung dari argumen route.
    var aktif by remember(filters) { mutableStateOf(filters) }
    var tab by remember { mutableIntStateOf(0) }
    var sheetTerbuka by remember { mutableStateOf(false) }
    var sheetFokus by remember { mutableStateOf<BagianFilter?>(null) }

    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var lokasiDitolak by remember { mutableStateOf(false) }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun ambilLokasi() {
        val diizinkan = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!diizinkan) return
        lokasiDitolak = false
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) { lat = loc.latitude; lng = loc.longitude }
        }
    }

    val izinLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { diberi ->
        if (diberi) ambilLokasi() else lokasiDitolak = true
    }

    // Izin lokasi hanya diminta ketika benar-benar dibutuhkan, yaitu saat
    // pengguna memilih urutan "Terdekat". Meminta di awal untuk fitur yang
    // mungkin tidak pernah ia pakai adalah cara tercepat mendapat penolakan
    // permanen — dan penolakan itu ikut mematikan Nearby.
    LaunchedEffect(aktif.sort) {
        if (aktif.sort == SearchSort.TERDEKAT && lat == null) {
            val diizinkan = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (diizinkan) ambilLokasi() else izinLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // `null` = belum ada jawaban dari Firestore. Ini satu-satunya cara
    // membedakan "sedang dimuat" dari "hasilnya nol" pada Flow yang tipenya
    // sama-sama List.
    val creators by remember(aktif, lat, lng) { viewModel.results(aktif, lat, lng) }
        .collectAsState(initial = null)
    val posts by remember(aktif) { viewModel.posts(aktif) }.collectAsState(initial = null)

    val kata = aktif.query?.trim().orEmpty()
    val kategoriCocok = remember(kata) {
        AppConstants.SERVICE_CATEGORIES.filter { kata.isBlank() || it.contains(kata, ignoreCase = true) }
    }

    val sedangMemuat = creators == null || posts == null
    // "Tidak ditemukan" yang sesungguhnya: tiga-tiganya kosong. Selama salah
    // satu tab masih punya isi, yang terjadi bukan pencarian gagal melainkan
    // hasilnya kebetulan ada di tab sebelah — dan itu pesan yang berbeda.
    val takAdaApaPun = !sedangMemuat &&
        creators!!.isEmpty() && posts!!.isEmpty() && kategoriCocok.isEmpty()

    Scaffold(
        topBar = {
            AppTopBar(
                title = kata.ifBlank { "Hasil Pencarian" },
                onBack = onBack,
                actions = {
                    // Lencana angka membuat filter yang sedang berlaku terlihat
                    // dari layar hasil. Tanpa itu, daftar yang pendek terbaca
                    // sebagai "memang tidak ada creator" padahal sebenarnya
                    // "filternya terlalu ketat".
                    BadgedBox(
                        badge = {
                            if (aktif.adaFilterAktif) {
                                Badge(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary) {
                                    Text("${aktif.jumlahFilterAktif}")
                                }
                            }
                        },
                    ) {
                        IconButton(onClick = { sheetFokus = null; sheetTerbuka = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Filter & urutkan")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // ---- Baris kontrol ------------------------------------------
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ChipFilter(
                        label = aktif.sort.label,
                        aktif = aktif.sort != SearchSort.RELEVAN,
                        ikon = Icons.Default.Tune,
                        adaMenu = true,
                        onClick = { sheetFokus = BagianFilter.URUTKAN; sheetTerbuka = true },
                    )
                }
                item {
                    ChipFilter(
                        label = aktif.ringkasanHarga() ?: "Harga",
                        aktif = aktif.hargaAktif,
                        adaMenu = true,
                        onClick = { sheetFokus = BagianFilter.HARGA; sheetTerbuka = true },
                    )
                }
                item {
                    ChipFilter(
                        label = aktif.ringkasanRating() ?: "Rating",
                        aktif = aktif.ratingAktif,
                        ikon = Icons.Default.Star,
                        adaMenu = true,
                        onClick = { sheetFokus = BagianFilter.RATING; sheetTerbuka = true },
                    )
                }
                item {
                    ChipFilter(
                        label = aktif.ringkasanLayanan() ?: "Jenis Layanan",
                        aktif = aktif.layananAktif,
                        adaMenu = true,
                        onClick = { sheetFokus = BagianFilter.LAYANAN; sheetTerbuka = true },
                    )
                }
                item {
                    ChipFilter(
                        label = "Terverifikasi",
                        aktif = aktif.verifiedOnly,
                        ikon = Icons.Default.Verified,
                        onClick = { aktif = aktif.copy(verifiedOnly = !aktif.verifiedOnly) },
                    )
                }
            }

            if (aktif.adaFilterAktif) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${aktif.jumlahFilterAktif} filter aktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { aktif = aktif.direset() }) { Text("Reset filter") }
                }
            }

            if (aktif.sort == SearchSort.TERDEKAT && lokasiDitolak) {
                Text(
                    "Izin lokasi ditolak, jadi urutan \"Terdekat\" belum bisa dipakai.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.Danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // Tidak ada apa pun di ketiga tab: menampilkan tiga tab kosong
            // hanya menyuruh pengguna memeriksa sendiri satu per satu untuk
            // sampai pada kesimpulan yang sudah kita ketahui.
            if (takAdaApaPun) {
                TidakDitemukan(
                    kata = kata,
                    adaFilter = aktif.adaFilterAktif,
                    onResetFilter = { aktif = aktif.direset() },
                )
            } else {

                TabRow(
                    selectedTabIndex = tab,
                    containerColor = AppColors.Background,
                    contentColor = AppColors.Primary,
                ) {
                    TabHasil(
                        terpilih = tab == 0,
                        onClick = { tab = 0 },
                        label = "Creator",
                        jumlah = creators?.size,
                        ikon = Icons.Default.PersonSearch,
                    )
                    TabHasil(
                        terpilih = tab == 1,
                        onClick = { tab = 1 },
                        label = "Karya",
                        jumlah = posts?.size,
                        ikon = Icons.Default.PhotoLibrary,
                    )
                    TabHasil(
                        terpilih = tab == 2,
                        onClick = { tab = 2 },
                        label = "Kategori",
                        jumlah = kategoriCocok.size,
                        ikon = Icons.Default.Category,
                    )
                }

                // Satu kalimat yang menjawab "isi tab ini apa". Nama tab saja tidak
                // cukup: "Karya" bisa berarti portofolio creator yang ada di tab
                // sebelah, dan pengguna baru tidak punya cara menebak bedanya.
                Text(
                    text = when (tab) {
                        0 -> "Profil creator yang bisa langsung kamu pesan."
                        1 -> "Foto & video hasil pemotretan. Ketuk untuk melihat karyanya."
                        else -> "Telusuri berdasarkan jenis layanan."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.SurfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                when (tab) {
                    0 -> TabCreator(
                        creators = creators,
                        aktif = aktif,
                        kata = kata,
                        adaKarya = posts?.isNotEmpty() == true,
                        onCreatorClick = onCreatorClick,
                        onResetFilter = { aktif = aktif.direset() },
                        onLihatKarya = { tab = 1 },
                    )

                    1 -> TabKarya(
                        posts = posts,
                        kata = kata,
                        adaCreator = creators?.isNotEmpty() == true,
                        onPostClick = onPostClick,
                        onLihatCreator = { tab = 0 },
                    )

                    else -> LazyColumn(Modifier.weight(1f)) {
                        items(kategoriCocok) { kategori ->
                            ListItem(
                                leadingContent = {
                                    Icon(Icons.Default.Category, contentDescription = null, tint = AppColors.Primary)
                                },
                                headlineContent = { Text(kategori, color = AppColors.TextPrimary) },
                                supportingContent = {
                                    Text(
                                        "Lihat creator kategori ini",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextSecondary,
                                    )
                                },
                                trailingContent = {
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
                                },
                                colors = ListItemDefaults.colors(containerColor = AppColors.Background),
                                // Memilih kategori menyaring ulang di layar ini juga,
                                // supaya tidak menumpuk halaman hasil di back stack.
                                modifier = Modifier.clickable {
                                    aktif = aktif.copy(categories = listOf(kategori), query = null)
                                    tab = 0
                                },
                            )
                            HorizontalDivider(color = AppColors.Border)
                        }
                    }
                }
            }
        }
    }

    if (sheetTerbuka) {
        SearchFilterSheet(
            awal = aktif,
            fokus = sheetFokus,
            onDismiss = { sheetTerbuka = false },
            onApply = { baru ->
                aktif = baru
                sheetTerbuka = false
            },
        )
    }
}

/* --------------------------------------------------------------------------
 * Tab Creator
 * ----------------------------------------------------------------------- */

@Composable
private fun ColumnScope.TabCreator(
    creators: List<CreatorModel>?,
    aktif: SearchFilters,
    kata: String,
    adaKarya: Boolean,
    onCreatorClick: (String) -> Unit,
    onResetFilter: () -> Unit,
    onLihatKarya: () -> Unit,
) {
    val kolom = GridCells.Fixed(2)
    val isi = PaddingValues(16.dp)
    val jarak = Arrangement.spacedBy(12.dp)

    when {
        creators == null -> LazyVerticalGrid(
            columns = kolom,
            contentPadding = isi,
            horizontalArrangement = jarak,
            verticalArrangement = jarak,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f),
        ) {
            items(6) { SkeletonCreatorCard(Modifier.fillMaxWidth()) }
        }

        creators.isEmpty() -> KotakKosong {
            // Dua sebab kosong, dua jalan keluar. Menyuruh "coba kata lain"
            // kepada orang yang filternya terlalu ketat membuang pencarian yang
            // sebenarnya sudah benar.
            val labelAksi: String? = when {
                aktif.adaFilterAktif -> "Reset filter"
                adaKarya -> "Lihat tab Karya"
                else -> null
            }
            val aksi: (() -> Unit)? = when {
                aktif.adaFilterAktif -> onResetFilter
                adaKarya -> onLihatKarya
                else -> null
            }
            EmptyState(
                icon = if (aktif.adaFilterAktif) Icons.Default.FilterAltOff else Icons.Default.PersonSearch,
                title = "Belum ada creator yang cocok",
                description = when {
                    aktif.adaFilterAktif ->
                        "Filter yang sedang aktif menyingkirkan semua creator. Longgarkan salah satunya."
                    adaKarya ->
                        "Tidak ada nama creator yang cocok, tapi ada karya yang cocok dengan \"$kata\"."
                    kata.isNotBlank() ->
                        "Kata kunci dicocokkan dengan nama creator, kota, dan kategorinya."
                    else ->
                        "Belum ada creator aktif untuk pencarian ini."
                },
                actionLabel = labelAksi,
                onAction = aksi,
            )
        }

        else -> LazyVerticalGrid(
            columns = kolom,
            contentPadding = isi,
            horizontalArrangement = jarak,
            verticalArrangement = jarak,
            modifier = Modifier.weight(1f),
        ) {
            items(creators) { creator ->
                // Kartu yang sama persis dengan Home dan Favorit — hanya
                // lebarnya yang menyesuaikan sel grid.
                CreatorCard(
                    creator = creator,
                    onClick = { onCreatorClick(creator.creatorId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Tab Karya
 * ----------------------------------------------------------------------- */

@Composable
private fun ColumnScope.TabKarya(
    posts: List<ExplorePostModel>?,
    kata: String,
    adaCreator: Boolean,
    onPostClick: (String) -> Unit,
    onLihatCreator: () -> Unit,
) {
    val kolom = GridCells.Fixed(3)
    val jarak = Arrangement.spacedBy(2.dp)

    when {
        posts == null -> LazyVerticalGrid(
            columns = kolom,
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = jarak,
            verticalArrangement = jarak,
            userScrollEnabled = false,
            modifier = Modifier.weight(1f),
        ) {
            items(12) { SkeletonPostTile(Modifier.fillMaxWidth()) }
        }

        posts.isEmpty() -> KotakKosong {
            EmptyState(
                icon = Icons.Default.ImageSearch,
                title = "Belum ada karya yang cocok",
                description = if (adaCreator) {
                    "Tidak ada foto atau video yang cocok, tapi ada creator yang cocok dengan \"$kata\"."
                } else {
                    "Kata kunci dicocokkan dengan caption, kategori, dan nama creator pada karya."
                },
                actionLabel = if (adaCreator) "Lihat tab Creator" else null,
                onAction = if (adaCreator) onLihatCreator else null as (() -> Unit)?,
            )
        }

        else -> LazyVerticalGrid(
            columns = kolom,
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = jarak,
            verticalArrangement = jarak,
            modifier = Modifier.weight(1f),
        ) {
            items(posts) { post -> UbinKarya(post = post, onClick = { onPostClick(post.postId) }) }
        }
    }
}

/**
 * Satu ubin karya.
 *
 * Nama creator ditempel di kaki ubin, bukan disembunyikan sampai ubinnya
 * dibuka. Tanpa itu tab Karya terbaca sebagai kumpulan foto tanpa pemilik, dan
 * pengguna kehilangan satu-satunya hal yang menghubungkannya kembali ke tab
 * Creator: siapa yang memotretnya.
 */
@Composable
private fun UbinKarya(post: ExplorePostModel, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.SurfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = post.thumbnailUrl ?: post.mediaUrls.firstOrNull(),
            contentDescription = post.caption.ifBlank { "Karya ${post.creatorName}" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (post.type == "video") {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),
            )
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))
                ),
        )
        Text(
            post.creatorName,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 6.dp, vertical = 5.dp),
        )
    }
}

/* --------------------------------------------------------------------------
 * Keadaan kosong
 * ----------------------------------------------------------------------- */

/** Keadaan kosong tingkat layar: tidak ada creator, karya, MAUPUN kategori. */
@Composable
private fun ColumnScope.TidakDitemukan(
    kata: String,
    adaFilter: Boolean,
    onResetFilter: () -> Unit,
) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                icon = Icons.Default.SearchOff,
                title = if (kata.isBlank()) "Tidak ada hasil" else "\"$kata\" tidak ditemukan",
                description = if (adaFilter) {
                    "Tidak ada creator, karya, maupun kategori yang cocok dengan filter yang sedang aktif."
                } else {
                    "Coba periksa ejaannya, pakai kata yang lebih umum seperti \"wedding\", atau telusuri lewat kategori."
                },
                actionLabel = if (adaFilter) "Reset filter" else null,
                onAction = if (adaFilter) onResetFilter else null as (() -> Unit)?,
            )
        }
    }
}

@Composable
private fun ColumnScope.KotakKosong(isi: @Composable () -> Unit) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { isi() }
}

/* --------------------------------------------------------------------------
 * Tab
 * ----------------------------------------------------------------------- */

/**
 * Tab dengan ikon + jumlah.
 *
 * Jumlahnya sengaja tidak digambar selama [jumlah] masih null: menampilkan "0"
 * saat data belum tiba adalah angka yang salah, dan angka salah lebih merusak
 * daripada angka yang belum muncul.
 */
@Composable
private fun TabHasil(
    terpilih: Boolean,
    onClick: () -> Unit,
    label: String,
    jumlah: Int?,
    ikon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Tab(
        selected = terpilih,
        onClick = onClick,
        selectedContentColor = AppColors.Primary,
        unselectedContentColor = AppColors.TextSecondary,
        text = {
            Text(
                if (jumlah == null) label else "$label ($jumlah)",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = { Icon(ikon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}
