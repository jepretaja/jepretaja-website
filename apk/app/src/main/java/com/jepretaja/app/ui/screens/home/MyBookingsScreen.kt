package com.jepretaja.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.data.repository.BookingRepository
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.StatusBadge
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class MyBookingsViewModel @Inject constructor(private val repository: BookingRepository) : ViewModel() {
    fun bookings(customerId: String): StateFlow<List<com.jepretaja.app.data.model.BookingModel>> =
        repository.streamCustomerBookings(customerId).catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Pesanan yang MASUK ke creator ini. */
    fun incoming(creatorId: String): StateFlow<List<com.jepretaja.app.data.model.BookingModel>> =
        repository.streamCreatorBookings(creatorId).catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

/**
 * Tab Booking pada bilah menu bawah.
 *
 * Sebelumnya tab ini SELALU menampilkan `streamCustomerBookings` — daftar
 * booking yang dibuat sebagai pelanggan. Untuk akun creator itu daftar yang
 * salah dan hampir selalu kosong: pesanan yang masuk ke mereka justru terkubur
 * di dalam Creator Studio, sehingga creator menekan menu "Booking" dan tidak
 * menemukan pekerjaannya sendiri.
 *
 * Sekarang creator mendapat dua tab — "Pesanan Masuk" (sebagai creator) dan
 * "Booking Saya" (sebagai pelanggan, karena creator juga bisa memesan orang
 * lain). Konsumen biasa tidak melihat tab sama sekali, karena bagi mereka
 * hanya ada satu daftar dan menampilkan tab tunggal hanya menambah bising.
 */
@Composable
fun MyBookingsScreen(
    onBookingClick: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: MyBookingsViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val myUid = authState.uid
    val isCreator = authState.isCreator
    var tabTerpilih by remember { mutableStateOf(0) }

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = if (isCreator) "Booking" else "Booking Saya",
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (myUid == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Masuk untuk melihat booking") }
            return@Scaffold
        }

        val sebagaiPelanggan by remember(myUid) { viewModel.bookings(myUid) }.collectAsState()
        val pesananMasuk by remember(myUid, isCreator) {
            if (isCreator) viewModel.incoming(myUid) else MutableStateFlow(emptyList())
        }.collectAsState()

        val menampilkanPesananMasuk = isCreator && tabTerpilih == 0
        val bookings = if (menampilkanPesananMasuk) pesananMasuk else sebagaiPelanggan

        Column(Modifier.padding(padding).fillMaxSize()) {
            if (isCreator) {
                TabRow(
                    selectedTabIndex = tabTerpilih,
                    containerColor = AppColors.Background,
                    contentColor = AppColors.Primary,
                ) {
                    Tab(
                        selected = tabTerpilih == 0,
                        onClick = { tabTerpilih = 0 },
                        text = { Text("Pesanan Masuk") },
                    )
                    Tab(
                        selected = tabTerpilih == 1,
                        onClick = { tabTerpilih = 1 },
                        text = { Text("Booking Saya") },
                    )
                }
            }

            if (bookings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.EventNote,
                        title = if (menampilkanPesananMasuk) "Belum ada pesanan masuk" else "Belum ada booking",
                        description = if (menampilkanPesananMasuk)
                            "Pesanan dari pelanggan akan muncul di sini beserta statusnya."
                        else "Booking yang kamu buat akan tampil di sini beserta statusnya.",
                    )
                }
                return@Column
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(bookings) { b ->
                    PremiumCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 3.dp,
                        onClick = { onBookingClick(b.bookingId) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    b.packageName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.W700,
                                    color = AppColors.TextPrimary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    // Di daftar pesanan masuk yang paling perlu
                                    // dilihat creator adalah KAPAN dan DI MANA
                                    // pekerjaannya. Nama pemesan sengaja tidak
                                    // ditampilkan: dokumen booking hanya menyimpan
                                    // customerId, dan koleksi users tertutup untuk
                                    // orang lain — menampilkannya berarti membuka
                                    // data pribadi seluruh pengguna.
                                    if (menampilkanPesananMasuk) {
                                        listOfNotNull(
                                            b.date?.let { Formatters.date(it) },
                                            b.time.takeIf { it.isNotBlank() },
                                            b.location.takeIf { it.isNotBlank() },
                                        ).joinToString(" • ").ifBlank { "Jadwal belum diatur" }
                                    } else {
                                        "${b.date?.let { Formatters.date(it) } ?: "-"} • ${Formatters.currency(b.total)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                )
                                if (menampilkanPesananMasuk) {
                                    Text(
                                        Formatters.currency(b.total),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = AppColors.Primary,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            StatusBadge(status = b.status)
                        }
                    }
                }
            }
        }
    }
}
