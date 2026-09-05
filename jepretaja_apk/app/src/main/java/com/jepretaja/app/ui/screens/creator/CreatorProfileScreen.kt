package com.jepretaja.app.ui.screens.creator

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.PortfolioModel
import com.jepretaja.app.data.model.ReviewModel
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SkeletonBox
import com.jepretaja.app.ui.components.SkeletonList
import com.jepretaja.app.ui.state.AuthViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LOKAL_ID = Locale("in", "ID")
private val HARI_PENDEK = DateTimeFormatter.ofPattern("EEE", LOKAL_ID)

/**
 * Indeks item TabRow di dalam grid: 0 kepala, 1 badan keterangan, 2 TabRow.
 *
 * Dipakai tombol "Booking" untuk menggulir ke daftar paket. Sebelumnya posisi
 * itu diukur lewat `onGloballyPositioned` lalu digulir dengan koordinat piksel —
 * cara yang selalu meleset kalau isi di atasnya berubah tinggi (bio panjang,
 * kartu ketersediaan muncul-hilang) sesudah pengukuran terakhir.
 */
private const val INDEKS_TABROW = 2

/**
 * Profil publik creator — halaman paling menentukan di aplikasi ini, karena di
 * sinilah calon pelanggan memutuskan memesan atau menutup aplikasi.
 *
 * Perubahan terbesar di versi ini adalah **satu area gulir, bukan dua**.
 * Sebelumnya halaman ini berupa Column yang menggulir, dan isi tab (portofolio,
 * Explore, paket) ditumpangkan di dalam `Box(Modifier.height(520.dp))` yang
 * menggulir sendiri di tengahnya. Akibatnya: menggeser di dalam kotak
 * menggerakkan grid tapi tidak halamannya, menggeser di luar kotak menggerakkan
 * halaman tapi tidak gridnya, dan portofolio berisi 40 foto terkurung di
 * jendela setinggi separuh layar. Sekarang seluruh halaman adalah satu
 * LazyVerticalGrid tiga kolom: kepala profil dan blok keterangan menjadi item
 * selebar penuh, ubin portofolio menjadi item biasa di grid yang sama.
 *
 * Selebihnya sudah ada sejak versi sebelumnya dan dipertahankan: kepala dengan
 * sampul 220dp + avatar 108dp, lencana terverifikasi bertulisan, kartu angka
 * (rating, ulasan, proyek selesai, pengikut) dengan harga mulai di barisnya
 * sendiri, kalender ketersediaan 14 hari, dan bilah Chat/Booking yang menempel
 * di dasar layar.
 */
