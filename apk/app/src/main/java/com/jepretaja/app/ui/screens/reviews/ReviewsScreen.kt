package com.jepretaja.app.ui.screens.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.data.model.ReviewModel
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior

/** Rating & Review (section 16 & 28). */
@Composable
fun ReviewsScreen(
    creatorId: String,
    onBack: () -> Unit,
    viewModel: ReviewsViewModel = hiltViewModel(),
) {
    val reviews by remember(creatorId) { viewModel.reviews(creatorId) }.collectAsState()

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Review", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (reviews.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.StarBorder,
                    title = "Belum ada review",
                    description = "Ulasan dari klien akan muncul setelah sesi pertama selesai.",
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(reviews) { review -> ReviewItem(review) }
            }
        }
    }
}

@Composable
private fun ReviewItem(review: ReviewModel) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Person, contentDescription = null, tint = AppColors.TextSecondary,
                modifier = Modifier.size(36.dp).background(AppColors.PrimarySoft, CircleShape).padding(7.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                review.customerName.ifEmpty { "Pengguna" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W700,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            review.createdAt?.let {
                Text(Formatters.dateShort(it), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            repeat(5) { i ->
                Icon(
                    Icons.Default.Star, contentDescription = null,
                    tint = if (i < review.rating) AppColors.Accent else AppColors.Border,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(review.text, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
        review.creatorReply?.let {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().background(AppColors.Background, MaterialTheme.shapes.medium).padding(12.dp)) {
                Text("Balasan creator: $it", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            }
        }
    }
}
