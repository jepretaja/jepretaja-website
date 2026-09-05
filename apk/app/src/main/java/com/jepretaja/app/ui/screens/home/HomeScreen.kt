package com.jepretaja.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.AppConstants
import com.jepretaja.app.data.model.BookingModel
import com.jepretaja.app.data.model.CreatorModel
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.ui.components.BadgedIconButton
import com.jepretaja.app.ui.components.CreatorCard
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.SkeletonBox
import com.jepretaja.app.ui.components.SkeletonCreatorCard
import com.jepretaja.app.ui.components.StatusBadge
import com.jepretaja.app.ui.components.premiumShadow
import com.jepretaja.app.ui.components.rememberPinnedAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

/* --------------------------------------------------------------------------
 * Ukuran bersama
 *
 * Sebelumnya tiap seksi memilih angkanya sendiri: kartu creator 168dp, ubin
 * karya 150×200, jarak antar-item 10dp di kategori tapi 12dp di tempat lain,
 * dan jarak antar-seksi berkisar 20–28dp tergantung seksinya. Perbedaan sekecil
 * itu tidak pernah terbaca sebagai keputusan; ia terbaca sebagai halaman yang
 * dirakit potong-potong. Semua ukuran sekarang berasal dari satu tempat, jadi
 * mengubah ritme halaman berarti mengubah satu angka, bukan berburu di 400
 * baris.
 * ----------------------------------------------------------------------- */

private val PadTepi = 20.dp
private val JarakItem = 12.dp
private val JarakAntarSeksi = 28.dp
private val JarakJudulKeIsi = 12.dp

/** Lebar kartu mendatar — dipakai kartu creator DAN ubin karya. */
private val LebarKartu = 168.dp

/** 168 × 4/3. Rasio 3:4 yang sama dengan CreatorCard, supaya tiap baris
 *  mendatar di Home berhenti di garis bawah yang sama. */
private val TinggiKartu: Dp = 224.dp

private val SudutKartu = RoundedCornerShape(18.dp)

