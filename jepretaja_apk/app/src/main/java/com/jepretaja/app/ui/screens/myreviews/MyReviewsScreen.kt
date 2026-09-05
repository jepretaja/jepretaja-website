package com.jepretaja.app.ui.screens.myreviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

/** Review Saya (section 28) — review yang pernah ditulis customer ini. */
@Composable
fun MyReviewsScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: MyReviewsViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val myUid = authState.uid

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppColors.Background,
        topBar = { AppTopBar(title = "Review Saya", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (myUid == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Lock,
                    title = "Masuk untuk melihat review Anda",
                    description = "Review yang pernah Anda tulis akan muncul di sini setelah masuk.",
                )
            }
            return@Scaffold
        }
        val reviews by remember(myUid) { viewModel.myReviews(myUid) }.collectAsState(initial = emptyList())
        if (reviews.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.StarBorder,
                    title = "Anda belum menulis review",
                    description = "Setelah booking selesai, Anda bisa memberi ulasan untuk creator di sini.",
                )
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(title = "Review Saya", subtitle = "${reviews.size} review telah Anda tulis")
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(reviews) { r ->
                        PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
                            Row {
                                repeat(r.rating.toInt()) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(r.text, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
                            r.createdAt?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(Formatters.dateShort(it), style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                            }
                            r.creatorReply?.let { reply ->
                                Spacer(Modifier.height(12.dp))
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(AppColors.PrimarySoft, MaterialTheme.shapes.medium)
                                        .padding(12.dp),
                                ) {
                                    Text("Balasan Creator", style = MaterialTheme.typography.labelSmall, color = AppColors.Primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(reply, style = MaterialTheme.typography.bodySmall, color = AppColors.TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
