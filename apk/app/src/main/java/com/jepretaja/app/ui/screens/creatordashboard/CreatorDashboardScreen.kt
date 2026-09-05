package com.jepretaja.app.ui.screens.creatordashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.navigation.Routes
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.GradientHeroCard
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.StatTile
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

private data class DashMenu(val icon: ImageVector, val label: String, val route: String)

private data class AksiCepat(
    val ikon: ImageVector,
    val label: String,
    val route: String,
    val warna: Color,
)

/** 1.234 -> "1,2rb" — angka besar dibuat ringkas supaya muat di ubin statistik. */
private fun ringkasAngka(nilai: Long): String = when {
    nilai >= 1_000_000 -> "%.1fjt".format(nilai / 1_000_000.0)
    nilai >= 1_000 -> "%.1frb".format(nilai / 1_000.0)
    else -> nilai.toString()
}

/** "Rp1,5jt" — untuk ubin dan sumbu grafik yang ruangnya sempit. */
private fun ringkasRupiah(nilai: Long): String = when {
    nilai >= 1_000_000_000 -> "Rp%.1fm".format(nilai / 1_000_000_000.0).replace('.', ',')
    nilai >= 1_000_000 -> "Rp%.1fjt".format(nilai / 1_000_000.0).replace('.', ',')
    nilai >= 1_000 -> "Rp%.0frb".format(nilai / 1_000.0)
    else -> "Rp$nilai"
}

/**
 * Creator Studio.
 *
 * **Apa yang berubah.** Sebelumnya layar ini praktis hanya kartu saldo dan
 * sekumpulan pintu menu. Itu membuatnya berfungsi sebagai daftar isi, bukan
 * dashboard: creator yang membukanya tidak bisa menjawab satu pun pertanyaan
 * yang sebenarnya ia bawa ke sini — *ada pesanan baru tidak?*, *bulan ini lebih
 * baik atau lebih buruk?* — tanpa terlebih dulu menekan salah satu menunya dan
 * membaca daftar di baliknya.
 *
 * Sekarang lima angka yang menentukan ditaruh di depan (booking baru, booking
 * aktif, pendapatan, saldo tersedia, rating), disusul grafik pendapatan enam
 * bulan, lalu empat aksi yang paling sering dikerjakan. Menu pengelolaan
 * lengkap tetap ada, tapi turun ke bawah — ia jawaban untuk "saya mau ke mana",
 * bukan untuk "bagaimana keadaan usaha saya".
 *
 * **Kartu yang bisa diketuk.** Tiap ubin statistik menuju daftar di baliknya:
 * angka yang menyatakan ada tiga pesanan baru tapi tidak bisa membawa creator
 * ke tiga pesanan itu hanya memindahkan pekerjaan mencarinya.
 */
