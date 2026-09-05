package com.jepretaja.app.ui.screens.root

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jepretaja.app.core.navigation.Routes
import com.jepretaja.app.ui.components.AppBottomNavBar
import com.jepretaja.app.ui.components.FiturButuhAkun
import com.jepretaja.app.ui.components.LoginRequiredSheet
import com.jepretaja.app.ui.components.NavTab
import com.jepretaja.app.ui.screens.explore.ExploreScreen
import com.jepretaja.app.ui.screens.home.HomeScreen
import com.jepretaja.app.ui.screens.home.MyBookingsScreen
import com.jepretaja.app.ui.screens.profile.ProfileScreen
import com.jepretaja.app.ui.screens.search.SearchFilters
import com.jepretaja.app.ui.state.AuthViewModel

/**
 * Empat menu untuk SEMUA orang: Home | Explore | Booking | Profile.
 *
 * Chat sengaja tidak lagi menempati satu slot di sini — ia pindah ke pojok
 * kanan atas layar Home. Alasannya: chat adalah tempat yang dituju setelah ada
 * urusan (booking, tanya paket), bukan tujuan sehari-hari seperti empat menu
 * ini, dan mengosongkan satu slot membuat tombol unggah creator bisa duduk
 * tepat di tengah.
 */
private val mainTabs = listOf(
    NavTab("tab_home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab("tab_explore", "Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
    NavTab("tab_bookings", "Booking", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    NavTab("tab_profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person),
)

/**
 * Shell utama aplikasi, dipakai konsumen, tamu, MAUPUN creator.
 *
 * Sebelumnya creator dilempar ke shell terpisah berisi dua menu (Dashboard &
 * Explore) sehingga mereka tidak punya Home, Booking, atau Profile sama sekali.
 * Sekarang semua orang memakai kerangka yang sama, dan yang membedakan hanya
 * dua hal: tombol + di tengah, dan pintu Creator Studio di dalam Profile.
 */
@Composable
fun CustomerRootShell(
    outerNavController: NavHostController,
    authViewModel: AuthViewModel,
) {
    val innerNav = rememberNavController()
    val backStackEntry by innerNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val authState by authViewModel.uiState.collectAsState()

    // Denyut kehadiran: cukup dipanggil saat shell tampil dan tiap kali akun
    // berubah — pembatas 5 menit di ViewModel yang memutuskan perlu-tidaknya
    // benar-benar menulis.
    LaunchedEffect(authState.uid, authState.isCreator) { authViewModel.tandaiKehadiran() }
    var showUploadSheet by remember { mutableStateOf(false) }

    // Menu yang sedang diminta tamu, atau null bila tidak ada ajakan masuk yang
    // sedang tampil. Splash baru melepas pengguna ke sini setelah status auth
    // selesai dibaca, jadi `isLoggedIn == false` di titik ini benar-benar
    // berarti tamu — bukan sekadar "belum sempat dimuat".
    var mintaMasuk by remember { mutableStateOf<FiturButuhAkun?>(null) }
    val tamu = !authState.isLoggedIn

    // Home, Explore, dan Profile tetap terbuka untuk tamu: justru itu yang
    // membuat orang mau mendaftar. Yang dijaga hanya menu yang isinya memang
    // milik satu akun.
    fun bukaBooking() {
        if (tamu) mintaMasuk = FiturButuhAkun.BOOKING else innerNav.navigate("tab_bookings")
    }

    fun bukaChat() {
        if (tamu) mintaMasuk = FiturButuhAkun.CHAT else outerNavController.navigate(Routes.CHAT_LIST)
    }

    fun bukaNotifikasi() {
        if (tamu) mintaMasuk = FiturButuhAkun.NOTIFIKASI else outerNavController.navigate(Routes.NOTIFICATIONS)
    }

    Scaffold(bottomBar = {
        AppBottomNavBar(
            tabs = mainTabs,
            currentRoute = currentRoute,
            onSelect = { route ->
                if (route == "tab_bookings" && tamu) {
                    mintaMasuk = FiturButuhAkun.BOOKING
                } else {
                    innerNav.navigate(route) { popUpTo("tab_home") { saveState = true }; launchSingleTop = true; restoreState = true }
                }
            },
            // Hanya creator yang mendapat tombol unggah.
            centerAction = if (authState.isCreator) ({ showUploadSheet = true }) else null,
        )
    }) { padding ->
        NavHost(innerNav, startDestination = "tab_home", modifier = Modifier.padding(padding)) {
            composable("tab_home") {
                HomeScreen(
                    onSearch = { outerNavController.navigate(Routes.SEARCH) },
                    onNotifications = { bukaNotifikasi() },
                    onChat = { bukaChat() },
                    onCreatorClick = { id -> outerNavController.navigate(Routes.creatorProfile(id)) },
                    onCategoryClick = { category ->
                        outerNavController.currentBackStackEntry?.savedStateHandle?.set("search_filters", SearchFilters(categories = listOf(category)))
                        outerNavController.navigate(Routes.SEARCH_RESULT)
                    },
                    onSeeExplore = { innerNav.navigate("tab_explore") },
                    onBookingClick = { id -> outerNavController.navigate(Routes.bookingDetail(id)) },
                    onPostClick = { postId -> outerNavController.navigate(Routes.exploreDetail(postId)) },
                    authViewModel = authViewModel,
                )
            }
            composable("tab_explore") {
                ExploreScreen(
                    authViewModel = authViewModel,
                    onCreatorClick = { id -> outerNavController.navigate(Routes.creatorProfile(id)) },
                    onCommentClick = { postId -> outerNavController.navigate(Routes.exploreDetail(postId)) },
                    onTagClick = { tag, isCategory ->
                        outerNavController.navigate(Routes.tagFeed(tag, isCategory))
                    },
                    onPackageClick = { id -> outerNavController.navigate(Routes.packageDetail(id)) },
                )
            }
            composable("tab_bookings") {
                MyBookingsScreen(
                    onBookingClick = { id -> outerNavController.navigate(Routes.bookingDetail(id)) },
                    authViewModel = authViewModel,
                )
            }
            composable("tab_profile") {
                ProfileScreen(
                    onLogin = { outerNavController.navigate(Routes.LOGIN) },
                    onEditProfile = { outerNavController.navigate(Routes.EDIT_PROFILE) },
                    onSaved = { outerNavController.navigate(Routes.SAVED_POSTS) },
                    onMyBookings = { bukaBooking() },
                    onFavorites = { outerNavController.navigate(Routes.FAVORITES) },
                    onMyReviews = { outerNavController.navigate(Routes.MY_REVIEWS) },
                    onReports = { outerNavController.navigate(Routes.MY_REPORTS) },
                    onNotifications = { bukaNotifikasi() },
                    onHelp = { outerNavController.navigate(Routes.HELP) },
                    onChat = { bukaChat() },
                    onCreatorStudio = { outerNavController.navigate(Routes.CREATOR_DASHBOARD) },
                    // Ubin di grid profil membuka postnya, sama seperti profil TikTok.
                    onPostClick = { postId -> outerNavController.navigate(Routes.exploreDetail(postId)) },
                    onMyWorks = { outerNavController.navigate(Routes.MY_WORKS) },
                    onInterests = { outerNavController.navigate(Routes.INTERESTS) },
                    onWatchHistory = { outerNavController.navigate(Routes.WATCH_HISTORY) },
                    onFollowList = { tab ->
                        authState.uid?.let { outerNavController.navigate(Routes.followList(it, tab)) }
                    },
                    onLoggedOut = { outerNavController.navigate(Routes.CHOOSE_ACCESS) { popUpTo(0) } },
                    authViewModel = authViewModel,
                )
            }
        }
    }

    mintaMasuk?.let { fitur ->
        LoginRequiredSheet(
            fitur = fitur,
            onDismiss = { mintaMasuk = null },
            // Lembar ditutup DULU baru berpindah: kalau tidak, ia masih
            // menempel di layar masuk dan ikut terlihat lagi saat pengguna
            // menekan tombol kembali.
            onLogin = { mintaMasuk = null; outerNavController.navigate(Routes.LOGIN) },
            onRegister = { mintaMasuk = null; outerNavController.navigate(Routes.REGISTER_CUSTOMER) },
        )
    }

    if (showUploadSheet) {
        CreatorUploadSheet(
            onDismiss = { showUploadSheet = false },
            onUploadMedia = { showUploadSheet = false; outerNavController.navigate(Routes.CREATOR_UPLOAD) },
            onNewPackage = { showUploadSheet = false; outerNavController.navigate(Routes.CREATOR_PACKAGE_MANAGEMENT) },
            onManagePortfolio = { showUploadSheet = false; outerNavController.navigate(Routes.CREATOR_PORTFOLIO_MANAGEMENT) },
        )
    }
}
