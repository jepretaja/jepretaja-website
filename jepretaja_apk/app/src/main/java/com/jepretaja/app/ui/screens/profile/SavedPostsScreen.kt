package com.jepretaja.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.premiumShadow
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

/**
 * Post Explore yang disimpan pengguna.
 *
 * Tombol simpan sudah lama menulis ke koleksi `explore_saves`, tapi tidak ada
 * satu layar pun yang membacanya kembali — jadi menyimpan post secara efektif
 * tidak ada gunanya. Layar ini yang membuatnya bisa dilihat lagi.
 */
@Composable
fun SavedPostsScreen(
    onBack: () -> Unit,
    onPostClick: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: SavedPostsViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Tersimpan", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (uid == null) return@Scaffold
        val posts by remember(uid) { viewModel.savedPosts(uid) }.collectAsState(initial = emptyList())

        if (posts.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.BookmarkBorder,
                    title = "Belum ada yang disimpan",
                    description = "Ketuk ikon bookmark di Explore untuk menyimpan karya yang kamu suka, supaya gampang dicari lagi.",
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(posts) { post ->
                    Box(
                        Modifier.aspectRatio(0.75f)
                            .premiumShadow(4.dp, MaterialTheme.shapes.medium)
                            .clip(MaterialTheme.shapes.medium)
                            .background(AppColors.SurfaceVariant)
                            .clickable { onPostClick(post.postId) },
                    ) {
                        AsyncImage(
                            model = post.thumbnailUrl ?: post.mediaUrls.firstOrNull(),
                            contentDescription = post.caption,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (post.type == "video") {
                            Icon(
                                Icons.Default.PlayArrow, contentDescription = null, tint = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.small)
                                    .padding(4.dp).size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