/**
 * Home Konsumen.
 *
 * Susunan seksinya: Creator Terdekat, Creator Populer, Rekomendasi, Karya
 * Terbaru, Kategori — masing-masing dengan judul dan satu kalimat yang
 * menyatakan atas dasar apa isinya dipilih. Kalimat itu bukan hiasan: tiga
 * seksi pertama sama-sama berisi deretan kartu creator, dan tanpa keterangan
 * pembeda ketiganya terbaca sebagai daftar yang sama diulang tiga kali.
 *
 * Setiap seksi punya tiga keadaan yang digambar berbeda — memuat, kosong, dan
 * gagal. Sebelumnya kegagalan jaringan tampil identik dengan "memang belum ada
 * isinya", tanpa satu pun tombol untuk mencoba lagi.
 */
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onNotifications: () -> Unit,
    onCreatorClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onSeeExplore: () -> Unit,
    onChat: () -> Unit = {},
    onBookingClick: (String) -> Unit = {},
    onPostClick: (String) -> Unit = {},
    authViewModel: AuthViewModel? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val terdekat by viewModel.nearbyCreators.collectAsState()
    val populer by viewModel.popularCreators.collectAsState()
    val rekomendasi by viewModel.recommendedCreators.collectAsState()
    val karyaTerbaru by viewModel.latestWorks.collectAsState()
    val activeBooking by viewModel.activeBooking.collectAsState()

    val authState = authViewModel?.uiState?.collectAsState()?.value
    LaunchedEffect(authState?.uid) { viewModel.setUserId(authState?.uid) }
    val creatorName by viewModel.creatorName.collectAsState()
    val unreadChats by viewModel.unreadChats.collectAsState()
    val unreadNotifications by viewModel.unreadNotifications.collectAsState()

    // Nama yang tampil di header: nama tampilan creator kalau akunnya creator,
    // kalau bukan nama profil biasa, dan "Tamu" untuk yang belum masuk.
    val namaProfil = authState?.profile?.name?.takeIf { it.isNotBlank() }
    val namaHeader = when {
        authState?.isLoggedIn != true -> "Tamu"
        !creatorName.isNullOrBlank() -> creatorName!!
        namaProfil != null -> namaProfil
        else -> "Tamu"
    }

    // Header Home sengaja TIDAK ikut menyingkir saat digulir: isinya bukan
    // sekadar judul, tapi juga pintasan Chat dan Notifikasi yang harus tetap
    // terjangkau selama pengguna menelusuri halaman.
    val scrollBehavior = rememberPinnedAppTopBarScrollBehavior()

    Scaffold(
        // nestedScroll tetap dipasang: dengan perilaku pinned, inilah yang
        // membuat warna latar header berubah begitu konten mulai bergulir.
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        namaHeader,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    BadgedIconButton(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = if (unreadChats > 0) "Chat, $unreadChats pesan baru" else "Chat",
                        count = unreadChats,
                        onClick = onChat,
                    )
                    BadgedIconButton(
                        icon = Icons.Default.NotificationsNone,
                        contentDescription = if (unreadNotifications > 0) {
                            "Notifikasi, $unreadNotifications baru"
                        } else {
                            "Notifikasi"
                        },
                        count = unreadNotifications,
                        onClick = onNotifications,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    scrolledContainerColor = AppColors.Surface,
                    titleContentColor = AppColors.TextPrimary,
                    actionIconContentColor = AppColors.TextPrimary,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            // --- Sapaan ---
            // Namanya sudah tampil di header, jadi baris ini tidak lagi
            // mengulangnya — dua kali nama yang sama di satu layar terbaca
            // seperti kelalaian, bukan sambutan. Judul besar Fraunces dipakai
            // SEKALI di halaman ini; header memakai titleLarge supaya tidak
            // ada dua tulisan yang sama-sama berebut jadi yang terbesar.
            Text(
                "Temukan fotografermu",
                style = MaterialTheme.typography.displaySmall,
                color = AppColors.TextPrimary,
                modifier = Modifier.padding(horizontal = PadTepi),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Siapa yang mengabadikan momenmu berikutnya?",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(horizontal = PadTepi),
            )

            // --- Pencarian ---
            Spacer(Modifier.height(18.dp))
            Surface(
                onClick = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PadTepi),
                shape = RoundedCornerShape(percent = 50),
                color = AppColors.SurfaceVariant,
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.TextSecondary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Cari fotografer, wedding, kota...",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // --- Booking yang sedang berjalan ---
            activeBooking?.let { booking ->
                Spacer(Modifier.height(20.dp))
                ActiveBookingCard(booking = booking, onClick = { onBookingClick(booking.bookingId) })
            }

            // --- 1. Creator Terdekat ---
            Seksi(
                judul = "Creator Terdekat",
                subjudul = "Ada di kotamu, gampang diajak ketemu",
            ) {
                BarisCreator(
                    seksi = terdekat,
                    kosongJudul = "Belum ada creator di sekitarmu",
                    kosongKeterangan = "Coba lihat Creator Populer di bawah, banyak yang melayani luar kota.",
                    onCreatorClick = onCreatorClick,
                    onCobaLagi = viewModel::muatUlang,
                )
            }

            // --- 2. Creator Populer ---
            Seksi(
                judul = "Creator Populer",
                subjudul = "Rating tertinggi minggu ini",
            ) {
                BarisCreator(
                    seksi = populer,
                    kosongJudul = "Belum ada creator populer",
                    kosongKeterangan = "Peringkat muncul setelah ada ulasan pertama masuk.",
                    onCreatorClick = onCreatorClick,
                    onCobaLagi = viewModel::muatUlang,
                )
            }

            // --- 3. Rekomendasi ---
            Seksi(
                judul = "Rekomendasi",
                subjudul = if (viewModel.punyaMinat) {
                    "Dipilih dari kategori yang kamu minati"
                } else {
                    "Terverifikasi dan sedang menerima booking"
                },
            ) {
                BarisCreator(
                    seksi = rekomendasi,
                    kosongJudul = "Belum ada rekomendasi",
                    kosongKeterangan = "Rekomendasi menajam setelah kamu menelusuri beberapa creator.",
                    onCreatorClick = onCreatorClick,
                    onCobaLagi = viewModel::muatUlang,
                )
            }

            // --- 4. Karya Terbaru ---
            Seksi(
                judul = "Karya Terbaru",
                subjudul = "Baru diunggah creator",
                labelAksi = "Lihat semua",
                onAksi = onSeeExplore,
            ) {
                BarisKarya(
                    seksi = karyaTerbaru,
                    onPostClick = onPostClick,
                    onSeeExplore = onSeeExplore,
                    onCobaLagi = viewModel::muatUlang,
                )
            }

            // --- 5. Kategori ---
            Seksi(
                judul = "Kategori",
                subjudul = "Telusuri berdasarkan jenis layanan",
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = PadTepi),
                    horizontalArrangement = Arrangement.spacedBy(JarakItem),
                ) {
                    items(AppConstants.SERVICE_CATEGORIES) { kategori ->
                        CategoryTile(label = kategori, onClick = { onCategoryClick(kategori) })
                    }
                }
            }

            // Ruang ekstra di bawah supaya isi terakhir tidak tertutup bilah
            // menu yang melayang.
            Spacer(Modifier.height(110.dp))
        }
    }
}

