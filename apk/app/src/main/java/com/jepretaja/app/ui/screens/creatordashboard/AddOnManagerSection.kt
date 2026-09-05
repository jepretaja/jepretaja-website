package com.jepretaja.app.ui.screens.creatordashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters

/** Kelola add-on per paket (section 17 & 25) — dipakai di dalam
 * CreatorPackageManagementScreen, expand saat baris paket ditekan. */
@Composable
fun AddOnManagerSection(packageId: String, viewModel: CreatorPackageManagementViewModel) {
    var nameInput by remember { mutableStateOf("") }
    var priceInput by remember { mutableStateOf("") }
    val addOns by remember(packageId) { viewModel.repository.streamAddOns(packageId) }.collectAsState(initial = emptyList())

    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        HorizontalDivider(color = AppColors.Border)
        Spacer(Modifier.height(12.dp))
        Text("Add-on", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(10.dp))
        if (addOns.isEmpty()) {
            Text("Belum ada add-on", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            addOns.forEach { addOn ->
                Row(
                    Modifier.fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(AppColors.SurfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(addOn.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(Formatters.currency(addOn.price), style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
                    IconButton(onClick = { viewModel.deleteAddOn(packageId, addOn.addOnId) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(nameInput, { nameInput = it }, placeholder = { Text("Nama add-on") }, modifier = Modifier.weight(2f), singleLine = true)
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(priceInput, { priceInput = it }, placeholder = { Text("Harga") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), singleLine = true)
            IconButton(onClick = {
                val priceLong = priceInput.toLongOrNull()
                if (nameInput.isNotBlank() && priceLong != null) {
                    viewModel.addAddOn(packageId, nameInput.trim(), priceLong)
                    nameInput = ""; priceInput = ""
                }
            }) { Icon(Icons.Default.AddCircle, contentDescription = "Tambah", tint = AppColors.Primary) }
        }
    }
}