@Composable
fun CreatorDashboardScreen(
    onNotifications: () -> Unit,
    onNavigate: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorDashboardViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid

    val menu = listOf(
        DashMenu(Icons.Default.PhotoCamera, "Kelola Portfolio", Routes.CREATOR_PORTFOLIO_MANAGEMENT),
        DashMenu(Icons.Default.Inventory2, "Kelola Paket", Routes.CREATOR_PACKAGE_MANAGEMENT),
        DashMenu(Icons.Default.CalendarMonth, "Kelola Booking", Routes.CREATOR_BOOKING_MANAGEMENT),
        DashMenu(Icons.Default.AccountBalanceWallet, "Wallet", Routes.CREATOR_WALLET),
        // Diberi pintu sendiri, bukan disembunyikan di dalam form penarikan:
        // rekening perlu diisi SEBELUM ada saldo yang ditarik, dan creator yang
        // baru bergabung tidak akan menemukannya kalau satu-satunya jalan ke
        // sana adalah layar yang hanya berguna ketika saldonya sudah cukup.
        DashMenu(Icons.Default.AccountBalance, "Rekening Bank", Routes.CREATOR_BANK_ACCOUNT),
        DashMenu(Icons.Default.EventAvailable, "Ketersediaan", Routes.CREATOR_AVAILABILITY),
        // Ulasan yang DITERIMA creator: sebelumnya tidak ada jalan sama sekali
        // untuk melihatnya dari aplikasi, padahal panel web punya halamannya.
        DashMenu(Icons.Default.StarRate, "Ulasan Saya", "creator_reviews"),
        DashMenu(Icons.Default.Settings, "Pengaturan", Routes.CREATOR_SETTINGS),
    )

    val aksiCepat = listOf(
        AksiCepat(Icons.Default.AddAPhoto, "Tambah Karya", Routes.CREATOR_UPLOAD, AppColors.Primary),
        AksiCepat(Icons.Default.Inventory2, "Tambah Paket", Routes.CREATOR_PACKAGE_MANAGEMENT, AppColors.Info),
        AksiCepat(Icons.Default.EditCalendar, "Atur Jadwal", Routes.CREATOR_AVAILABILITY, AppColors.Warning),
        AksiCepat(Icons.Default.Payments, "Tarik Saldo", Routes.CREATOR_WITHDRAWAL, AppColors.Success),
    )

    val context = LocalContext.current
    val adminUrl = com.jepretaja.app.BuildConfig.API_BASE_URL.trimEnd('/')
    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppColors.Background,
        topBar = {
            AppTopBar(
                title = "Creator Studio",
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = onNotifications) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = "Notifikasi")
                    }
                },
            )
        },
    ) { padding ->
        if (uid == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Masuk sebagai creator untuk membuka Studio", color = AppColors.TextSecondary)
            }
            return@Scaffold
        }

        val wallet by remember(uid) { viewModel.wallet(uid) }.collectAsState(initial = null)
        val creator by remember(uid) { viewModel.creator(uid) }.collectAsState(initial = null)
        val bookings by remember(uid) { viewModel.bookings(uid) }.collectAsState(initial = emptyList())
        val pendapatan by remember(uid) { viewModel.pendapatanBulanan(uid) }.collectAsState(initial = emptyList())
        val posts by remember(uid) { viewModel.myPosts(uid) }.collectAsState(initial = emptyList())

        val bookingBaru = bookings.count { it.status in CreatorDashboardViewModel.BARU }
        val bookingAktif = bookings.count { it.status in CreatorDashboardViewModel.AKTIF }

        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {

            Spacer(Modifier.height(4.dp))

            // --- Saldo ---
            GradientHeroCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "Saldo Tersedia",
                    color = AppColors.OnPrimary.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    Formatters.currency(wallet?.availableBalance ?: 0),
                    color = AppColors.OnPrimary,
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(14.dp))
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Menunggu pencairan",
                            color = AppColors.OnPrimary.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            Formatters.currency(wallet?.pendingBalance ?: 0),
                            color = AppColors.OnPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Sudah ditarik",
                            color = AppColors.OnPrimary.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            Formatters.currency(wallet?.withdrawn ?: 0),
                            color = AppColors.OnPrimary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onNavigate(Routes.CREATOR_WITHDRAWAL) },
                        enabled = (wallet?.availableBalance ?: 0) > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.OnPrimary,
                            contentColor = AppColors.Primary,
                        ),
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.weight(1f),
                    ) { Text("Tarik Saldo") }
                    OutlinedButton(
                        onClick = { onNavigate(Routes.CREATOR_WALLET) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.OnPrimary),
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.weight(1f),
                    ) { Text("Riwayat") }
                }
            }

            // --- Aksi cepat ---
            // Diletakkan tinggi, tepat di bawah saldo. Empat hal ini adalah yang
            // paling sering dikerjakan creator dan sebelumnya masing-masing
            // butuh mencari kartunya di grid delapan menu di bawah — kecuali
            // "Tarik Saldo", yang bahkan tidak punya jalan langsung sama sekali.
            Spacer(Modifier.height(22.dp))
            SectionHeader("Aksi Cepat")
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                aksiCepat.forEach { aksi ->
                    TombolAksiCepat(
                        aksi = aksi,
                        onClick = { onNavigate(aksi.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // --- Statistik ---
            Spacer(Modifier.height(26.dp))
            SectionHeader("Ringkasan", subtitle = "Keadaan usahamu hari ini")
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile(
                    icon = Icons.Default.MarkEmailUnread,
                    value = "$bookingBaru",
                    label = "Booking baru",
                    // Warna berubah hanya kalau memang ada yang menunggu.
                    // Ubin yang selalu oranye berhenti berarti apa-apa.
                    tint = if (bookingBaru > 0) AppColors.Warning else AppColors.TextSecondary,
                    modifier = Modifier.weight(1f).clickableTile { onNavigate(Routes.CREATOR_BOOKING_MANAGEMENT) },
                )
                StatTile(
                    icon = Icons.Default.EventAvailable,
                    value = "$bookingAktif",
                    label = "Booking aktif",
                    tint = AppColors.Info,
                    modifier = Modifier.weight(1f).clickableTile { onNavigate(Routes.CREATOR_BOOKING_MANAGEMENT) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile(
                    icon = Icons.Default.TrendingUp,
                    value = ringkasRupiah(wallet?.totalEarnings ?: 0),
                    label = "Pendapatan",
                    tint = AppColors.Success,
                    modifier = Modifier.weight(1f).clickableTile { onNavigate(Routes.CREATOR_WALLET) },
                )
                StatTile(
                    icon = Icons.Default.AccountBalanceWallet,
                    value = ringkasRupiah(wallet?.availableBalance ?: 0),
                    label = "Saldo tersedia",
                    tint = AppColors.Primary,
                    modifier = Modifier.weight(1f).clickableTile { onNavigate(Routes.CREATOR_WALLET) },
                )
                StatTile(
                    icon = Icons.Default.Star,
                    value = if ((creator?.reviewCount ?: 0) == 0) "–" else "%.1f".format(creator?.rating ?: 0.0),
                    label = "${creator?.reviewCount ?: 0} ulasan",
                    tint = AppColors.Warning,
                    modifier = Modifier.weight(1f).clickableTile { onNavigate(Routes.reviews(uid)) },
                )
            }

            // --- Grafik pendapatan ---
            Spacer(Modifier.height(26.dp))
            SectionHeader("Pendapatan", subtitle = "Enam bulan terakhir, setelah biaya layanan")
            Spacer(Modifier.height(12.dp))
            GrafikPendapatan(
                data = pendapatan,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            // --- Performa karya ---
            // Angka ini sudah lama tersimpan di metrics tiap post tapi tidak
            // pernah ditampilkan di mana pun, sehingga creator tidak punya cara
            // mengetahui apakah karyanya benar-benar dilihat orang.
            Spacer(Modifier.height(26.dp))
            SectionHeader("Performa Karya", subtitle = "${posts.size} post tayang di Explore")
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatTile(
                    icon = Icons.Default.Visibility,
                    value = ringkasAngka(posts.sumOf { it.viewCount }),
                    label = "Tayangan",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Default.Favorite,
                    value = ringkasAngka(posts.sumOf { it.likeCount }),
                    label = "Suka",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Default.ModeComment,
                    value = ringkasAngka(posts.sumOf { it.commentCount }),
                    label = "Komentar",
                    modifier = Modifier.weight(1f),
                )
            }

            // --- Menu lengkap ---
            Spacer(Modifier.height(26.dp))
            SectionHeader("Kelola Bisnis")
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Tinggi dihitung dari jumlah menu, bukan angka tetap: grid ini
                // tidak bisa menggulir sendiri (ia ada di dalam Column yang
                // menggulir), jadi tinggi yang dipatok akan memotong baris
                // terakhir diam-diam setiap kali satu menu ditambahkan.
                modifier = Modifier.padding(horizontal = 16.dp).height(((menu.size + 1) / 2 * 118).dp),
                userScrollEnabled = false,
            ) {
                items(menu) { item ->
                    PremiumCard(
                        onClick = {
                            // "Ulasan Saya" memakai layar ulasan publik, jadi
                            // rutenya perlu id creator yang sedang masuk.
                            if (item.route == "creator_reviews") onNavigate(Routes.reviews(uid))
                            else onNavigate(item.route)
                        },
                        elevation = 3.dp,
                    ) {
                        Box(
                            Modifier.size(38.dp).clip(MaterialTheme.shapes.small).background(AppColors.PrimarySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                item.icon, contentDescription = null,
                                tint = AppColors.Primary, modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(item.label, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Jembatan ke panel web creator (Vercel) — halaman /creator di sana
            // punya laporan & pengelolaan yang lebih lengkap daripada yang muat
            // di layar HP, jadi keduanya saling melengkapi, bukan menggantikan.
            Spacer(Modifier.height(26.dp))
            SectionHeader("Panel Web Creator", subtitle = "Laporan & pengelolaan versi lengkap")
            Spacer(Modifier.height(12.dp))
            PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), elevation = 4.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(MaterialTheme.shapes.medium).background(AppColors.PrimarySoft),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Default.Language, contentDescription = null, tint = AppColors.Primary) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Buka di Browser", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (adminUrl.isBlank()) "Alamat panel belum diatur" else "$adminUrl/creator",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (adminUrl.isBlank()) {
                    // Jujur soal keadaannya: tanpa API_BASE_URL tombol ini tidak
                    // bisa membuka apa pun, jadi lebih baik menjelaskan daripada
                    // memberi tombol yang diam saja saat ditekan.
                    Text(
                        "API_BASE_URL belum diisi di gradle.properties, jadi panel web belum bisa dibuka dari aplikasi.",
                        style = MaterialTheme.typography.bodySmall, color = AppColors.Warning,
                    )
                } else {
                    Button(
                        onClick = {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("$adminUrl/creator"),
                            )
                            runCatching { context.startActivity(intent) }
                        },
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Primary,
                            contentColor = AppColors.OnPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Buka Panel Web Creator")
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

/* --------------------------------------------------------------------------
 * Potongan
 * ----------------------------------------------------------------------- */

/** Riak sentuh untuk ubin statistik, tanpa mengubah tampilannya. */
private fun Modifier.clickableTile(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

@Composable
private fun TombolAksiCepat(aksi: AksiCepat, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(aksi.warna.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) { Icon(aksi.ikon, contentDescription = null, tint = aksi.warna, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.height(7.dp))
        Text(
            aksi.label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Grafik batang pendapatan enam bulan.
 *
 * Digambar dengan Box berbobot, bukan pustaka grafik: satu-satunya bentuk yang
 * dibutuhkan di sini adalah enam batang sebanding, dan menambah dependensi
 * grafik penuh untuk itu berarti menyeret ratusan kilobyte ke dalam APK demi
 * sesuatu yang selesai dalam tiga puluh baris.
 *
 * Nilai tertinggi jadi acuan tinggi. Sumbu yang dimulai dari nol dan berskala
 * relatif seperti ini tidak bisa melebih-lebihkan perbedaan — hal yang justru
 * gampang terjadi pada grafik yang sumbunya dipotong.
 */
@Composable
private fun GrafikPendapatan(data: List<BulanPendapatan>, modifier: Modifier = Modifier) {
    PremiumCard(modifier = modifier, elevation = 3.dp) {
        val tertinggi = data.maxOfOrNull { it.nilai } ?: 0L
        val total = data.sumOf { it.nilai }

        if (data.isEmpty() || total == 0L) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.ShowChart, contentDescription = null,
                    tint = AppColors.TextSecondary, modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("Belum ada pemasukan", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                Text(
                    // Menjelaskan kapan angkanya muncul, bukan cuma bahwa ia
                    // belum ada — jeda antara booking selesai dan dana masuk
                    // adalah pertanyaan yang pasti timbul di sini.
                    "Pendapatan tercatat setelah dana booking dilepas ke walletmu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {

        Text("Total 6 bulan", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
        Text(
            Formatters.currency(total),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W700,
            color = AppColors.TextPrimary,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEachIndexed { i, bulan ->
                val terakhir = i == data.lastIndex
                val rasio = if (tertinggi == 0L) 0f else (bulan.nilai.toFloat() / tertinggi)
                // Batang nol tetap digambar setipis 2dp supaya bulan tanpa
                // pemasukan terlihat sebagai bulan yang ada, bukan celah.
                val tinggi by animateFloatAsState(
                    targetValue = rasio.coerceAtLeast(0.015f),
                    label = "tinggi_batang_$i",
                )

                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Hanya bulan berjalan yang diberi angka. Enam label rupiah
                    // berjajar di lebar layar HP akan terpotong semua, dan
                    // angka terpotong lebih buruk daripada tidak ada angka —
                    // perbandingan antar-bulan sudah dikerjakan tinggi batangnya.
                    Text(
                        if (terakhir && bulan.nilai > 0L) ringkasRupiah(bulan.nilai) else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.Primary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    // Wadah batang mengambil sisa tinggi lewat weight, dan
                    // batangnya mengisi pecahan DARI WADAH ITU. Kalau tingginya
                    // dihitung langsung terhadap tinggi baris, label bulan di
                    // bawah akan terdorong keluar setiap kali ada batang penuh.
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(tinggi)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                // Bulan berjalan diberi warna penuh; lima bulan
                                // sebelumnya lebih redup, karena yang dibandingkan
                                // orang adalah "sekarang terhadap sebelumnya".
                                .background(if (terakhir) AppColors.Primary else AppColors.PrimarySoft),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        bulan.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (terakhir) AppColors.TextPrimary else AppColors.TextSecondary,
                    )
                }
            }
        }
        }
    }
}
