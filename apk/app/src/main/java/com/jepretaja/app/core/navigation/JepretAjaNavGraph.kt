package com.jepretaja.app.core.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jepretaja.app.ui.screens.auth.ChooseAccessScreen
import com.jepretaja.app.ui.screens.auth.LoginScreen
import com.jepretaja.app.ui.screens.auth.RegisterCreatorScreen
import com.jepretaja.app.ui.screens.auth.RegisterCustomerScreen
import com.jepretaja.app.ui.screens.booking.BookingConfirmationScreen
import com.jepretaja.app.ui.screens.booking.BookingDetailScreen
import com.jepretaja.app.ui.screens.booking.BookingFormScreen
import com.jepretaja.app.ui.screens.chat.ChatListScreen
import com.jepretaja.app.ui.screens.chat.ChatRoomScreen
import com.jepretaja.app.ui.screens.creator.CreatorProfileScreen
import com.jepretaja.app.ui.screens.profile.EditProfileScreen
import com.jepretaja.app.ui.screens.reviews.WriteReviewScreen
import com.jepretaja.app.ui.screens.profile.SavedPostsScreen
import com.jepretaja.app.ui.screens.creatordashboard.CreatorAvailabilityScreen
import com.jepretaja.app.ui.screens.creatordashboard.CreatorBankAccountScreen
import com.jepretaja.app.ui.screens.creatordashboard.CreatorBookingManagementScreen
import com.jepretaja.app.ui.screens.creatordashboard.CreatorPackageManagementScreen
import com.jepretaja.app.ui.screens.creatordashboard.CreatorPortfolioManagementScreen
import com.jepretaja.app.ui.screens.creatordashboard.CreatorSettingsScreen
import com.jepretaja.app.ui.screens.creatordashboard.CreatorWalletScreen
import com.jepretaja.app.ui.screens.creatordashboard.CreatorWithdrawalScreen
import com.jepretaja.app.ui.screens.creatorupload.CreatorUploadScreen
import com.jepretaja.app.ui.screens.explore.ExploreDetailScreen
import com.jepretaja.app.ui.screens.favorites.FavoritesScreen
import com.jepretaja.app.ui.screens.follows.FollowListScreen
import com.jepretaja.app.ui.screens.creatorworks.MyWorksScreen
import com.jepretaja.app.ui.screens.history.WatchHistoryScreen
import com.jepretaja.app.ui.screens.interests.InterestsScreen
import com.jepretaja.app.ui.screens.tag.TagFeedScreen
import com.jepretaja.app.ui.screens.help.HelpScreen
import com.jepretaja.app.ui.screens.myreports.MyReportsScreen
import com.jepretaja.app.ui.screens.myreviews.MyReviewsScreen
import com.jepretaja.app.ui.screens.notifications.NotificationsScreen
import com.jepretaja.app.ui.screens.onboarding.OnboardingScreen
import com.jepretaja.app.ui.screens.packages.PackageDetailScreen
import com.jepretaja.app.ui.screens.payment.PaymentResultScreen
import com.jepretaja.app.ui.screens.payment.PaymentScreen
import com.jepretaja.app.ui.screens.reviews.ReviewsScreen
import com.jepretaja.app.ui.screens.creatordashboard.CreatorDashboardScreen
import com.jepretaja.app.ui.screens.root.CustomerRootShell
import com.jepretaja.app.ui.screens.search.NearbyScreen
import com.jepretaja.app.ui.screens.search.SearchFilters
import com.jepretaja.app.ui.screens.search.SearchResultScreen
import com.jepretaja.app.ui.screens.search.SearchScreen
import com.jepretaja.app.ui.screens.splash.SplashScreen
import com.jepretaja.app.ui.state.AuthViewModel

/**
 * Graf navigasi utama — setara GoRouter di versi Flutter. AuthViewModel
 * di-scope di sini (bukan per-screen) supaya satu instance state auth
 * dipakai konsisten di seluruh app.
 *
 * STATUS: SEMUA jalur Konsumen & Creator lengkap dirangkai — Home/Explore/
 * Search/Nearby/Creator Profile/Booking/Payment/Chat/Profile/Notifications/
 * Favorites/MyReviews/Help, plus seluruh Creator Dashboard (Upload, Kelola
 * Paket/Portfolio/Booking, Wallet, Withdrawal, Availability, Settings).
 */
