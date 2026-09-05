package com.jepretaja.app.ui.screens.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.model.ReviewModel
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.QrCode
import com.jepretaja.app.ui.components.badgeLabel
import com.jepretaja.app.ui.state.AuthViewModel
import kotlinx.coroutines.flow.flowOf

/** Lima bagian profil. Urutannya sekaligus urutan tab di layar. */
private enum class BagianProfil(val judul: String, val ikon: ImageVector) {
    PROFIL("Profil", Icons.Default.PersonOutline),
    AKTIVITAS("Aktivitas", Icons.Default.History),
    KARYA("Karya", Icons.Default.GridView),
    REVIEW("Review", Icons.Default.StarBorder),
    PENGATURAN("Pengaturan", Icons.Default.Settings),
}

/** Sub-tab di dalam bagian Karya — tiga hal yang sama-sama berupa grid post. */
private enum class KoleksiKarya(val judul: String) { MILIKKU("Karya saya"), TERSIMPAN("Tersimpan"), DISUKAI("Disukai") }

/**
 * Profil pengguna.
 *
 * **Apa yang diperbaiki.** Isi halaman ini sebelumnya terbelah dua tanpa aturan
 * yang jelas: sebagian tampil di badan halaman (identitas, tiga grid post),
 * sisanya — empat belas baris menu tanpa pengelompokan apa pun, dari "Booking
 * Saya" sampai "Hapus Akun" — ditumpuk dalam satu bottom sheet yang harus
 * digulir. Menaruh "Riwayat Tontonan" bersebelahan dengan "Hapus Akun" dalam
 * satu daftar rata membuat keduanya tampak sama beratnya.
 *
 * Sekarang isinya dipisah jadi lima bagian dengan tab: Profil (identitas dan
 * detail akun), Aktivitas (booking, koleksi, jejak), Karya (tiga grid),
 * Review, dan Pengaturan. Menu setelan dikelompokkan berjudul, dan aksi
 * berbahaya diberi kelompoknya sendiri di paling bawah.
 *
 * **Konfirmasi untuk aksi berbahaya.** Keluar sebelumnya langsung dieksekusi
 * dari satu ketukan di daftar, tanpa pertanyaan apa pun — satu jari yang
 * meleset di sheet yang sedang digulir cukup untuk mengeluarkan orang dari
 * akunnya. Hapus akun punya dialog, tapi tombol merahnya bisa ditekan seketika.
 * Keduanya kini berdialog, dan hapus akun menuntut pengguna mengetik ulang kata
 * konfirmasi sebelum tombolnya hidup.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogin: () -> Unit,
    onMyBookings: () -> Unit,
    onFavorites: () -> Unit,
    onMyReviews: () -> Unit,
    onReports: () -> Unit,
    onNotifications: () -> Unit,
    onHelp: () -> Unit,
    onLoggedOut: () -> Unit,
    authViewModel: AuthViewModel,
    onEditProfile: () -> Unit = {},
    onSaved: () -> Unit = {},
    onChat: () -> Unit = {},
    onCreatorStudio: () -> Unit = {},
    onPostClick: (String) -> Unit = {},
    /** [tab]: 0 = Mengikuti, 1 = Pengikut. */
    onFollowList: (Int) -> Unit = {},
    onMyWorks: () -> Unit = {},
    onInterests: () -> Unit = {},
    onWatchHistory: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid

    // Dokumen creator dan karya sendiri hanya diambil untuk akun creator —
    // konsumen tidak punya dokumen creator sama sekali.
    val creator by remember(uid, authState.isCreator) {
        if (uid != null && authState.isCreator) viewModel.creator(uid) else flowOf(null)
    }.collectAsState(initial = null)
    val myPosts by remember(uid, authState.isCreator) {
        if (uid != null && authState.isCreator) viewModel.myPosts(uid) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val savedPosts by remember(uid) {
        if (uid != null) viewModel.savedPosts(uid) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val likedPosts by remember(uid) {
        if (uid != null) viewModel.likedPosts(uid) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val followingCount by remember(uid) {
        if (uid != null) viewModel.followingCount(uid) else flowOf(0)
    }.collectAsState(initial = 0)
    val myBookings by remember(uid, authState.isCreator) {
        if (uid != null && !authState.isCreator) viewModel.myBookings(uid) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    val ulasan by remember(uid, authState.isCreator) {
        if (uid != null) viewModel.myReviews(uid, authState.isCreator) else flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    val unreadChats by remember(uid) {
        if (uid != null) viewModel.unreadChats(uid) else flowOf(0)
    }.collectAsState(initial = 0)
    val unreadNotifications by remember(uid) {
        if (uid != null) viewModel.unreadNotifications(uid) else flowOf(0)
    }.collectAsState(initial = 0)

    // rememberSaveable, bukan remember: memutar layar atau membiarkan aplikasi
    // di latar belakang cukup lama akan mengembalikan pengguna ke tab Profil
    // dari mana pun ia berada. Enum tidak bisa langsung disimpan di Bundle,
    // jadi yang disimpan indeksnya.
    var indeksBagian by rememberSaveable { mutableIntStateOf(0) }
    var indeksKoleksi by rememberSaveable { mutableIntStateOf(0) }
    val bagian = BagianProfil.entries[indeksBagian.coerceIn(BagianProfil.entries.indices)]
    val koleksi = KoleksiKarya.entries[indeksKoleksi.coerceIn(KoleksiKarya.entries.indices)]

    var showQr by remember { mutableStateOf(false) }
    var dialogKeluar by remember { mutableStateOf(false) }
    var dialogHapus by remember { mutableStateOf(false) }
    var dialogMasukUlang by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var deleting by remember { mutableStateOf(false) }
    var verifyBusy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }

    Scaffold(
        containerColor = AppColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!authState.isLoggedIn) {
            GuestProfile(modifier = Modifier.padding(padding), onLogin = onLogin)
            return@Scaffold
        }

        val nama = authState.profile?.name ?: "-"
        val username = "@" + (authState.profile?.name ?: "pengguna").lowercase().replace(" ", "")

        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.size(44.dp))
                Text(
                    nama,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W700,
                    color = AppColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                // Ikon roda gigi, bukan ☰: sekarang ia memilih tab Pengaturan,
                // bukan membuka laci berisi segala hal.
                IconButton(onClick = { indeksBagian = BagianProfil.PENGATURAN.ordinal }) {
                    Icon(Icons.Default.Settings, contentDescription = "Pengaturan", tint = AppColors.TextPrimary)
                }
            }

            // Berpindah bagian mengembalikan gulir ke atas. Tanpa ini, menekan
            // "Pengaturan" setelah menggulir jauh di grid Karya menampilkan
            // bagian baru yang sudah tergulir separuh — judul kelompoknya di
            // atas layar dan yang terlihat pertama justru Zona Berbahaya.
            val gulir = rememberScrollState()
            LaunchedEffect(indeksBagian) { gulir.animateScrollTo(0) }

            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(gulir)) {

                // Identitas tetap terlihat di semua tab. Ia bukan salah satu
                // bagian, melainkan subjek yang dibicarakan kelima bagian itu.
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppAvatar(
                        url = authState.profile?.photoUrl,
                        name = nama,
                        size = 96.dp,
                        verified = creator?.verified == true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(username, style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)

                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        // Angka yang punya daftar di baliknya dibuat bisa
                        // diketuk; yang tidak, tidak diberi riak sentuhan.
                        if (authState.isCreator) {
                            ProfileStat("${creator?.followerCount ?: 0}", "Pengikut") { onFollowList(1) }
                            StatDivider()
                            ProfileStat("$followingCount", "Mengikuti") { onFollowList(0) }
                            StatDivider()
                            ProfileStat(String.format("%.1f", creator?.rating ?: 0.0), "Rating") {
                                indeksBagian = BagianProfil.REVIEW.ordinal
                            }
                        } else {
                            ProfileStat("$followingCount", "Mengikuti") { onFollowList(0) }
                            StatDivider()
                            ProfileStat("${savedPosts.size}", "Tersimpan") {
                                indeksBagian = BagianProfil.KARYA.ordinal
                                indeksKoleksi = KoleksiKarya.TERSIMPAN.ordinal
                            }
                            StatDivider()
                            ProfileStat("${myBookings.size}", "Booking", onMyBookings)
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier.weight(1f).height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.SurfaceVariant)
                                .clickable(onClick = onEditProfile),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Edit profil",
                                style = MaterialTheme.typography.labelLarge,
                                color = AppColors.TextPrimary,
                            )
                        }
                        SquareIconButton(Icons.AutoMirrored.Filled.Chat, "Chat", unreadChats, onChat)
                        SquareIconButton(Icons.Default.NotificationsNone, "Notifikasi", unreadNotifications, onNotifications)
                    }
                }

                Spacer(Modifier.height(16.dp))

                ScrollableTabRow(
                    selectedTabIndex = bagian.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = AppColors.Primary,
                    edgePadding = 12.dp,
                    divider = { HorizontalDivider(color = AppColors.Border) },
                ) {
                    BagianProfil.entries.forEach { b ->
                        Tab(
                            selected = bagian == b,
                            onClick = { indeksBagian = b.ordinal },
                            text = { Text(b.judul, style = MaterialTheme.typography.labelLarge, maxLines = 1) },
                            icon = { Icon(b.ikon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            selectedContentColor = AppColors.Primary,
                            unselectedContentColor = AppColors.TextSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                when (bagian) {
                    BagianProfil.PROFIL -> BagianProfilIsi(
                        nama = nama,
                        email = authState.profile?.email.orEmpty(),
                        telepon = authState.profile?.phone,
                        emailTerverifikasi = authState.emailVerified,
                        isCreator = authState.isCreator,
                        bio = creator?.bio,
                        verifyBusy = verifyBusy,
                        onEditProfile = onEditProfile,
                        onBagikan = { showQr = true },
                        onCreatorStudio = onCreatorStudio,
                        onKirimVerifikasi = {
                            verifyBusy = true
                            authViewModel.resendVerificationEmail { pesan ->
                                verifyBusy = false
                                errorMessage = pesan
                            }
                        },
                        onSegarkanVerifikasi = {
                            verifyBusy = true
                            authViewModel.refreshEmailVerified { _, pesan ->
                                verifyBusy = false
                                errorMessage = pesan
                            }
                        },
                    )

                    BagianProfil.AKTIVITAS -> BagianAktivitas(
                        isCreator = authState.isCreator,
                        jumlahBooking = myBookings.size,
                        jumlahTersimpan = savedPosts.size,
                        jumlahDisukai = likedPosts.size,
                        jumlahUlasan = ulasan.size,
                        onMyBookings = onMyBookings,
                        onFavorites = onFavorites,
                        onSaved = onSaved,
                        onDisukai = {
                            indeksBagian = BagianProfil.KARYA.ordinal
                            indeksKoleksi = KoleksiKarya.DISUKAI.ordinal
                        },
                        onMyReviews = { indeksBagian = BagianProfil.REVIEW.ordinal },
                        onReports = onReports,
                        onWatchHistory = onWatchHistory,
                        onInterests = onInterests,
                    )

                    BagianProfil.KARYA -> BagianKarya(
                        koleksi = koleksi,
                        onKoleksi = { indeksKoleksi = it.ordinal },
                        isCreator = authState.isCreator,
                        myPosts = myPosts,
                        savedPosts = savedPosts,
                        likedPosts = likedPosts,
                        onPostClick = onPostClick,
                        onSaved = onSaved,
                        onMyWorks = onMyWorks,
                    )

                    BagianProfil.REVIEW -> BagianReview(
                        ulasan = ulasan,
                        isCreator = authState.isCreator,
                        onLihatSemua = onMyReviews,
                    )

                    BagianProfil.PENGATURAN -> BagianPengaturan(
                        isCreator = authState.isCreator,
                        onEditProfile = onEditProfile,
                        onNotifications = onNotifications,
                        onInterests = onInterests,
                        onBagikan = { showQr = true },
                        onMyWorks = onMyWorks,
                        onCreatorStudio = onCreatorStudio,
                        onWatchHistory = onWatchHistory,
                        onHelp = onHelp,
                        onKeluar = { dialogKeluar = true },
                        onHapusAkun = { dialogHapus = true },
                    )
                }

                Spacer(Modifier.height(90.dp))
            }
        }

        if (showQr) {
            // Tautan profil hanya bermakna untuk akun creator — profil konsumen
            // tidak punya halaman publik yang bisa dibuka orang lain.
            val tautan = if (authState.isCreator && uid != null) {
                "https://jepretaja.app/creator/$uid"
            } else {
                "https://jepretaja.app"
            }
            AlertDialog(
                onDismissRequest = { showQr = false },
                title = { Text("Bagikan profil") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        QrCode(content = tautan, modifier = Modifier.size(200.dp))
                        Spacer(Modifier.height(14.dp))
                        Text(
                            tautan,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        if (!authState.isCreator) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Profil konsumen belum punya halaman publik, jadi yang dibagikan adalah tautan aplikasi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val kirim = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Lihat profilku di JepretAja: $tautan")
                        }
                        runCatching { context.startActivity(Intent.createChooser(kirim, "Bagikan lewat")) }
                        showQr = false
                    }) { Text("Bagikan tautan") }
                },
                dismissButton = { TextButton(onClick = { showQr = false }) { Text("Tutup") } },
            )
        }

        if (dialogKeluar) {
            DialogKeluar(
                onBatal = { dialogKeluar = false },
                onKeluar = {
                    dialogKeluar = false
                    authViewModel.logout()
                    onLoggedOut()
                },
            )
        }

        if (dialogMasukUlang) {
            // Kegagalan ini punya satu jalan keluar yang pasti, jadi jalannya
            // disediakan di sini. Menuliskannya sebagai snackbar berarti
            // menyuruh orang yang baru saja mengetik "HAPUS" untuk mencari
            // sendiri tombol keluar di daftar setelan.
            AlertDialog(
                onDismissRequest = { dialogMasukUlang = false },
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = AppColors.Warning) },
                title = { Text("Perlu masuk ulang") },
                text = {
                    Text(
                        "Demi keamanan, akun hanya bisa dihapus tepat setelah login. " +
                            "Keluar sekarang, masuk lagi, lalu ulangi penghapusan.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        dialogMasukUlang = false
                        authViewModel.logout()
                        onLoggedOut()
                    }) { Text("Keluar sekarang") }
                },
                dismissButton = { TextButton(onClick = { dialogMasukUlang = false }) { Text("Nanti") } },
            )
        }

        if (dialogHapus) {
            DialogHapusAkun(
                sedangMenghapus = deleting,
                onBatal = { if (!deleting) dialogHapus = false },
                onHapus = {
                    deleting = true
                    authViewModel.deleteAccount { hasil ->
                        deleting = false
                        dialogHapus = false
                        when {
                            hasil.pesan == null -> onLoggedOut()
                            hasil.perluMasukUlang -> dialogMasukUlang = true
                            else -> errorMessage = hasil.pesan
                        }
                    }
                },
            )
        }
    }
}

