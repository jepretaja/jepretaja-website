package com.jepretaja.app.ui.screens.search

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.location.LocationServices
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SkeletonList
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.data.model.CreatorModel

private val radiusOptions = listOf(5.0, 10.0, 25.0, 50.0, 100.0)

/** Nearby (section 20) — permission lokasi nyata + jarak Haversine. */
@Composable
fun NearbyScreen(
    onBack: () -> Unit,
    onCreatorClick: (String) -> Unit,
    viewModel: NearbyViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var radiusKm by remember { mutableStateOf(25.0) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Pair<CreatorModel, Double>>>(emptyList()) }
    var loadingResults by remember { mutableStateOf(false) }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionDenied = true
            return
        }
        permissionDenied = false
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) { lat = loc.latitude; lng = loc.longitude; error = null }
            else error = "Gagal mendapatkan lokasi. Pastikan GPS aktif."
        }.addOnFailureListener { error = "Gagal mendapatkan lokasi: ${it.message}" }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchLocation() else permissionDenied = true
    }

    LaunchedEffect(Unit) { fetchLocation() }

    LaunchedEffect(lat, lng, radiusKm) {
        val currentLat = lat; val currentLng = lng
        if (currentLat != null && currentLng != null) {
            loadingResults = true
            results = viewModel.nearby(currentLat, currentLng, radiusKm)
            loadingResults = false
        }
    }

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { AppTopBar(title = "Creator Terdekat", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        when {
            permissionDenied -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocationOff, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Izin lokasi ditolak. Aktifkan untuk memakai fitur Nearby.", color = AppColors.TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary, contentColor = AppColors.OnPrimary),
                    ) { Text("Coba Lagi") }
                }
            }
            error != null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!, color = AppColors.Danger) }
            lat == null || loadingResults -> SkeletonList(count = 7, modifier = Modifier.padding(padding))
            else -> Column(Modifier.padding(padding)) {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(radiusOptions) { r ->
                        FilterChip(
                            selected = radiusKm == r,
                            onClick = { radiusKm = r },
                            label = { Text("${r.toInt()} km") },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = 50),
                        )
                    }
                }
                if (results.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = Icons.Default.LocationOff,
                            title = "Tidak ada creator di sekitar",
                            description = "Perlebar radius pencarian untuk menemukan lebih banyak creator.",
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(results) { (creator, distance) ->
                            PremiumCard(
                                onClick = { onCreatorClick(creator.creatorId) },
                                elevation = 3.dp,
                                contentPadding = PaddingValues(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppAvatar(url = creator.coverUrl, name = creator.displayName, size = 46.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(creator.displayName, style = MaterialTheme.typography.titleSmall)
                                            if (creator.verified) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Default.Verified, contentDescription = null, tint = AppColors.Info, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                        Text(
                                            "${creator.city ?: "-"} • ★ ${"%.1f".format(creator.rating)}",
                                            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                                        )
                                    }
                                    Text(
                                        "%.1f km".format(distance),
                                        color = AppColors.Primary, style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