@Composable
fun CreatorProfileScreen(
    onBack: () -> Unit,
    onChatOpen: (String) -> Unit,
    onReviewsClick: (String) -> Unit,
    onPackageClick: (String) -> Unit,
    onLoginRequired: () -> Unit,
    authViewModel: AuthViewModel,
    onFollowList: (String, Int) -> Unit = { _, _ -> },
    onCategoryClick: (String) -> Unit = {},
    onPostClick: (String) -> Unit = {},
    viewModel: CreatorProfileViewModel = hiltViewModel(),
) {
    val creator by viewModel.creator.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val tabs = listOf("Portfolio", "Explore", "Paket")
    var tabIndex by remember { mutableIntStateOf(0) }
    var menuTerbuka by remember { mutableStateOf(false) }
    var dialogLapor by remember { mutableStateOf(false) }
    var dialogBlokir by remember { mutableStateOf(false) }
    var pesan by remember { mutableStateOf<String?>(null) }
    var portofolioDibuka by remember { mutableStateOf<PortfolioModel?>(null) }
    var fotoProfilDibuka by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pesan) {
        pesan?.let { snackbarHostState.showSnackbar(it); pesan = null }
    }

    val gridState = rememberLazyGridState()

    fun bukaPaket() {
        tabIndex = 2
        // Menggulir ke indeks item, bukan ke koordinat piksel. Tombol "Booking"
        // dulu hanya mengubah tabIndex; kalau tabnya sedang jauh di bawah
        // layar, ketukan itu tampak tidak menghasilkan apa-apa dan pengguna
        // menekannya berulang kali.
        scope.launch { gridState.animateScrollToItem(INDEKS_TABROW) }
    }

    fun mulaiChat(c: CreatorModel) {
        val uid = authState.uid
        if (uid == null) onLoginRequired()
        else scope.launch { onChatOpen(viewModel.openChat(uid, c.displayName)) }
    }

    val mengikutiJumlah by remember(viewModel.creatorId) { viewModel.jumlahMengikuti() }
        .collectAsState(initial = 0)
    // `null` = belum ada jawaban, dibedakan dari daftar kosong. Tanpa
    // pembedaan itu bagian ulasan selalu berkedip "belum ada ulasan" sekejap
    // pada creator yang justru ulasannya banyak.
    val ulasan by remember(viewModel.creatorId) { viewModel.ulasanTeratas() }
        .collectAsState(initial = null)
    val tanggalPenuh by remember(viewModel.creatorId) { viewModel.tanggalPenuh() }
        .collectAsState(initial = emptyList())
    val portofolio by remember(viewModel.creatorId) { viewModel.portofolio() }
        .collectAsState(initial = null)
    val karyaExplore by remember(viewModel.creatorId) { viewModel.karyaExplore() }
        .collectAsState(initial = null)
    val paket by remember(viewModel.creatorId) { viewModel.paket() }
        .collectAsState(initial = null)
    val sudahDiikuti by remember(viewModel.creatorId, authState.uid) {
        val uid = authState.uid
        if (uid != null) viewModel.apakahDiikuti(uid) else kotlinx.coroutines.flow.flowOf(false)
    }.collectAsState(initial = false)

    // Dihitung dari daftar karya yang memang sudah diambil untuk tab Explore,
    // bukan dari langganan Firestore kedua ke koleksi yang sama.
    val totalSuka = remember(karyaExplore) { karyaExplore.orEmpty().sumOf { it.likeCount.toInt() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = creator?.displayName ?: "",
                onBack = onBack,
                actions = {
                    IconButton(onClick = {
                        val tautan = "https://jepretaja.app/creator/${viewModel.creatorId}"
                        val kirim = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Lihat profil ${creator?.displayName.orEmpty()} di JepretAja: $tautan")
                        }
                        runCatching { context.startActivity(Intent.createChooser(kirim, "Bagikan profil")) }
                    }) { Icon(Icons.Default.Share, contentDescription = "Bagikan profil") }

                    IconButton(onClick = { menuTerbuka = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu lainnya")
                    }
                    DropdownMenu(expanded = menuTerbuka, onDismissRequest = { menuTerbuka = false }) {
                        DropdownMenuItem(
                            text = { Text("Laporkan creator") },
                            leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null) },
                            onClick = { menuTerbuka = false; dialogLapor = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Blokir creator") },
                            leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                            onClick = { menuTerbuka = false; dialogBlokir = true },
                        )
                    }
                },
            )
        },
        bottomBar = {
            creator?.let { c ->
                BilahAksi(
                    creator = c,
                    onBooking = { bukaPaket() },
                    onChat = { mulaiChat(c) },
                )
            }
        },
    ) { padding ->
        when {
            loading -> KerangkaProfil(Modifier.padding(padding))

            error != null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.CloudOff,
                    title = "Gagal memuat profil",
                    description = error,
                    actionLabel = "Coba Lagi",
                    onAction = { viewModel.load() },
                )
            }

            creator == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.PersonOff,
                    title = "Creator tidak ditemukan",
                    description = "Profil ini mungkin sudah dihapus atau sedang dinonaktifkan.",
                    actionLabel = "Kembali",
                    onAction = onBack,
                )
            }

            else -> {
                val c = creator!!
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    itemPenuh("kepala") {
                        KepalaProfil(c = c, onFotoClick = { fotoProfilDibuka = true })
                    }

                    itemPenuh("badan") {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Spacer(Modifier.height(14.dp))

                            // --- Nama + lencana verifikasi ---
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    c.displayName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = AppColors.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (c.verified) {
                                    Spacer(Modifier.width(8.dp))
                                    LencanaVerified()
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn, contentDescription = "Lokasi",
                                    tint = AppColors.TextSecondary, modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    c.city?.takeIf { it.isNotBlank() } ?: "Lokasi belum diisi",
                                    color = AppColors.TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text("  ·  ", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    statusKehadiran(c),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (sedangOnline(c)) AppColors.Success else AppColors.TextSecondary,
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                            KartuAngka(
                                creator = c,
                                mengikutiJumlah = mengikutiJumlah,
                                onUlasan = { onReviewsClick(c.creatorId) },
                                onPengikut = { onFollowList(viewModel.creatorId, 1) },
                                onMengikuti = { onFollowList(viewModel.creatorId, 0) },
                                onLihatPaket = { bukaPaket() },
                            )

                            Spacer(Modifier.height(14.dp))
                            BadgeStatusBooking(c)

                            // Sinyal kepercayaan yang lebih menentukan daripada
                            // jumlah pengikut ketika yang dibeli adalah jasa.
                            val penyelesaian = tingkatPenyelesaian(c)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                c.avgResponseMinutes?.let {
                                    ChipInfo(Icons.Default.Bolt, "Balas ${durasiSingkat(it)}")
                                }
                                penyelesaian?.let { ChipInfo(Icons.Default.TaskAlt, "$it% booking selesai") }
                                // "Suka" adalah keterangan, jadi digambar
                                // sebagai keterangan — bukan angka besar yang
                                // bisa diketuk tapi tidak menuju ke mana pun.
                                if (totalSuka > 0) {
                                    ChipInfo(Icons.Default.FavoriteBorder, "${Formatters.compactCount(totalSuka.toLong())} suka")
                                }
                            }

                            c.bio?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(14.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
                            }

                            if (c.categories.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    c.categories.take(4).forEach { kategori ->
                                        AssistChip(
                                            // Chip kategori adalah jalan pintas
                                            // paling wajar dari profil ke
                                            // "siapa lagi yang mengerjakan ini".
                                            onClick = { onCategoryClick(kategori) },
                                            label = { Text(kategori, style = MaterialTheme.typography.labelSmall) },
                                            shape = RoundedCornerShape(percent = 50),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val uid = authState.uid
                                    if (uid == null) {
                                        onLoginRequired()
                                    } else {
                                        viewModel.toggleFollow(
                                            uid,
                                            authState.profile?.name.orEmpty(),
                                            authState.profile?.photoUrl,
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(percent = 50),
                                colors = if (sudahDiikuti) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = AppColors.SurfaceVariant,
                                        contentColor = AppColors.TextPrimary,
                                    )
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = AppColors.Primary,
                                        contentColor = AppColors.OnPrimary,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                            ) {
                                Icon(
                                    if (sudahDiikuti) Icons.Default.Check else Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(if (sudahDiikuti) "Mengikuti" else "Ikuti")
                            }

                            Spacer(Modifier.height(20.dp))
                            KartuKetersediaan(creator = c, tanggalPenuh = tanggalPenuh, onTanya = { mulaiChat(c) })

                            Spacer(Modifier.height(20.dp))
                            BagianUlasan(
                                creator = c,
                                ulasan = ulasan,
                                onLihatSemua = { onReviewsClick(c.creatorId) },
                            )

                            Spacer(Modifier.height(20.dp))
                        }
                    }

                    itemPenuh("tab") {
                        TabRow(
                            selectedTabIndex = tabIndex,
                            containerColor = AppColors.Background,
                            contentColor = AppColors.Primary,
                        ) {
                            tabs.forEachIndexed { i, judul ->
                                Tab(
                                    selected = tabIndex == i,
                                    onClick = { tabIndex = i },
                                    text = { Text(judul, style = MaterialTheme.typography.labelLarge) },
                                    icon = {
                                        Icon(
                                            ikonTab(i), contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                    selectedContentColor = AppColors.Primary,
                                    unselectedContentColor = AppColors.TextSecondary,
                                )
                            }
                        }
                    }

                    when (tabIndex) {
                        0 -> isiPortofolio(portofolio) { portofolioDibuka = it }
                        1 -> isiExplore(karyaExplore, c.pinnedPostId, onPostClick)
                        else -> isiPaket(paket, onPackageClick) { mulaiChat(c) }
                    }

                    itemPenuh("ekor") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        portofolioDibuka?.let { item ->
            PratinjauPortfolio(item = item, onTutup = { portofolioDibuka = null })
        }

        if (fotoProfilDibuka) {
            creator?.let { c ->
                PratinjauFotoProfil(
                    url = c.photoUrl,
                    nama = c.displayName,
                    onTutup = { fotoProfilDibuka = false },
                )
            }
        }

        if (dialogBlokir) {
            AlertDialog(
                onDismissRequest = { dialogBlokir = false },
                title = { Text("Blokir ${creator?.displayName.orEmpty()}?") },
                text = {
                    Text(
                        "Kalian tidak akan bisa saling mengirim pesan. " +
                            "Blokir bisa dibatalkan kapan saja lewat daftar chat.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val uid = authState.uid
                        dialogBlokir = false
                        if (uid == null) onLoginRequired()
                        else viewModel.blokir(uid) { pesan = "Creator diblokir." }
                    }) { Text("Blokir", color = AppColors.Danger) }
                },
                dismissButton = { TextButton(onClick = { dialogBlokir = false }) { Text("Batal") } },
            )
        }

        if (dialogLapor) {
            val alasanList = listOf(
                "inappropriate_content" to "Konten tidak pantas",
                "scam" to "Penipuan / meminta bayaran di luar aplikasi",
                "impersonation" to "Mengaku sebagai orang lain",
                "other" to "Lainnya",
            )
            AlertDialog(
                onDismissRequest = { dialogLapor = false },
                title = { Text("Laporkan creator") },
                text = {
                    Column {
                        alasanList.forEach { (kode, label) ->
                            TextButton(
                                onClick = {
                                    val uid = authState.uid
                                    dialogLapor = false
                                    if (uid == null) {
                                        onLoginRequired()
                                    } else {
                                        viewModel.laporkan(uid, kode) {
                                            pesan = "Laporan terkirim. Tim kami akan meninjaunya."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(label, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialogLapor = false }) { Text("Batal") } },
            )
        }
    }
}

/* --------------------------------------------------------------------------
 * Kepala
 * ----------------------------------------------------------------------- */

/**
 * Sampul besar + avatar yang menimpanya.
 *
 * Avatar diberi cincin warna latar selebar 4dp supaya tetap terpisah dari foto
 * sampul apa pun warnanya — tanpa cincin, avatar bertepi gelap di atas sampul
 * gelap kehilangan bentuknya sama sekali.
 */
@Composable
private fun KepalaProfil(c: CreatorModel, onFotoClick: () -> Unit) {
    val tinggiSampul = 220.dp
    val ukuranAvatar = 108.dp

    Box(Modifier.fillMaxWidth().height(tinggiSampul + ukuranAvatar / 2)) {
        Box(
            Modifier.fillMaxWidth().height(tinggiSampul)
                .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryDark))),
        ) {
            if (!c.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = c.coverUrl,
                    contentDescription = "Sampul ${c.displayName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Scrim bawah: menjaga tepi sampul menyatu ke latar halaman, sekaligus
            // memberi kontras di belakang avatar.
            Box(
                Modifier.fillMaxWidth().height(90.dp).align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, AppColors.Background))
                    ),
            )
        }

        Box(
            Modifier.align(Alignment.BottomStart).padding(start = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(ukuranAvatar + 8.dp)
                    .clip(CircleShape)
                    .background(AppColors.Background)
                    // Foto profil bisa diketuk untuk dilihat penuh. Di halaman
                    // yang menjual seseorang, foto orang itu adalah satu-satunya
                    // gambar yang tidak bisa dibuka besar sebelum ini — padahal
                    // setiap ubin portofolio di bawahnya bisa.
                    .clickable(enabled = !c.photoUrl.isNullOrBlank(), onClick = onFotoClick),
                contentAlignment = Alignment.Center,
            ) {
                AppAvatar(url = c.photoUrl, name = c.displayName, size = ukuranAvatar, verified = false)
            }
            if (sedangOnline(c)) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(24.dp).clip(CircleShape)
                        .background(AppColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(15.dp).clip(CircleShape).background(AppColors.Success))
                }
            }
        }
    }
}

/** Lencana terverifikasi bertulisan, bukan sekadar ikon centang. */
@Composable
private fun LencanaVerified() {
    Row(
        Modifier.clip(RoundedCornerShape(percent = 50))
            .background(AppColors.Info.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Verified, contentDescription = null,
            tint = AppColors.Info, modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("Terverifikasi", style = MaterialTheme.typography.labelSmall, color = AppColors.Info)
    }
}

/* --------------------------------------------------------------------------
 * Kartu angka
 * ----------------------------------------------------------------------- */

/**
 * Empat angka penentu keputusan + harga mulai.
 *
 * Harga diberi barisnya sendiri di bawah pemisah, bukan diperlakukan sebagai
 * angka kelima. Harga adalah satu-satunya angka di kartu ini yang menjawab
 * "mampu tidak saya", dan menyusunnya sejajar dengan jumlah pengikut membuatnya
 * hilang di antara angka-angka yang lebih kecil urusannya.
 */
@Composable
private fun KartuAngka(
    creator: CreatorModel,
    mengikutiJumlah: Int,
    onUlasan: () -> Unit,
    onPengikut: () -> Unit,
    onMengikuti: () -> Unit,
    onLihatPaket: () -> Unit,
) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AngkaProfil(
                nilai = if (creator.reviewCount == 0) "Baru" else "%.1f".format(creator.rating),
                label = if (creator.reviewCount == 0) "belum ada ulasan" else "${creator.reviewCount} ulasan",
                ikon = Icons.Default.Star,
                warnaIkon = AppColors.Warning,
                onClick = if (creator.reviewCount > 0) onUlasan else null,
            )
            AngkaProfil(
                nilai = "${creator.completedBookings}",
                label = "proyek selesai",
                ikon = Icons.Default.CameraAlt,
            )
            AngkaProfil(
                nilai = Formatters.compactCount(creator.followerCount.toLong()),
                label = "pengikut",
                onClick = onPengikut,
            )
            AngkaProfil(
                nilai = Formatters.compactCount(mengikutiJumlah.toLong()),
                label = "mengikuti",
                onClick = onMengikuti,
            )
        }

        HorizontalDivider(color = AppColors.Border)

        Row(
            Modifier.fillMaxWidth().clickable(onClick = onLihatPaket).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Mulai dari", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
                Text(
                    creator.minPrice?.let { Formatters.currency(it) } ?: "Harga belum diatur",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W700,
                    color = AppColors.TextPrimary,
                )
            }
            Text("Lihat paket", style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
            Icon(
                Icons.Default.ChevronRight, contentDescription = null,
                tint = AppColors.Primary, modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Satu angka.
 *
 * [onClick] boleh null, dan itu penting: yang tidak menuju ke mana pun tidak
 * dipasangi `clickable` sama sekali, sehingga tidak memberi riak sentuhan yang
 * menjanjikan sesuatu yang tidak akan terjadi.
 */
@Composable
private fun AngkaProfil(
    nilai: String,
    label: String,
    ikon: ImageVector? = null,
    warnaIkon: Color = AppColors.TextSecondary,
    onClick: (() -> Unit)? = null,
) {
    val dasar = Modifier.widthIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 4.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick).then(dasar) else dasar,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (ikon != null) {
                Icon(ikon, contentDescription = null, tint = warnaIkon, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(
                nilai,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W700,
                color = AppColors.TextPrimary,
                maxLines = 1,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* --------------------------------------------------------------------------
 * Ketersediaan
 * ----------------------------------------------------------------------- */

/**
 * Ketersediaan 14 hari ke depan.
 *
 * Datanya sudah lama ada di availability_blocks tapi hanya terlihat oleh
 * creator sendiri — padahal justru calon pelangganlah yang perlu tahu tanggal
 * mana yang sudah penuh. Sebelumnya ia tampil sebagai empat tanggal yang
 * dipisah koma, yang memaksa pengguna mencocokkan sendiri dengan tanggal
 * acaranya. Deretan hari membuat jawabannya bisa dibaca sekilas.
 */
@Composable
private fun KartuKetersediaan(
    creator: CreatorModel,
    tanggalPenuh: List<LocalDate>,
    onTanya: () -> Unit,
) {
    val hariIni = remember { LocalDate.now() }
    val penuh = remember(tanggalPenuh) { tanggalPenuh.toSet() }
    val duaMinggu = remember(hariIni) { (0L until 14L).map { hariIni.plusDays(it) } }
    val jumlahKosong = duaMinggu.count { it !in penuh }

    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CalendarMonth, contentDescription = null,
                tint = AppColors.Primary, modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Ketersediaan", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                Text(
                    if (creator.acceptingBookings) {
                        "$jumlahKosong dari 14 hari ke depan masih kosong"
                    } else {
                        "Sedang tidak menerima booking baru"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
        }

        // Jam kerja yang diisi creator di Studio. Tanpa ini, pelanggan menebak
        // sendiri dan sering mengajukan jam yang memang tidak bisa diambil —
        // penolakan yang bisa dihindari hanya dengan satu baris keterangan.
        creator.workingHours?.let { jam ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule, contentDescription = null,
                    tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Jam kerja ${jam.start}–${jam.end}" + (jam.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            duaMinggu.forEach { tanggal ->
                val terisi = tanggal in penuh || !creator.acceptingBookings
                Column(
                    Modifier
                        .width(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (terisi) AppColors.Danger.copy(alpha = 0.10f) else AppColors.Success.copy(alpha = 0.12f)
                        )
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        tanggal.format(HARI_PENDEK),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${tanggal.dayOfMonth}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.W700,
                        color = if (terisi) AppColors.Danger else AppColors.Success,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KeteranganWarna(AppColors.Success, "Kosong")
            Spacer(Modifier.width(12.dp))
            KeteranganWarna(AppColors.Danger, "Penuh")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onTanya) { Text("Tanya tanggal lain") }
        }
    }
}

@Composable
private fun KeteranganWarna(warna: Color, teks: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(warna))
        Spacer(Modifier.width(5.dp))
        Text(teks, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
    }
}

/* --------------------------------------------------------------------------
 * Ulasan
 * ----------------------------------------------------------------------- */

/**
 * Bagian ulasan: ringkasan rating, sebaran bintang, lalu dua ulasan terbaru.
 *
 * Sebaran bintangnya bukan hiasan. Rata-rata 4,6 bisa berarti "hampir semua
 * memberi 5" atau "banyak 5 dengan beberapa 1", dan dua kemungkinan itu adalah
 * dua creator yang sangat berbeda bagi orang yang sedang mempertaruhkan hari
 * pernikahannya. Satu angka rata-rata tidak pernah bisa membedakan keduanya.
 */
@Composable
private fun BagianUlasan(
    creator: CreatorModel,
    ulasan: List<ReviewModel>?,
    onLihatSemua: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Ulasan",
            style = MaterialTheme.typography.titleSmall,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (!ulasan.isNullOrEmpty()) {
            TextButton(onClick = onLihatSemua) { Text("Lihat semua ${ulasan.size}") }
        }
    }
    Spacer(Modifier.height(4.dp))

    when {
        ulasan == null -> {
            SkeletonBox(Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(18.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(64.dp), RoundedCornerShape(18.dp))
        }

        ulasan.isEmpty() -> PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.RateReview, contentDescription = null,
                    tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Belum ada ulasan", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                    Text(
                        // Creator baru tidak pantas terbaca seperti creator
                        // buruk. Kalimat ini menyatakan keadaannya apa adanya
                        // tanpa menyiratkan penilaian.
                        "Creator ini belum pernah diulas. Kamu bisa jadi yang pertama.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                    )
                }
            }
        }

        else -> {
            RingkasanRating(creator = creator, ulasan = ulasan)
            Spacer(Modifier.height(10.dp))
            ulasan.take(2).forEach { r -> KartuUlasan(r) }
        }
    }
}

@Composable
private fun RingkasanRating(creator: CreatorModel, ulasan: List<ReviewModel>) {
    // Dihitung dari daftar ulasan yang sudah di tangan, bukan dari field
    // agregat: rata-rata di dokumen creator dan daftar ulasan yang tayang bisa
    // berselisih kalau ada ulasan yang disembunyikan moderator, dan yang harus
    // cocok dengan bintang-bintang di bawahnya adalah yang terlihat.
    val rerata = remember(ulasan) { ulasan.map { it.rating.toInt() }.average() }
    val sebaran = remember(ulasan) {
        (5 downTo 1).map { bintang -> bintang to ulasan.count { it.rating.toInt() == bintang } }
    }

    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
                Text(
                    "%.1f".format(rerata),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.W700,
                    color = AppColors.TextPrimary,
                )
                Row {
                    repeat(5) { i ->
                        Icon(
                            if (i < rerata.toInt()) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (i < rerata.toInt()) AppColors.Warning else AppColors.Border,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${ulasan.size} ulasan",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                sebaran.forEach { (bintang, jumlah) ->
                    BarisSebaran(bintang = bintang, jumlah = jumlah, total = ulasan.size)
                }
            }
        }
        if (creator.completedBookings > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Dari ${creator.completedBookings} proyek yang sudah selesai",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun BarisSebaran(bintang: Int, jumlah: Int, total: Int) {
    val rasio = if (total == 0) 0f else jumlah.toFloat() / total
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            "$bintang",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(10.dp),
        )
        Icon(
            Icons.Default.Star, contentDescription = null,
            tint = AppColors.Border, modifier = Modifier.size(10.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(percent = 50))
                .background(AppColors.SurfaceVariant),
        ) {
            if (rasio > 0f) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(rasio)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(AppColors.Warning),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            "$jumlah",
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(22.dp),
        )
    }
}

/** Foto profil ukuran penuh. */
@Composable
private fun PratinjauFotoProfil(url: String?, nama: String, onTutup: () -> Unit) {
    Dialog(onDismissRequest = onTutup, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
            AsyncImage(
                model = url,
                contentDescription = "Foto profil $nama",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            )
            IconButton(onClick = onTutup, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
            }
        }
    }
}

