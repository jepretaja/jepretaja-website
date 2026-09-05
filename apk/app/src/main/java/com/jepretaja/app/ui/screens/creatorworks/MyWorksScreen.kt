package com.jepretaja.app.ui.screens.creatorworks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.StatusBadge
import com.jepretaja.app.ui.state.AuthViewModel

/**
 * "Karya Saya" — daftar semua unggahan beserta status moderasinya.
 *
 * Sebelumnya karya yang berstatus `pending_review` atau `rejected` tidak muncul
 * di mana pun: grid profil hanya menampilkan yang sudah tayang. Dari sisi
 * creator, unggahannya seolah hilang begitu saja, dan keluhan yang muncul
 * hampir selalu "aplikasinya rusak" — padahal karyanya sedang antre ditinjau.
 */
@Composable
fun MyWorksScreen(
    onBack: () -> Unit,
    onPostClick: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: MyWorksViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid

    Scaffold(topBar = { AppTopBar(title = "Karya Saya", onBack = onBack) }) { padding ->
        if (uid == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Masuk untuk melihat karyamu")
            }
            return@Scaffold
        }

        val posts by remember(uid) { viewModel.myPosts(uid) }.collectAsState(initial = emptyList())

        if (posts.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.CollectionsBookmark,
                    title = "Belum ada karya",
                    description = "Unggahanmu akan muncul di sini lengkap dengan status peninjauannya.",
                )
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(posts) { post ->
                PremiumCard(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 3.dp,
                    onClick = { if (post.status == "published") onPostClick(post.postId) },
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        AsyncImage(
                            model = post.thumbnailUrl ?: post.mediaUrls.firstOrNull(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 56.dp, height = 72.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(AppColors.SurfaceVariant),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                post.caption.ifBlank { "(tanpa caption)" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppColors.TextPrimary,
                                maxLines = 2,
                            )
                            Spacer(Modifier.height(6.dp))
                            StatusBadge(post.status)
                            // Angka yang selama ini sudah tercatat di `metrics`
                            // tapi tidak pernah ditunjukkan ke pemiliknya.
                            // Kunjungan profil sengaja tidak ikut: tidak ada
                            // yang mencatatnya, dan menampilkan angka yang tidak
                            // diukur lebih buruk daripada tidak menampilkannya.
                            if (post.status == "published") {
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Statistik(Icons.Default.Visibility, post.viewCount)
                                    Statistik(Icons.Default.FavoriteBorder, post.likeCount)
                                    Statistik(Icons.Default.BookmarkBorder, post.saveCount)
                                    Statistik(Icons.AutoMirrored.Filled.Comment, post.commentCount)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                penjelasanStatus(post.status),
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.TextSecondary,
                            )
                            // Alasan penolakan ditampilkan apa adanya. Menolak
                            // tanpa memberi tahu alasannya membuat creator
                            // mengulang kesalahan yang sama pada unggahan
                            // berikutnya.
                            post.moderationNote?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Catatan admin: $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.Danger,
                                )
                            }
                            post.processingError?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Danger)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Statistik(icon: androidx.compose.ui.graphics.vector.ImageVector, nilai: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text("$nilai", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
    }
}

private fun penjelasanStatus(status: String): String = when (status) {
    "pending_review" -> "Sedang ditinjau admin. Biasanya selesai dalam beberapa jam kerja."
    "published" -> "Sudah tayang di Explore."
    "rejected" -> "Ditolak moderasi. Perbaiki lalu unggah ulang."
    "hidden" -> "Disembunyikan admin dari Explore."
    "processing" -> "Video sedang diproses."
    "processing_failed" -> "Pemrosesan video gagal. Coba unggah ulang."
    "draft" -> "Masih draft."
    else -> status
}
