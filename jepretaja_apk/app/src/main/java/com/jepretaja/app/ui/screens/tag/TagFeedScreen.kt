package com.jepretaja.app.ui.screens.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState

/**
 * Halaman satu hashtag atau kategori — tujuan ketukan pada "#wedding" di caption.
 *
 * Bentuknya grid, bukan feed layar penuh: orang yang membuka halaman tagar
 * sedang menelusuri, bukan menonton satu per satu, dan grid membuatnya bisa
 * memilih.
 */
@Composable
fun TagFeedScreen(
    tag: String,
    isCategory: Boolean,
    onBack: () -> Unit,
    onPostClick: (String) -> Unit,
    viewModel: TagFeedViewModel = hiltViewModel(),
) {
    val posts by remember(tag, isCategory) {
        if (isCategory) viewModel.byCategory(tag) else viewModel.byTag(tag)
    }.collectAsState(initial = emptyList())

    Scaffold(
        topBar = { AppTopBar(title = if (isCategory) tag else "#$tag", onBack = onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(AppColors.PrimarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Tag, contentDescription = null, tint = AppColors.Primary)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (isCategory) tag else "#$tag",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.W700,
                        color = AppColors.TextPrimary,
                    )
                    Text(
                        "${posts.size} karya",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                    )
                }
            }
            HorizontalDivider(color = AppColors.Border)

            if (posts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Tag,
                        title = "Belum ada karya",
                        description = if (isCategory) {
                            "Belum ada karya tayang di kategori ini."
                        } else {
                            "Belum ada karya yang memakai tagar ini."
                        },
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(1.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(posts) { post ->
                        Box(
                            Modifier.aspectRatio(0.75f)
                                .background(AppColors.SurfaceVariant)
                                .clickable { onPostClick(post.postId) },
                        ) {
                            AsyncImage(
                                model = post.thumbnailUrl ?: post.mediaUrls.firstOrNull(),
                                contentDescription = post.caption,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }
}
