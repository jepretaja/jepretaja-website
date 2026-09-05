package com.jepretaja.app.ui.screens.booking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.ui.components.InfoRow
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader

/** Booking Confirmation (section 10) — ringkasan sebelum lanjut ke Payment. */
@Composable
fun BookingConfirmationScreen(
    bookingId: String,
    onBack: () -> Unit,
    onProceedToPayment: (String) -> Unit,
    viewModel: BookingDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(bookingId) { viewModel.load(bookingId) }
    val booking by viewModel.booking.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            TopAppBar(title = { Text("Review Booking") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            })
        },
    ) { padding ->
        when {
            loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppColors.Primary) }
            error != null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = AppColors.Danger)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.load(bookingId) },
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                    ) { Text("Coba Lagi") }
                }
            }
            booking == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Booking tidak ditemukan") }
            else -> {
                val b = booking!!
                Column(Modifier.padding(padding).fillMaxSize()) {
                    Column(Modifier.weight(1f).padding(vertical = 20.dp)) {
                        SectionHeader("Ringkasan")
                        Spacer(Modifier.height(12.dp))
                        PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), elevation = 3.dp) {
                            InfoRow("Paket", b.packageName)
                            b.date?.let { InfoRow("Tanggal", Formatters.date(it)) }
                            InfoRow("Jam", b.time)
                            InfoRow("Lokasi", b.location)
                            b.note?.takeIf { it.isNotBlank() }?.let { InfoRow("Catatan", it) }
                        }
                        Spacer(Modifier.height(16.dp))
                        PremiumCard(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), elevation = 3.dp, color = AppColors.PrimarySoft,
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Total Pembayaran", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
                                Text(Formatters.currency(b.total), style = MaterialTheme.typography.headlineSmall, color = AppColors.Primary)
                            }
                        }
                    }
                    Button(
                        onClick = { onProceedToPayment(bookingId) },
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                        modifier = Modifier.fillMaxWidth().padding(20.dp).height(54.dp),
                    ) { Text("Lanjut ke Pembayaran", style = MaterialTheme.typography.titleSmall) }
                }
            }
        }
    }
}