@Composable
private fun KartuUlasan(r: ReviewModel) {
    PremiumCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), elevation = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppAvatar(url = null, name = r.customerName, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.customerName.ifBlank { "Pelanggan" },
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Lima bintang terbaca lebih cepat daripada angka "4": bentuknya
                // langsung memberi tahu seberapa jauh dari penuh tanpa perlu
                // mengingat bahwa skalanya sampai lima.
                Row {
                    repeat(5) { i ->
                        Icon(
                            if (i < r.rating) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (i < r.rating) AppColors.Warning else AppColors.Border,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
        if (r.text.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                r.text,
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* --------------------------------------------------------------------------
 * Bilah aksi bawah
 * ----------------------------------------------------------------------- */

/**
 * Chat dan Booking, menempel di dasar layar.
 *
 * Keduanya ada di sini dan tidak diulang lagi di badan halaman: satu tindakan
 * yang punya dua tombol di satu layar membuat pengguna berhenti untuk menebak
 * apakah keduanya melakukan hal yang sama.
 */
@Composable
private fun BilahAksi(creator: CreatorModel, onBooking: () -> Unit, onChat: () -> Unit) {
    val libur = !creator.acceptingBookings
    Surface(color = AppColors.Surface, shadowElevation = 12.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (libur) "Sedang libur" else "Mulai dari",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.TextSecondary,
                )
                Text(
                    if (libur) {
                        creator.awayUntil?.let { "Kembali ${Formatters.dateShort(it)}" } ?: "Hubungi lewat chat"
                    } else {
                        creator.minPrice?.let { Formatters.currency(it) } ?: "Lihat paket"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W700,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))

            OutlinedButton(
                onClick = onChat,
                shape = RoundedCornerShape(percent = 50),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(50.dp),
            ) {
                Icon(Icons.Filled.Chat, contentDescription = "Chat creator", modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                // Saat libur, tombolnya tidak dimatikan begitu saja: paketnya
                // tetap boleh dilihat, dan alasan liburnya sudah tertulis di
                // sebelah kiri.
                onClick = onBooking,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (libur) AppColors.SurfaceVariant else AppColors.Primary,
                    contentColor = if (libur) AppColors.TextPrimary else AppColors.OnPrimary,
                ),
                modifier = Modifier.height(50.dp),
            ) {
                Text(if (libur) "Lihat Paket" else "Booking")
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Sisanya
 * ----------------------------------------------------------------------- */

@Composable
private fun KerangkaProfil(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        SkeletonBox(Modifier.fillMaxWidth().height(220.dp), RoundedCornerShape(0.dp))
        Column(Modifier.padding(20.dp)) {
            SkeletonBox(Modifier.size(108.dp), CircleShape)
            Spacer(Modifier.height(14.dp))
            SkeletonBox(Modifier.width(180.dp).height(24.dp))
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.width(110.dp).height(14.dp))
            Spacer(Modifier.height(16.dp))
            SkeletonBox(Modifier.fillMaxWidth().height(120.dp), RoundedCornerShape(18.dp))
        }
        SkeletonList(count = 3)
    }
}

@Composable
private fun BadgeStatusBooking(c: CreatorModel) {
    val libur = !c.acceptingBookings
    Row(
        Modifier.clip(RoundedCornerShape(percent = 50))
            .background(if (libur) AppColors.Warning.copy(alpha = 0.15f) else AppColors.Success.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (libur) Icons.Default.EventBusy else Icons.Default.EventAvailable,
            contentDescription = null,
            tint = if (libur) AppColors.Warning else AppColors.Success,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            when {
                !libur -> "Menerima booking"
                c.awayUntil != null -> "Libur sampai ${Formatters.dateShort(c.awayUntil)}"
                else -> "Sedang libur"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (libur) AppColors.Warning else AppColors.Success,
        )
    }
    c.awayNote?.takeIf { libur && it.isNotBlank() }?.let {
        Spacer(Modifier.height(6.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
    }
}

@Composable
private fun ChipInfo(icon: ImageVector, teks: String) {
    Row(
        Modifier.clip(RoundedCornerShape(percent = 50)).background(AppColors.SurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(teks, style = MaterialTheme.typography.labelMedium, color = AppColors.TextPrimary)
    }
}

private fun ikonTab(index: Int): ImageVector = when (index) {
    0 -> Icons.Default.PhotoLibrary
    1 -> Icons.Default.GridView
    else -> Icons.Default.Inventory2
}

/** Online = aktif dalam 2 menit terakhir. */
private fun sedangOnline(c: CreatorModel): Boolean {
    val terakhir = c.lastActiveAt?.toDate()?.time ?: return false
    return System.currentTimeMillis() - terakhir < 2 * 60 * 1000
}

private fun statusKehadiran(c: CreatorModel): String {
    val terakhir = c.lastActiveAt?.toDate()?.time ?: return "Belum pernah aktif"
    val menit = (System.currentTimeMillis() - terakhir) / 60000
    return when {
        menit < 2 -> "Online"
        menit < 60 -> "Aktif $menit menit lalu"
        menit < 60 * 24 -> "Aktif ${menit / 60} jam lalu"
        else -> "Aktif ${menit / 1440} hari lalu"
    }
}

/**
 * Persentase booking yang selesai.
 *
 * Null kalau belum ada booking sama sekali — menampilkan "0% selesai" pada
 * creator baru akan menghukumnya karena belum sempat bekerja, bukan karena
 * kinerjanya buruk.
 */
private fun tingkatPenyelesaian(c: CreatorModel): Int? {
    if (c.totalBookings < 3) return null
    return (c.completedBookings * 100 / c.totalBookings).coerceIn(0, 100)
}

private fun durasiSingkat(menit: Int): String = when {
    menit < 60 -> "< $menit menit"
    menit < 60 * 24 -> "< ${menit / 60} jam"
    else -> "> 1 hari"
}