/* --------------------------------------------------------------------------
 * Bagian 1 — Profil
 * ----------------------------------------------------------------------- */

@Composable
private fun BagianProfilIsi(
    nama: String,
    email: String,
    telepon: String?,
    emailTerverifikasi: Boolean,
    isCreator: Boolean,
    bio: String?,
    verifyBusy: Boolean,
    onEditProfile: () -> Unit,
    onBagikan: () -> Unit,
    onCreatorStudio: () -> Unit,
    onKirimVerifikasi: () -> Unit,
    onSegarkanVerifikasi: () -> Unit,
) {
    if (!emailTerverifikasi) {
        VerifyEmailBanner(busy = verifyBusy, onSend = onKirimVerifikasi, onRefresh = onSegarkanVerifikasi)
        Spacer(Modifier.height(12.dp))
    }
    if (isCreator) {
        CreatorStudioBanner(onCreatorStudio)
        Spacer(Modifier.height(12.dp))
    }

    KelompokMenu(judul = "Detail Akun") {
        BarisData("Nama", nama.ifBlank { "-" })
        HorizontalDivider(color = AppColors.Border)
        BarisData(
            "Email",
            email.ifBlank { "-" },
            lencana = if (emailTerverifikasi) "Terverifikasi" else "Belum diverifikasi",
            lencanaWarna = if (emailTerverifikasi) AppColors.Success else AppColors.Warning,
        )
        HorizontalDivider(color = AppColors.Border)
        BarisData("Nomor HP", telepon?.takeIf { it.isNotBlank() } ?: "Belum diisi")
        HorizontalDivider(color = AppColors.Border)
        BarisData("Jenis akun", if (isCreator) "Creator" else "Konsumen")
    }

    Spacer(Modifier.height(14.dp))

    KelompokMenu(judul = "Bio") {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            val isi = bio?.takeIf { it.isNotBlank() }
            if (isi != null) {
                Text(isi, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
            } else {
                Text(
                    if (isCreator) {
                        "Belum ada bio. Calon pelanggan memakai bagian ini untuk menilai gaya kerjamu."
                    } else {
                        "Bio hanya tampil untuk akun creator."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
            if (isCreator) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onEditProfile, contentPadding = PaddingValues(0.dp)) {
                    Text(if (isi == null) "Tambah bio" else "Ubah bio")
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    KelompokMenu(judul = null) {
        BarisMenu(Icons.Default.Edit, "Edit Profil", onClick = onEditProfile)
        HorizontalDivider(color = AppColors.Border)
        BarisMenu(Icons.Default.QrCode2, "Bagikan Profil", onClick = onBagikan)
    }
}

/* --------------------------------------------------------------------------
 * Bagian 2 — Aktivitas
 * ----------------------------------------------------------------------- */

@Composable
private fun BagianAktivitas(
    isCreator: Boolean,
    jumlahBooking: Int,
    jumlahTersimpan: Int,
    jumlahDisukai: Int,
    jumlahUlasan: Int,
    onMyBookings: () -> Unit,
    onFavorites: () -> Unit,
    onSaved: () -> Unit,
    onDisukai: () -> Unit,
    onMyReviews: () -> Unit,
    onReports: () -> Unit,
    onWatchHistory: () -> Unit,
    onInterests: () -> Unit,
) {
    KelompokMenu(judul = "Pesanan") {
        if (!isCreator) {
            BarisMenu(Icons.Default.CalendarMonth, "Booking Saya", nilai = "$jumlahBooking", onClick = onMyBookings)
            HorizontalDivider(color = AppColors.Border)
        }
        BarisMenu(Icons.Default.StarBorder, "Review Saya", nilai = "$jumlahUlasan", onClick = onMyReviews)
    }

    Spacer(Modifier.height(14.dp))

    KelompokMenu(judul = "Koleksi") {
        BarisMenu(Icons.Default.FavoriteBorder, "Creator Favorit", onClick = onFavorites)
        HorizontalDivider(color = AppColors.Border)
        BarisMenu(Icons.Default.BookmarkBorder, "Karya Tersimpan", nilai = "$jumlahTersimpan", onClick = onSaved)
        HorizontalDivider(color = AppColors.Border)
        BarisMenu(Icons.Default.Favorite, "Karya Disukai", nilai = "$jumlahDisukai", onClick = onDisukai)
    }

    Spacer(Modifier.height(14.dp))

    KelompokMenu(judul = "Jejak") {
        BarisMenu(Icons.Default.History, "Riwayat Tontonan", onClick = onWatchHistory)
        HorizontalDivider(color = AppColors.Border)
        BarisMenu(Icons.Default.Interests, "Minat Kamu", onClick = onInterests)
        HorizontalDivider(color = AppColors.Border)
        BarisMenu(Icons.Default.Flag, "Laporan Saya", onClick = onReports)
    }
}

/* --------------------------------------------------------------------------
 * Bagian 3 — Karya
 * ----------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BagianKarya(
    koleksi: KoleksiKarya,
    onKoleksi: (KoleksiKarya) -> Unit,
    isCreator: Boolean,
    myPosts: List<ExplorePostModel>,
    savedPosts: List<ExplorePostModel>,
    likedPosts: List<ExplorePostModel>,
    onPostClick: (String) -> Unit,
    onSaved: () -> Unit,
    onMyWorks: () -> Unit,
) {
    // Tiga koleksi ini semuanya grid post, jadi ia jadi satu bagian dengan
    // pemilih di dalamnya — bukan tiga tab sejajar dengan Pengaturan, yang akan
    // menyamakan "grid foto" dengan "hapus akun" sebagai hal yang setara.
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        KoleksiKarya.entries.forEachIndexed { i, k ->
            SegmentedButton(
                selected = koleksi == k,
                onClick = { onKoleksi(k) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = KoleksiKarya.entries.size),
                label = { Text(k.judul, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    val posts = when (koleksi) {
        KoleksiKarya.MILIKKU -> myPosts
        KoleksiKarya.TERSIMPAN -> savedPosts
        KoleksiKarya.DISUKAI -> likedPosts
    }

    if (posts.isEmpty()) {
        TabEmptyState(
            icon = when (koleksi) {
                KoleksiKarya.MILIKKU -> Icons.Default.GridView
                KoleksiKarya.TERSIMPAN -> Icons.Default.BookmarkBorder
                KoleksiKarya.DISUKAI -> Icons.Default.FavoriteBorder
            },
            title = when (koleksi) {
                KoleksiKarya.MILIKKU -> "Belum ada karya"
                KoleksiKarya.TERSIMPAN -> "Belum ada yang disimpan"
                KoleksiKarya.DISUKAI -> "Belum ada yang disukai"
            },
            message = when (koleksi) {
                KoleksiKarya.MILIKKU -> if (isCreator) {
                    "Unggah foto atau video pertamamu lewat tombol + di bawah."
                } else {
                    "Tab ini menampilkan karya yang kamu unggah sebagai creator."
                }
                KoleksiKarya.TERSIMPAN -> "Post yang kamu simpan dari Explore akan muncul di sini."
                KoleksiKarya.DISUKAI -> "Karya yang kamu sukai di Explore akan terkumpul di sini."
            },
            actionLabel = if (koleksi == KoleksiKarya.TERSIMPAN) "Buka halaman Tersimpan" else null,
            onAction = onSaved,
        )
    } else {
        // Dibatasi, dengan jalan ke halaman penuhnya.
        //
        // PostGrid menggambar SEMUA post sekaligus di dalam kolom yang
        // menggulir — bukan grid malas — jadi creator dengan 200 karya membuat
        // 200 AsyncImage hidup bersamaan begitu tab dibuka. Angkanya tidak
        // pernah terasa saat menguji dengan lima post, dan selalu terasa pada
        // akun yang benar-benar dipakai.
        val ditampilkan = posts.take(BATAS_GRID)
        PostGrid(posts = ditampilkan, onPostClick = onPostClick)
        if (posts.size > ditampilkan.size) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                    onClick = if (koleksi == KoleksiKarya.MILIKKU) onMyWorks else onSaved,
                ) { Text("Lihat semua ${posts.size}") }
            }
        }
    }
}

/** Sebanyak ini karya digambar di tab Profil sebelum ditawarkan halaman penuh. */
private const val BATAS_GRID = 30

/* --------------------------------------------------------------------------
 * Bagian 4 — Review
 * ----------------------------------------------------------------------- */

@Composable
private fun BagianReview(
    ulasan: List<ReviewModel>,
    isCreator: Boolean,
    onLihatSemua: () -> Unit,
) {
    if (ulasan.isEmpty()) {
        TabEmptyState(
            icon = Icons.Default.StarBorder,
            title = if (isCreator) "Belum ada ulasan masuk" else "Belum ada ulasan yang kamu tulis",
            message = if (isCreator) {
                "Ulasan muncul setelah pelanggan menyelesaikan booking bersamamu."
            } else {
                "Setelah pemotretan selesai, kamu bisa menilai creator lewat detail booking."
            },
            actionLabel = null,
            onAction = {},
        )
        return
    }

    val rerata = remember(ulasan) { ulasan.map { it.rating.toInt() }.average() }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.SurfaceVariant)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                String.format("%.1f", rerata),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.W700,
                color = AppColors.TextPrimary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BarisBintang(rerata.toInt())
                Text(
                    if (isCreator) "${ulasan.size} ulasan diterima" else "${ulasan.size} ulasan ditulis",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
            TextButton(onClick = onLihatSemua) { Text("Lihat semua") }
        }

        Spacer(Modifier.height(12.dp))

        // Lima terbaru saja di sini; sisanya ada di halaman Review Saya. Tab
        // profil bukan tempat menggulir dua ratus ulasan.
        ulasan.take(5).forEach { r ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.Surface)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.customerName.ifBlank { "Pelanggan" },
                        style = MaterialTheme.typography.labelLarge,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Formatters.dateShort(r.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = AppColors.TextSecondary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                BarisBintang(r.rating.toInt())
                if (r.text.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(r.text, style = MaterialTheme.typography.bodySmall, color = AppColors.TextPrimary)
                }
                r.creatorReply?.takeIf { it.isNotBlank() }?.let { balasan ->
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.SurfaceVariant)
                            .padding(10.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat, contentDescription = null,
                            tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(balasan, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarisBintang(nilai: Int) {
    Row {
        repeat(5) { i ->
            Icon(
                if (i < nilai) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (i < nilai) AppColors.Warning else AppColors.Border,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

/* --------------------------------------------------------------------------
 * Bagian 5 — Pengaturan
 * ----------------------------------------------------------------------- */

@Composable
private fun BagianPengaturan(
    isCreator: Boolean,
    onEditProfile: () -> Unit,
    onNotifications: () -> Unit,
    onInterests: () -> Unit,
    onBagikan: () -> Unit,
    onMyWorks: () -> Unit,
    onCreatorStudio: () -> Unit,
    onWatchHistory: () -> Unit,
    onHelp: () -> Unit,
    onKeluar: () -> Unit,
    onHapusAkun: () -> Unit,
) {
    KelompokMenu(judul = "Akun") {
        BarisMenu(Icons.Default.Edit, "Edit Profil", onClick = onEditProfile)
        HorizontalDivider(color = AppColors.Border)
        BarisMenu(Icons.Default.NotificationsNone, "Notifikasi", onClick = onNotifications)
        HorizontalDivider(color = AppColors.Border)
        BarisMenu(Icons.Default.QrCode2, "Bagikan Profil", onClick = onBagikan)
    }

    if (isCreator) {
        Spacer(Modifier.height(14.dp))
        KelompokMenu(judul = "Creator") {
            BarisMenu(Icons.Default.CollectionsBookmark, "Karya Saya", onClick = onMyWorks)
            HorizontalDivider(color = AppColors.Border)
            BarisMenu(Icons.Default.Dashboard, "Creator Studio", onClick = onCreatorStudio)
        }
    }

    Spacer(Modifier.height(14.dp))

    KelompokMenu(judul = "Preferensi") {
        BarisMenu(Icons.Default.Interests, "Minat Kamu", onClick = onInterests)
        HorizontalDivider(color = AppColors.Border)
        BarisMenu(Icons.Default.History, "Riwayat Tontonan", onClick = onWatchHistory)
    }

    Spacer(Modifier.height(14.dp))

    KelompokMenu(judul = "Bantuan") {
        BarisMenu(Icons.Default.SupportAgent, "Pusat Bantuan", onClick = onHelp)
    }

    Spacer(Modifier.height(14.dp))

    // Kelompok sendiri, diberi jarak dan warna berbeda. Di daftar rata yang
    // lama, "Hapus Akun" duduk tepat di bawah "Bantuan" dengan bentuk yang
    // persis sama.
    KelompokMenu(judul = "Zona Berbahaya", warnaJudul = AppColors.Danger) {
        BarisMenu(
            Icons.AutoMirrored.Filled.Logout, "Keluar",
            warna = AppColors.Danger, onClick = onKeluar,
        )
        HorizontalDivider(color = AppColors.Border)
        BarisMenu(
            Icons.Default.DeleteForever, "Hapus Akun",
            keterangan = "Permanen, tidak bisa dibatalkan",
            warna = AppColors.Danger, onClick = onHapusAkun,
        )
    }
}

/* --------------------------------------------------------------------------
 * Dialog aksi berbahaya
 * ----------------------------------------------------------------------- */

/**
 * Konfirmasi keluar.
 *
 * Sebelumnya tidak ada sama sekali: satu ketukan di daftar setelan langsung
 * memanggil `logout()`. Di sheet yang sedang digulir, jari yang meleset satu
 * baris cukup untuk mengeluarkan orang dari akunnya.
 */
@Composable
private fun DialogKeluar(onBatal: () -> Unit, onKeluar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBatal,
        icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = AppColors.Danger) },
        title = { Text("Keluar dari akun ini?") },
        text = {
            Text(
                "Datamu tetap tersimpan. Kamu perlu masuk lagi untuk melihat booking, " +
                    "chat, dan karya tersimpan.",
            )
        },
        confirmButton = { TextButton(onClick = onKeluar) { Text("Keluar", color = AppColors.Danger) } },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal") } },
    )
}

/**
 * Konfirmasi hapus akun — menuntut kata konfirmasi diketik.
 *
 * Dialog lama sudah menjelaskan akibatnya, tapi tombol merahnya bisa ditekan
 * seketika, tepat di posisi tempat jari sudah berada sesudah membuka dialog.
 * Untuk tindakan yang benar-benar tidak bisa dibatalkan, penghalang yang tepat
 * bukan peringatan yang lebih panjang melainkan tindakan yang tidak mungkin
 * dilakukan tanpa sengaja: mengetik.
 */
@Composable
private fun DialogHapusAkun(
    sedangMenghapus: Boolean,
    onBatal: () -> Unit,
    onHapus: () -> Unit,
) {
    var ketikan by remember { mutableStateOf("") }
    // Dicocokkan tanpa memandang besar-kecil huruf. Gesekan yang disengaja di
    // sini adalah MENGETIK kata itu, bukan menebak apakah papan ketiknya
    // mengaktifkan huruf kapital — dan banyak papan ketik Android tidak. Versi
    // sebelumnya membandingkan persis, jadi orang yang mengetik "hapus"
    // menghadapi tombol mati tanpa satu pun keterangan kenapa.
    val cocok = ketikan.trim().equals(KATA_KONFIRMASI, ignoreCase = true)
    val salahKetik = ketikan.isNotBlank() && !cocok

    AlertDialog(
        onDismissRequest = { if (!sedangMenghapus) onBatal() },
        icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = AppColors.Danger) },
        title = { Text("Hapus akun secara permanen?") },
        text = {
            Column {
                Text(
                    "Yang akan hilang dan tidak bisa dipulihkan:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                listOf(
                    "Profil dan foto akunmu",
                    "Akses ke riwayat booking dan pembayaran",
                    "Karya, ulasan, dan percakapan",
                ).forEach {
                    Text("•  $it", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Ketik $KATA_KONFIRMASI untuk melanjutkan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = ketikan,
                    onValueChange = { ketikan = it },
                    singleLine = true,
                    enabled = !sedangMenghapus,
                    isError = salahKetik,
                    placeholder = { Text(KATA_KONFIRMASI) },
                    supportingText = if (salahKetik) {
                        { Text("Belum cocok. Ketik persis: $KATA_KONFIRMASI") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = cocok && !sedangMenghapus, onClick = onHapus) {
                Text(
                    if (sedangMenghapus) "Menghapus..." else "Hapus Akun",
                    color = if (cocok && !sedangMenghapus) AppColors.Danger else AppColors.TextSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !sedangMenghapus, onClick = onBatal) { Text("Batal") }
        },
    )
}

private const val KATA_KONFIRMASI = "HAPUS"

/* --------------------------------------------------------------------------
 * Potongan menu
 * ----------------------------------------------------------------------- */

/**
 * Satu kelompok menu: judul kecil di luar, isinya dalam satu kartu.
 *
 * Pengelompokan inilah yang hilang di versi sebelumnya — empat belas baris
 * ditumpuk rata tanpa satu pun judul, jadi tidak ada cara memindainya selain
 * membaca satu per satu dari atas.
 */
@Composable
private fun KelompokMenu(
    judul: String?,
    warnaJudul: Color = AppColors.TextSecondary,
    isi: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        judul?.let {
            Text(
                it.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = warnaJudul,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AppColors.Surface),
            content = isi,
        )
    }
}

@Composable
private fun BarisMenu(
    ikon: ImageVector,
    label: String,
    keterangan: String? = null,
    nilai: String? = null,
    warna: Color = AppColors.TextPrimary,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                .background(
                    if (warna == AppColors.Danger) AppColors.Danger.copy(alpha = 0.1f) else AppColors.PrimarySoft
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                ikon, contentDescription = null,
                tint = if (warna == AppColors.Danger) AppColors.Danger else AppColors.Primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = warna)
            keterangan?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
        nilai?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = AppColors.TextSecondary)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Default.ChevronRight, contentDescription = null,
            tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp),
        )
    }
}

/** Baris keterangan yang TIDAK bisa diketuk — sengaja tanpa riak sentuhan. */
@Composable
private fun BarisData(
    label: String,
    nilai: String,
    lencana: String? = null,
    lencanaWarna: Color = AppColors.TextSecondary,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
            modifier = Modifier.width(100.dp),
        )
        Text(
            nilai,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        lencana?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = lencanaWarna,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(lencanaWarna.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Profil untuk pengunjung yang belum masuk.
 *
 * HANYA menawarkan Masuk. Pendaftaran sengaja tidak ada di sini: akun dibuat
 * lewat layar pemilihan akses saat pertama membuka aplikasi, karena di sana
 * pengguna bisa memilih daftar sebagai konsumen atau creator — pilihan yang
 * tidak bisa diwakili satu tombol "Daftar" di halaman profil.
 */
@Composable
private fun GuestProfile(modifier: Modifier = Modifier, onLogin: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(AppColors.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Person, contentDescription = null,
                    tint = AppColors.TextSecondary, modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text("Masuk ke JepretAja", style = MaterialTheme.typography.titleLarge, color = AppColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Masuk untuk memesan fotografer, menyimpan karya favorit, dan melihat riwayat bookingmu.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onLogin,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Masuk") }
        }
    }
}

/** Satu angka statistik profil (pengikut / karya / rating). */
@Composable
private fun ProfileStat(value: String, label: String, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.W700,
            color = AppColors.TextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 13.sp, color = AppColors.TextSecondary)
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier.padding(top = 6.dp).width(1.dp).height(22.dp).background(AppColors.Border),
    )
}

@Composable
private fun SquareIconButton(
    icon: ImageVector,
    description: String,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BadgedBox(
            badge = {
                if (badgeCount > 0) {
                    Badge(containerColor = AppColors.Danger, contentColor = Color.White) {
                        Text(badgeLabel(badgeCount), fontSize = 10.sp, fontWeight = FontWeight.W700)
                    }
                }
            },
        ) {
            Icon(icon, contentDescription = description, tint = AppColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Grid tiga kolom rapat (jarak 1dp, sudut siku) — bentuk yang sama dengan
 * grid post di profil TikTok, supaya gambar yang jadi fokus, bukan kartunya.
 */
@Composable
private fun PostGrid(posts: List<ExplorePostModel>, onPostClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        posts.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { post ->
                    Box(
                        Modifier.weight(1f).aspectRatio(0.75f)
                            .background(AppColors.SurfaceVariant)
                            .clickable { onPostClick(post.postId) },
                    ) {
                        AsyncImage(
                            model = post.thumbnailUrl ?: post.mediaUrls.firstOrNull(),
                            contentDescription = post.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (post.type == "video") Icons.Default.PlayArrow else Icons.Default.Visibility,
                                contentDescription = null, tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(2.dp))
                            Text("${post.viewCount}", fontSize = 12.sp, color = Color.White)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(1.dp))
        }
    }
}

@Composable
private fun TabEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun CreatorStudioBanner(onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.PrimarySoft)
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Dashboard, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Creator Studio", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
            Text(
                "Kelola paket, portfolio, booking, dan penghasilanmu.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
    }
}

@Composable
private fun VerifyEmailBanner(busy: Boolean, onSend: () -> Unit, onRefresh: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Warning.copy(alpha = 0.1f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MarkEmailUnread, contentDescription = null, tint = AppColors.Warning, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Email belum diverifikasi", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Verifikasi email supaya akunmu aman dan bisa memulihkan sandi.",
            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onSend,
                enabled = !busy,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Warning, contentColor = AppColors.OnPrimary),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(38.dp),
            ) { Text("Kirim Email", style = MaterialTheme.typography.labelLarge) }

            OutlinedButton(
                onClick = onRefresh,
                enabled = !busy,
                shape = RoundedCornerShape(percent = 50),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.height(38.dp),
            ) { Text("Sudah verifikasi", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

