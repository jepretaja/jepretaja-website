package com.jepretaja.app.ui.screens.creatordashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
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
import com.jepretaja.app.data.model.PackageModel
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel
import java.util.UUID

/** Creator Package Management (section 9, 17 & 28) — CRUD paket jasa +
 * add-on per paket. */
@Composable
fun CreatorPackageManagementScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorPackageManagementViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid
    var editingPackage by remember { mutableStateOf<PackageModel?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var expandedAddOnsFor by remember { mutableStateOf<String?>(null) }
    // Menghapus paket tidak bisa dibatalkan, dan ikon hapusnya duduk tepat di
    // sebelah ikon edit — satu ketukan meleset tidak boleh langsung
    // memusnahkan paket beserta harganya.
    var menghapus by remember { mutableStateOf<PackageModel?>(null) }

    val scrollBehavior = rememberAppTopBarScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val pesan by viewModel.pesan.collectAsState()

    LaunchedEffect(pesan) {
        pesan?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.pesanDibaca()
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Kelola Paket",
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = { IconButton(onClick = { editingPackage = null; showForm = true }) { Icon(Icons.Default.Add, contentDescription = "Tambah") } },
            )
        },
    ) { padding ->
        if (uid == null) return@Scaffold
        val packages by remember(uid) { viewModel.repository.streamPackages(uid) }.collectAsState(initial = emptyList())

        if (showForm) {
            PackageFormSheet(
                creatorId = uid,
                existing = editingPackage,
                onDismiss = { showForm = false },
                onSave = { pkg -> viewModel.savePackage(pkg); showForm = false },
            )
        }

        if (packages.isEmpty()) {
            EmptyState(
                title = "Belum ada paket",
                description = "Tambahkan paket jasa pertamamu lewat tombol + di pojok kanan atas.",
                icon = Icons.Default.Inventory2,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                items(packages) { pkg ->
                    PremiumCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        onClick = { expandedAddOnsFor = if (expandedAddOnsFor == pkg.packageId) null else pkg.packageId },
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(AppColors.PrimarySoft),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Inventory2, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pkg.name, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${pkg.duration} • ${Formatters.currency(pkg.price)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.TextSecondary,
                                )
                            }
                            IconButton(onClick = { editingPackage = pkg; showForm = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit", tint = AppColors.TextSecondary) }
                            IconButton(onClick = { menghapus = pkg }) { Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = AppColors.Danger) }
                        }
                        if (expandedAddOnsFor == pkg.packageId) {
                            AddOnManagerSection(packageId = pkg.packageId, viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    val paketDihapus = menghapus
    if (paketDihapus != null && uid != null) {
        AlertDialog(
            onDismissRequest = { menghapus = null },
            title = { Text("Hapus paket ini?") },
            text = {
                Text(
                    "\"${paketDihapus.name}\" akan hilang dari profilmu dan tidak bisa " +
                        "dikembalikan. Booking yang sudah berjalan tidak terpengaruh.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePackage(paketDihapus.packageId, uid)
                    menghapus = null
                }) { Text("Hapus", color = AppColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { menghapus = null }) { Text("Batal") } },
        )
    }
}