/* --------------------------------------------------------------------------
 * Kerangka seksi
 * ----------------------------------------------------------------------- */

/**
 * Pembungkus satu seksi: jarak atas, judul, jarak judul-ke-isi, lalu isinya.
 *
 * Semua seksi melewati fungsi ini supaya ritme vertikalnya identik. Sebelumnya
 * tiap seksi menuliskan sendiri `Spacer(28.dp)` lalu `SectionHeader` lalu
 * `Spacer(12.dp)`, dan satu-dua di antaranya memakai angka yang berbeda tanpa
 * alasan.
 */
@Composable
private fun Seksi(
    judul: String,
    subjudul: String,
    labelAksi: String? = null,
    onAksi: (() -> Unit)? = null,
    isi: @Composable () -> Unit,
) {
    Spacer(Modifier.height(JarakAntarSeksi))
    SectionHeader(title = judul, subtitle = subjudul, actionLabel = labelAksi, onAction = onAksi)
    Spacer(Modifier.height(JarakJudulKeIsi))
    isi()
}

/**
 * Kartu pemberitahuan seragam untuk keadaan kosong dan gagal.
 *
 * Keduanya memakai bentuk yang sama supaya halaman tetap terbaca rapi ketika
 * beberapa seksi sekaligus tidak punya isi — tapi ikon, warna, dan tombolnya
 * berbeda, karena "belum ada isinya" tidak menuntut apa pun dari pengguna
 * sementara "gagal memuat" menuntut satu ketukan.
 */
