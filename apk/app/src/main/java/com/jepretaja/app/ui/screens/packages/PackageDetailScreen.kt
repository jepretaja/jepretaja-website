package com.jepretaja.app.ui.screens.packages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.components.GradientHeroCard
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader

/** Package Detail (section 9 & 28). */
@Composable
fun PackageDetailScreen(
    onBack: () -> Unit,
    onBookingClick: (String) -> Unit,
    viewModel: PackageDetailViewModel = hiltViewModel(),
) {
    val pkg by viewModel.pkg.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val notFound by viewModel.notFound.collectAsState()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Detail Paket", style = MaterialTheme.typography.headlineSmall) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
        })
    }) { padding ->
        when {
            loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error!!, color = AppColors.Danger)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.load() }) { Text("Coba Lagi") }
                }
            }
            notFound || pkg == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Paket tidak ditemukan") }
            else -> {
                val p = pkg!!
                Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(4.dp))
                    GradientHeroCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text(p.name, style = MaterialTheme.typography.headlineMedium, color = AppColors.OnPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(Formatters.currency(p.price), style = MaterialTheme.typography.displaySmall, color = AppColors.OnPrimary)
                    }
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Yang Kamu Dapat")
                    Spacer(Modifier.height(12.dp))
                    PremiumCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), elevation = 4.dp) {
                        PackageAttributeRow(Icons.Default.Schedule, "Durasi", p.duration)
                        p.personnel?.let { PackageAttributeRow(Icons.Default.Groups, "Personel", it) }
                        p.output?.let { PackageAttributeRow(Icons.Default.Inventory2, "Output", it) }
                    }
                    Spacer(Modifier.height(24.dp))
                    SectionHeader("Deskripsi")
                    Spacer(Modifier.height(10.dp))
                    Text(
                        p.description,
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                    BigPrimaryButton(
                        text = "Booking Sekarang",
                        onClick = { onBookingClick(p.packageId) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }
}

@Composable
private fun PackageAttributeRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(34.dp).clip(MaterialTheme.shapes.small).background(AppColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Text(label, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
    }
}
