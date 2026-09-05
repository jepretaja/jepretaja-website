package com.jepretaja.app.ui.screens.creatordashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.data.model.PackageModel
import com.jepretaja.app.ui.components.BigPrimaryButton
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackageFormSheet(
    creatorId: String,
    existing: PackageModel?,
    onDismiss: () -> Unit,
    onSave: (PackageModel) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var price by remember { mutableStateOf(existing?.price?.toString() ?: "") }
    var duration by remember { mutableStateOf(existing?.duration ?: "") }
    var personnel by remember { mutableStateOf(existing?.personnel ?: "") }
    var output by remember { mutableStateOf(existing?.output ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var pesanValidasi by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                if (existing == null) "Paket Baru" else "Edit Paket",
                style = MaterialTheme.typography.headlineSmall,
                color = AppColors.TextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Paket ini yang dilihat & dipesan konsumen di profil kamu.",
                style = MaterialTheme.typography.bodySmall,
                color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(name, { name = it; pesanValidasi = null }, label = { Text("Nama paket") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = price,
                // Angka saja. Sebelumnya "Rp 1.500.000" yang diketik apa adanya
                // lolos ke `toLongOrNull() ?: 0` dan tersimpan sebagai paket
                // seharga NOL yang bisa dipesan konsumen.
                onValueChange = { baru -> price = baru.filter { it.isDigit() }.take(12); pesanValidasi = null },
                label = { Text("Harga (Rp)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(duration, { duration = it; pesanValidasi = null }, label = { Text("Durasi (mis. 4 jam)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(personnel, { personnel = it }, label = { Text("Personel (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(output, { output = it }, label = { Text("Output (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(description, { description = it }, label = { Text("Deskripsi") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            pesanValidasi?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = AppColors.Danger, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(22.dp))
            BigPrimaryButton(
                text = "Simpan",
                onClick = {
                    // Paket yang belum lengkap TIDAK boleh tersimpan: ia langsung
                    // tayang di profil dan bisa dipesan konsumen, jadi paket tanpa
                    // nama atau berharga nol bukan sekadar catatan yang berantakan
                    // — ia pesanan yang harus dibatalkan secara manual nanti.
                    val harga = price.toLongOrNull() ?: 0
                    pesanValidasi = when {
                        name.isBlank() -> "Nama paket belum diisi."
                        harga <= 0 -> "Harga paket belum diisi."
                        duration.isBlank() -> "Durasi belum diisi, mis. \"4 jam\"."
                        else -> null
                    }
                    if (pesanValidasi != null) return@BigPrimaryButton

                    onSave(
                        PackageModel(
                            packageId = existing?.packageId ?: UUID.randomUUID().toString(),
                            creatorId = creatorId,
                            name = name.trim(),
                            price = harga,
                            duration = duration.trim(),
                            personnel = personnel.trim().ifBlank { null },
                            output = output.trim().ifBlank { null },
                            description = description.trim(),
                            active = true,
                        ),
                    )
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
