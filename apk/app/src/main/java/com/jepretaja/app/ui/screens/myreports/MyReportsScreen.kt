package com.jepretaja.app.ui.screens.myreports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.data.model.ReportModel
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.StatusBadge
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

private fun targetTypeLabel(targetType: String): String = when (targetType) {
    "explore_post" -> "Konten Explore"
    "creator" -> "Creator"
    "chat_message" -> "Pesan Chat"
    "review" -> "Review"
    else -> targetType.replace("_", " ").replaceFirstChar { it.uppercase() }
}

/** Laporan Saya (section 28) — laporan konten/pengguna yang pernah diajukan customer ini. */
@Composable
fun MyReportsScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: MyReportsViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val myUid = authState.uid

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppColors.Background,
        topBar = { AppTopBar(title = "Laporan Saya", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (myUid == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Lock,
                    title = "Masuk untuk melihat laporan Anda",
                    description = "Laporan yang pernah Anda ajukan akan muncul di sini setelah masuk.",
                )
            }
            return@Scaffold
        }
        val reports by remember(myUid) { viewModel.myReports(myUid) }.collectAsState(initial = emptyList())
        if (reports.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Flag,
                    title = "Anda belum pernah membuat laporan",
                    description = "Laporan konten atau pengguna yang Anda ajukan akan tercatat di sini.",
                )
            }
        } else {
            Column(Modifier.padding(padding).fillMaxSize()) {
                Spacer(Modifier.height(20.dp))
                SectionHeader(title = "Laporan Saya", subtitle = "${reports.size} laporan telah diajukan")
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(reports) { r -> ReportRow(r) }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(r: ReportModel) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(targetTypeLabel(r.targetType), style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
            StatusBadge(status = r.status)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            r.reason.replace("_", " ").replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
        r.createdAt?.let {
            Spacer(Modifier.height(8.dp))
            Text(Formatters.dateShort(it), style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
        }
    }
}
