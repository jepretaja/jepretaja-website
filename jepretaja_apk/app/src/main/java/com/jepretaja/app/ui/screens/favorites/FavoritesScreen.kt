package com.jepretaja.app.ui.screens.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.CreatorCard
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

/** Favorites/Saved — daftar creator yang di-follow (section 28). */
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onCreatorClick: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val myUid = authState.uid

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppColors.Background,
        topBar = { AppTopBar(title = "Favorit", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (myUid == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Lock,
                    title = "Masuk untuk melihat favorit",
                    description = "Simpan creator favorit Anda dan lihat semuanya di sini setelah masuk.",
                )
            }
            return@Scaffold
        }
        val creators by remember(myUid) { viewModel.favorites(myUid) }.collectAsState(initial = emptyList())
        if (creators.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.FavoriteBorder,
                    title = "Belum ada favorit",
                    description = "Ketuk ikon hati di profil creator untuk menyimpannya di sini.",
                )
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(title = "Favorit", subtitle = "${creators.size} creator tersimpan")
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(creators) { creator ->
                        CreatorCard(
                            creator = creator,
                            onClick = { onCreatorClick(creator.creatorId) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
