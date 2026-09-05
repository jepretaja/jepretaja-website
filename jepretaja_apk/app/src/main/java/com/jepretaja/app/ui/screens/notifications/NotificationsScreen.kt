package com.jepretaja.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

/** Jenis notifikasi yang lahir dari interaksi sosial, bukan dari transaksi. */
private val JENIS_SOSIAL = setOf("like", "comment", "follow")

private val JUDUL_TAB = listOf("Semua", "Suka", "Komentar", "Pengikut", "Sistem")

/** Notification System (section 17) — inbox bertab seperti TikTok. */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val myUid = authState.uid

    var tab by remember { mutableIntStateOf(0) }
    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Notifikasi", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (myUid == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Masuk untuk melihat notifikasi") }
            return@Scaffold
        }
        val semua by remember(myUid) { viewModel.stream(myUid) }.collectAsState(initial = emptyList())

        // Tab mengikuti pola inbox TikTok. Kategorinya ditentukan field `type`
        // yang sudah lama ada di NotificationModel tapi tidak pernah dipakai
        // untuk apa pun, sehingga kabar booking, suka, dan komentar dulu
        // tercampur dalam satu daftar panjang.
        val items = remember(semua, tab) {
            when (tab) {
                1 -> semua.filter { it.type == "like" }
                2 -> semua.filter { it.type == "comment" }
                3 -> semua.filter { it.type == "follow" }
                4 -> semua.filter { it.type !in JENIS_SOSIAL }
                else -> semua
            }
        }

        Column(Modifier.padding(padding).fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = tab,
            containerColor = AppColors.Background,
            contentColor = AppColors.TextPrimary,
            edgePadding = 12.dp,
        ) {
            JUDUL_TAB.forEachIndexed { index, judul ->
                val jumlahBaru = when (index) {
                    1 -> semua.count { it.type == "like" && it.readAt == null }
                    2 -> semua.count { it.type == "comment" && it.readAt == null }
                    3 -> semua.count { it.type == "follow" && it.readAt == null }
                    4 -> semua.count { it.type !in JENIS_SOSIAL && it.readAt == null }
                    else -> semua.count { it.readAt == null }
                }
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(if (jumlahBaru > 0) "$judul ($jumlahBaru)" else judul) },
                )
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.NotificationsNone,
                    title = if (tab == 0) "Belum ada notifikasi" else "Belum ada di kategori ini",
                    description = "Kabar soal booking, suka, komentar, dan pengikut baru akan muncul di sini.",
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items) { n ->
                    val unread = n.readAt == null
                    PremiumCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = if (unread) 5.dp else 2.dp,
                        onClick = { viewModel.markRead(n.notificationId) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (unread) AppColors.PrimarySoft else AppColors.SurfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Notifications, contentDescription = null,
                                    tint = if (unread) AppColors.Primary else AppColors.TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    n.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (unread) FontWeight.W700 else FontWeight.W500,
                                    color = AppColors.TextPrimary,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(n.body, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                            }
                            if (unread) {
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.Primary))
                            }
                        }
                    }
                }
            }
        }
        }
    }
}
