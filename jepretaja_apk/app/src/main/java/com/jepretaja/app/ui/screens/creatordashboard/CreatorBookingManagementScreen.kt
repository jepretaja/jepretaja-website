package com.jepretaja.app.ui.screens.creatordashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.data.repository.BookingRepository
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.StatusBadge
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

@dagger.hilt.android.lifecycle.HiltViewModel
class CreatorBookingManagementViewModel @javax.inject.Inject constructor(
    val repository: BookingRepository,
) : androidx.lifecycle.ViewModel()

/** Creator Booking Management (section 13 & 28) — daftar booking masuk. */
@Composable
fun CreatorBookingManagementScreen(
    onBack: () -> Unit,
    onBookingClick: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorBookingManagementViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Kelola Booking", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (uid == null) return@Scaffold
        val bookings by remember(uid) { viewModel.repository.streamCreatorBookings(uid) }.collectAsState(initial = emptyList())
        if (bookings.isEmpty()) {
            EmptyState(
                title = "Belum ada booking masuk",
                description = "Booking dari customer akan muncul di sini.",
                icon = Icons.Default.CalendarMonth,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                items(bookings) { b ->
                    PremiumCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        onClick = { onBookingClick(b.bookingId) },
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(AppColors.PrimarySoft),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Event, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(b.packageName, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${b.date?.let { Formatters.date(it) } ?: "-"} • ${b.time}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                )
                                Text(Formatters.currency(b.total), style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                            }
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(status = b.status)
                        }
                    }
                }
            }
        }
    }
}