@Composable
fun JepretAjaNavGraph(navController: NavHostController = rememberNavController()) {
    val authViewModel: AuthViewModel = hiltViewModel()

    // Transisi antar layar: geser halus + fade, bukan potongan mendadak bawaan
    // NavHost. Durasi 320/280 ms cukup terasa mewah tanpa membuat navigasi
    // terasa lambat, dan easing-nya sengaja berbeda antara masuk & keluar
    // supaya layar baru terasa "datang", bukan sekadar bertukar tempat.
    val enterDuration = 320
    val exitDuration = 280

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 12 },
                animationSpec = tween(enterDuration, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(enterDuration))
        },
        exitTransition = {
            fadeOut(animationSpec = tween(exitDuration)) +
                scaleOut(targetScale = 0.98f, animationSpec = tween(exitDuration))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(enterDuration)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(enterDuration))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it / 12 },
                animationSpec = tween(exitDuration, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(exitDuration))
        },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(authViewModel = authViewModel) { target ->
                navController.navigate(target) { popUpTo(Routes.SPLASH) { inclusive = true } }
            }
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinish = {
                // Ditandai di sini, bukan di dalam layarnya, supaya berlaku baik
                // saat perkenalan diselesaikan maupun saat dilewati.
                authViewModel.markOnboardingSeen()
                navController.navigate(Routes.CHOOSE_ACCESS) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.CHOOSE_ACCESS) {
            ChooseAccessScreen(
                onLogin = { navController.navigate(Routes.LOGIN) },
                onRegisterCustomer = { navController.navigate(Routes.REGISTER_CUSTOMER) },
                onRegisterCreator = { navController.navigate(Routes.REGISTER_CREATOR) },
                onContinueAsGuest = {
                    // Pilihan menjadi tamu ikut diingat — tanpa ini pengguna
                    // dikembalikan ke halaman ini lagi setiap membuka aplikasi.
                    authViewModel.markOnboardingSeen()
                    authViewModel.enterGuestMode()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.CHOOSE_ACCESS) { inclusive = true } }
                },
                onGoogleSignedIn = {
                    // Akun sungguhan: tandai perkenalan selesai, tapi JANGAN
                    // menyimpan penanda tamu.
                    authViewModel.markOnboardingSeen()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.CHOOSE_ACCESS) { inclusive = true } }
                },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = {
                    authViewModel.markOnboardingSeen()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.CHOOSE_ACCESS) { inclusive = true } }
                },
            )
        }
        composable(Routes.REGISTER_CUSTOMER) {
            RegisterCustomerScreen(
                onBack = { navController.popBackStack() },
                onSuccess = {
                    authViewModel.markOnboardingSeen()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.CHOOSE_ACCESS) { inclusive = true } }
                },
            )
        }
        composable(Routes.REGISTER_CREATOR) {
            RegisterCreatorScreen(
                onBack = { navController.popBackStack() },
                onSuccess = {
                    authViewModel.markOnboardingSeen()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.CHOOSE_ACCESS) { inclusive = true } }
                },
            )
        }

        composable(Routes.HOME) {
            CustomerRootShell(outerNavController = navController, authViewModel = authViewModel)
        }
        // Creator Studio: sekarang satu halaman biasa yang dibuka dari Profile,
        // bukan lagi shell navigasi tersendiri. Creator memakai empat menu bawah
        // yang sama seperti semua orang.
        composable(Routes.CREATOR_DASHBOARD) {
            CreatorDashboardScreen(
                onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onNavigate = { route -> navController.navigate(route) },
                authViewModel = authViewModel,
            )
        }
        composable(Routes.CREATOR_UPLOAD) {
            CreatorUploadScreen(onBack = { navController.popBackStack() }, onUploaded = { navController.popBackStack() }, authViewModel = authViewModel)
        }
        composable(Routes.CREATOR_PORTFOLIO_MANAGEMENT) {
            CreatorPortfolioManagementScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel)
        }
        composable(Routes.CREATOR_PACKAGE_MANAGEMENT) {
            CreatorPackageManagementScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel)
        }
        composable(Routes.CREATOR_BOOKING_MANAGEMENT) {
            CreatorBookingManagementScreen(
                onBack = { navController.popBackStack() },
                onBookingClick = { id -> navController.navigate(Routes.bookingDetail(id)) },
                authViewModel = authViewModel,
            )
        }
        composable(Routes.CREATOR_WALLET) {
            CreatorWalletScreen(onBack = { navController.popBackStack() }, onWithdraw = { navController.navigate(Routes.CREATOR_WITHDRAWAL) }, authViewModel = authViewModel)
        }
        composable(Routes.CREATOR_WITHDRAWAL) {
            CreatorWithdrawalScreen(
                onBack = { navController.popBackStack() },
                onKelolaRekening = { navController.navigate(Routes.CREATOR_BANK_ACCOUNT) },
                authViewModel = authViewModel,
            )
        }
        composable(Routes.CREATOR_BANK_ACCOUNT) {
            CreatorBankAccountScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel)
        }
        composable(Routes.CREATOR_AVAILABILITY) {
            CreatorAvailabilityScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel)
        }
        composable(Routes.CREATOR_SETTINGS) {
            CreatorSettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = { navController.navigate(Routes.CHOOSE_ACCESS) { popUpTo(0) } },
                authViewModel = authViewModel,
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onSeeResults = { filters -> navController.currentBackStackEntry?.savedStateHandle?.set("search_filters", filters); navController.navigate(Routes.SEARCH_RESULT) },
                onNearby = { navController.navigate(Routes.NEARBY) },
                onTagClick = { tagar -> navController.navigate(Routes.tagFeed(tagar, false)) },
                onCreatorClick = { id -> navController.navigate(Routes.creatorProfile(id)) },
            )
        }
        composable(Routes.SEARCH_RESULT) { backStackEntry ->
            val filters = navController.previousBackStackEntry?.savedStateHandle?.get<SearchFilters>("search_filters") ?: SearchFilters()
            SearchResultScreen(
                filters = filters,
                onBack = { navController.popBackStack() },
                onCreatorClick = { id -> navController.navigate(Routes.creatorProfile(id)) },
                onPostClick = { postId -> navController.navigate(Routes.exploreDetail(postId)) },
            )
        }
        composable(
            Routes.TAG_FEED,
            arguments = listOf(
                navArgument("tag") { type = NavType.StringType },
                navArgument("category") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { backStackEntry ->
            val tag = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("tag").orEmpty(), "UTF-8")
            TagFeedScreen(
                tag = tag,
                isCategory = backStackEntry.arguments?.getBoolean("category") ?: false,
                onBack = { navController.popBackStack() },
                onPostClick = { postId -> navController.navigate(Routes.exploreDetail(postId)) },
            )
        }
        composable(Routes.NEARBY) {
            NearbyScreen(
                onBack = { navController.popBackStack() },
                onCreatorClick = { id -> navController.navigate(Routes.creatorProfile(id)) },
            )
        }

        composable(
            Routes.CREATOR_PROFILE,
            arguments = listOf(navArgument("creatorId") { type = NavType.StringType }),
        ) {
            CreatorProfileScreen(
                onBack = { navController.popBackStack() },
                onChatOpen = { chatId -> navController.navigate(Routes.chatRoom(chatId)) },
                onReviewsClick = { creatorId -> navController.navigate(Routes.reviews(creatorId)) },
                onPackageClick = { packageId -> navController.navigate(Routes.packageDetail(packageId)) },
                onLoginRequired = { navController.navigate(Routes.LOGIN) },
                authViewModel = authViewModel,
                onFollowList = { id, tab -> navController.navigate(Routes.followList(id, tab)) },
            )
        }
        composable(
            Routes.REVIEWS,
            arguments = listOf(navArgument("creatorId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val creatorId = backStackEntry.arguments?.getString("creatorId") ?: ""
            ReviewsScreen(creatorId = creatorId, onBack = { navController.popBackStack() })
        }
        composable(
            Routes.PACKAGE_DETAIL,
            arguments = listOf(navArgument("packageId") { type = NavType.StringType }),
        ) {
            PackageDetailScreen(
                onBack = { navController.popBackStack() },
                onBookingClick = { packageId -> navController.navigate(Routes.bookingForm(packageId)) },
            )
        }

        composable(
            Routes.BOOKING_FORM,
            arguments = listOf(navArgument("packageId") { type = NavType.StringType }),
        ) {
            BookingFormScreen(
                onBack = { navController.popBackStack() },
                onSubmitted = { bookingId -> navController.navigate(Routes.bookingConfirmation(bookingId)) { popUpTo(Routes.HOME) } },
            )
        }
        composable(
            Routes.BOOKING_CONFIRMATION,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            BookingConfirmationScreen(
                bookingId = bookingId,
                onBack = { navController.popBackStack() },
                onProceedToPayment = { navController.navigate(Routes.payment(bookingId)) },
            )
        }
        composable(
            Routes.BOOKING_DETAIL,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            BookingDetailScreen(
                bookingId = bookingId,
                onBack = { navController.popBackStack() },
                onChatOpen = { chatId -> navController.navigate(Routes.chatRoom(chatId)) },
                onWriteReview = { bId, cId -> navController.navigate(Routes.writeReview(bId, cId)) },
                authViewModel = authViewModel,
            )
        }

        composable(
            Routes.PAYMENT,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            PaymentScreen(
                bookingId = bookingId,
                onBack = { navController.popBackStack() },
                // Transfer manual: tidak ada halaman gateway yang perlu dibuka,
                // jadi setelah pengguna menyatakan sudah transfer ia langsung
                // dibawa ke layar status menunggu verifikasi admin.
                onTransferDone = { navController.navigate(Routes.paymentResult(bookingId)) { popUpTo(Routes.HOME) } },
            )
        }
        composable(
            Routes.PAYMENT_RESULT,
            arguments = listOf(navArgument("bookingId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val bookingId = backStackEntry.arguments?.getString("bookingId") ?: ""
            PaymentResultScreen(
                bookingId = bookingId,
                onSeeBookingDetail = { navController.navigate(Routes.bookingDetail(bookingId)) { popUpTo(Routes.HOME) } },
                onBackToHome = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
            )
        }

        composable(
            Routes.EXPLORE_DETAIL,
            arguments = listOf(navArgument("postId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            ExploreDetailScreen(postId = postId, onBack = { navController.popBackStack() }, authViewModel = authViewModel)
        }

        composable(
            Routes.CHAT_ROOM,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatRoomScreen(
                chatId = chatId,
                authViewModel = authViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel)
        }
        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onBack = { navController.popBackStack() },
                onCreatorClick = { id -> navController.navigate(Routes.creatorProfile(id)) },
                authViewModel = authViewModel,
            )
        }
        composable(Routes.MY_REVIEWS) {
            MyReviewsScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel)
        }
        composable(Routes.MY_REPORTS) {
            MyReportsScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel)
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.WRITE_REVIEW,
            arguments = listOf(
                navArgument("bookingId") { type = NavType.StringType },
                navArgument("creatorId") { type = NavType.StringType },
            ),
        ) { entry ->
            WriteReviewScreen(
                bookingId = entry.arguments?.getString("bookingId").orEmpty(),
                creatorId = entry.arguments?.getString("creatorId").orEmpty(),
                onBack = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() },
                authViewModel = authViewModel,
            )
        }
        // Chat dipindahkan dari bilah menu bawah ke ikon di Home, tapi rutenya
        // sempat tidak ikut didaftarkan di sini — akibatnya menekan ikon Chat
        // tidak membuka apa pun. Daftar chat adalah satu-satunya jalan menuju
        // ruang percakapan, jadi tanpa entri ini seluruh fitur chat terputus.
        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onChatClick = { chatId -> navController.navigate(Routes.chatRoom(chatId)) },
                authViewModel = authViewModel,
            )
        }
        composable(Routes.EDIT_PROFILE) {
            EditProfileScreen(onBack = { navController.popBackStack() }, authViewModel = authViewModel)
        }
        composable(
            Routes.FOLLOW_LIST,
            arguments = listOf(
                navArgument("targetUserId") { type = NavType.StringType },
                navArgument("tab") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            FollowListScreen(
                targetUserId = backStackEntry.arguments?.getString("targetUserId") ?: "",
                initialTab = backStackEntry.arguments?.getInt("tab") ?: 0,
                onBack = { navController.popBackStack() },
                onCreatorClick = { id -> navController.navigate(Routes.creatorProfile(id)) },
            )
        }
        composable(Routes.INTERESTS) {
            InterestsScreen(
                onDone = { navController.popBackStack() },
                authViewModel = authViewModel,
            )
        }
        composable(Routes.WATCH_HISTORY) {
            WatchHistoryScreen(
                onBack = { navController.popBackStack() },
                onPostClick = { postId -> navController.navigate(Routes.exploreDetail(postId)) },
            )
        }
        composable(Routes.MY_WORKS) {
            MyWorksScreen(
                onBack = { navController.popBackStack() },
                onPostClick = { postId -> navController.navigate(Routes.exploreDetail(postId)) },
                authViewModel = authViewModel,
            )
        }
        composable(Routes.SAVED_POSTS) {
            SavedPostsScreen(
                onBack = { navController.popBackStack() },
                onPostClick = { postId -> navController.navigate(Routes.exploreDetail(postId)) },
                authViewModel = authViewModel,
            )
        }

        // Semua route sudah dirangkai — jalur Konsumen & Creator lengkap.
    }
}
