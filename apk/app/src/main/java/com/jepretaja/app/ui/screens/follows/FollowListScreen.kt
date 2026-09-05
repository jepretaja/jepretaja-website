package com.jepretaja.app.ui.screens.follows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState

/**
 * Daftar Pengikut & Mengikuti, dua tab dalam satu layar seperti di TikTok.
 *
 * Nama dan foto dibaca dari dokumen `follows` itu sendiri, bukan dari koleksi
 * `users` — lihat FollowModel untuk alasannya.
 *
 * Baris di tab "Mengikuti" bisa diketuk karena tujuannya adalah profil creator
 * yang memang publik. Baris di tab "Pengikut" tidak: pengikut umumnya akun
 * konsumen yang tidak punya halaman profil publik sama sekali.
 */
@Composable
fun FollowListScreen(
    targetUserId: String,
    initialTab: Int,
    onBack: () -> Unit,
    onCreatorClick: (String) -> Unit,
    viewModel: FollowListViewModel = hiltViewModel(),
) {
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    val following by remember(targetUserId) { viewModel.following(targetUserId) }
        .collectAsState(initial = emptyList())
    val followers by remember(targetUserId) { viewModel.followers(targetUserId) }
        .collectAsState(initial = emptyList())

    Scaffold(topBar = { AppTopBar(title = "Koneksi", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = AppColors.Background,
                contentColor = AppColors.TextPrimary,
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Mengikuti ${following.size}") },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Pengikut ${followers.size}") },
                )
            }

            val baris = if (tab == 0) following else followers
            if (baris.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = if (tab == 0) Icons.Default.PersonSearch else Icons.Default.GroupAdd,
                        title = if (tab == 0) "Belum mengikuti siapa pun" else "Belum ada pengikut",
                        description = if (tab == 0) {
                            "Creator yang kamu ikuti akan muncul di sini, dan karyanya masuk ke tab Following."
                        } else {
                            "Orang yang mengikutimu akan muncul di sini."
                        },
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(baris) { f ->
                        val creatorMode = tab == 0
                        val nama = if (creatorMode) {
                            f.creatorName.ifBlank { "Creator" }
                        } else {
                            f.userName.ifBlank { "Pengguna" }
                        }
                        val foto = if (creatorMode) f.creatorPhotoUrl else f.userPhotoUrl

                        ListItem(
                            leadingContent = { AppAvatar(url = foto, name = nama, size = 44.dp) },
                            headlineContent = { Text(nama, color = AppColors.TextPrimary) },
                            supportingContent = {
                                Text(
                                    if (creatorMode) "Ketuk untuk lihat profil" else "Mengikutimu",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = AppColors.Background),
                            modifier = if (creatorMode) {
                                Modifier.clickable { onCreatorClick(f.creatorId) }
                            } else {
                                Modifier
                            },
                        )
                        HorizontalDivider(color = AppColors.Border, modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}
