package com.jepretaja.app.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.data.local.AppPreferences
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.repository.ExploreRepository
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WatchHistoryViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val repository: ExploreRepository,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<ExplorePostModel>>(emptyList())
    val posts: StateFlow<List<ExplorePostModel>> = _posts.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { muat() }

    /**
     * Riwayat disimpan sebagai daftar id di perangkat, lalu postnya diambil satu
     * per satu saat layar ini dibuka.
     *
     * Sengaja tidak menyimpan salinan datanya: karya bisa dihapus atau ditolak
     * moderasi setelah ditonton, dan riwayat yang menyimpan salinan akan terus
     * menampilkan sesuatu yang sudah tidak boleh tampil di mana pun lagi.
     */
    fun muat() {
        viewModelScope.launch {
            _loading.value = true
            _posts.value = prefs.watchHistory.mapNotNull { id ->
                runCatching { repository.getPost(id) }.getOrNull()
            }
            _loading.value = false
        }
    }

    fun bersihkan() {
        prefs.clearWatchHistory()
        _posts.value = emptyList()
    }
}

@Composable
fun WatchHistoryScreen(
    onBack: () -> Unit,
    onPostClick: (String) -> Unit,
    viewModel: WatchHistoryViewModel = hiltViewModel(),
) {
    val posts by viewModel.posts.collectAsState()
    val loading by viewModel.loading.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Riwayat Tontonan",
                onBack = onBack,
                actions = {
                    if (posts.isNotEmpty()) {
                        TextButton(onClick = { viewModel.bersihkan() }) { Text("Hapus semua") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            posts.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = "Belum ada riwayat",
                    description = "Karya yang kamu tonton di Explore akan tercatat di sini, hanya di perangkat ini.",
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(1.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                items(posts) { post ->
                    Box(
                        Modifier.aspectRatio(0.75f).background(AppColors.SurfaceVariant)
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