@Composable
private fun KartuInfoSeksi(
    ikon: ImageVector,
    judul: String,
    keterangan: String,
    warnaIkon: Color,
    labelAksi: String? = null,
    onAksi: (() -> Unit)? = null,
) {
    PremiumCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PadTepi),
        elevation = 3.dp,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(MaterialTheme.shapes.medium).background(warnaIkon.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(ikon, contentDescription = null, tint = warnaIkon, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(judul, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(keterangan, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
        if (labelAksi != null && onAksi != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAksi) { Text(labelAksi) }
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Baris creator
 * ----------------------------------------------------------------------- */

@Composable
private fun BarisCreator(
    seksi: SeksiHome<List<CreatorModel>>,
    kosongJudul: String,
    kosongKeterangan: String,
    onCreatorClick: (String) -> Unit,
    onCobaLagi: () -> Unit,
) {
    when (seksi) {
        // Kerangka seukuran kartu asli supaya tata letak tidak melompat begitu
        // data tiba.
        is SeksiHome.Memuat -> LazyRow(
            contentPadding = PaddingValues(horizontal = PadTepi),
            horizontalArrangement = Arrangement.spacedBy(JarakItem),
            userScrollEnabled = false,
        ) {
            items(3) { SkeletonCreatorCard(Modifier.width(LebarKartu)) }
        }

        is SeksiHome.Gagal -> KartuInfoSeksi(
            ikon = Icons.Default.CloudOff,
            judul = "Gagal memuat creator",
            keterangan = seksi.pesan,
            warnaIkon = AppColors.Danger,
            labelAksi = "Coba lagi",
            onAksi = onCobaLagi,
        )

        is SeksiHome.Isi -> if (seksi.data.isEmpty()) {
            KartuInfoSeksi(
                ikon = Icons.Default.PersonSearch,
                judul = kosongJudul,
                keterangan = kosongKeterangan,
                warnaIkon = AppColors.TextSecondary,
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = PadTepi),
                horizontalArrangement = Arrangement.spacedBy(JarakItem),
            ) {
                items(seksi.data) { creator ->
                    CreatorCard(
                        creator = creator,
                        onClick = { onCreatorClick(creator.creatorId) },
                        modifier = Modifier.width(LebarKartu),
                    )
                }
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Baris karya
 * ----------------------------------------------------------------------- */

@Composable
private fun BarisKarya(
    seksi: SeksiHome<List<ExplorePostModel>>,
    onPostClick: (String) -> Unit,
    onSeeExplore: () -> Unit,
    onCobaLagi: () -> Unit,
) {
    when (seksi) {
        is SeksiHome.Memuat -> LazyRow(
            contentPadding = PaddingValues(horizontal = PadTepi),
            horizontalArrangement = Arrangement.spacedBy(JarakItem),
            userScrollEnabled = false,
        ) {
            items(3) { SkeletonBox(Modifier.width(LebarKartu).height(TinggiKartu), SudutKartu) }
        }

        is SeksiHome.Gagal -> KartuInfoSeksi(
            ikon = Icons.Default.CloudOff,
            judul = "Gagal memuat karya",
            keterangan = seksi.pesan,
            warnaIkon = AppColors.Danger,
            labelAksi = "Coba lagi",
            onAksi = onCobaLagi,
        )

        is SeksiHome.Isi -> if (seksi.data.isEmpty()) {
            KartuInfoSeksi(
                ikon = Icons.Default.PhotoLibrary,
                judul = "Belum ada karya tayang",
                keterangan = "Begitu creator mulai mengunggah, karyanya muncul di sini.",
                warnaIkon = AppColors.TextSecondary,
                labelAksi = "Buka Explore",
                onAksi = onSeeExplore,
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = PadTepi),
                horizontalArrangement = Arrangement.spacedBy(JarakItem),
            ) {
                items(seksi.data) { post ->
                    UbinKarya(post = post, onClick = { onPostClick(post.postId) })
                }
            }
        }
    }
}

/** Satu ubin karya — ukuran & sudutnya sama persis dengan kartu creator. */
@Composable
private fun UbinKarya(post: ExplorePostModel, onClick: () -> Unit) {
    Box(
        Modifier
            .width(LebarKartu)
            .height(TinggiKartu)
            .premiumShadow(8.dp, SudutKartu)
            .clip(SudutKartu)
            .background(AppColors.SurfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = post.thumbnailUrl ?: post.mediaUrls.firstOrNull(),
            contentDescription = post.caption.ifBlank { "Karya ${post.creatorName}" },
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))
                ),
        )
        if (post.type == "video") {
            Icon(
                Icons.Default.PlayArrow, contentDescription = "Video", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(percent = 50))
                    .padding(4.dp).size(16.dp),
            )
        }
        Text(
            "@${post.creatorName}",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

/* --------------------------------------------------------------------------
 * Sisanya
 * ----------------------------------------------------------------------- */

/** Pengingat booking yang masih berjalan — aksi paling mendesak di layar ini. */
@Composable
private fun ActiveBookingCard(booking: BookingModel, onClick: () -> Unit) {
    PremiumCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PadTepi),
        onClick = onClick,
        elevation = 6.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(AppColors.PrimarySoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.EventAvailable, contentDescription = null, tint = AppColors.Primary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Booking berjalan", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    booking.packageName.ifBlank { "Lihat detail booking" },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            StatusBadge(booking.status)
        }
    }
}

/** Satu ubin kategori: ikon dalam wadah lembut + label. */
@Composable
private fun CategoryTile(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(AppColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ikonKategori(label), contentDescription = null,
                tint = AppColors.Primary, modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Ikon per kategori layanan; kategori tak dikenal jatuh ke ikon kamera. */
private fun ikonKategori(kategori: String): ImageVector = when (kategori) {
    "Wedding" -> Icons.Default.Favorite
    "Prewedding" -> Icons.Default.FavoriteBorder
    "Event" -> Icons.Default.Celebration
    "Wisuda" -> Icons.Default.School
    "Couple" -> Icons.Default.People
    "Family" -> Icons.Default.Groups
    "Product" -> Icons.Default.Inventory2
    "Commercial" -> Icons.Default.Storefront
    "Video" -> Icons.Default.Videocam
    else -> Icons.Default.PhotoCamera
}
