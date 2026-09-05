package com.jepretaja.app.ui.screens.root

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors

/**
 * Pilihan yang muncul dari tombol + di tengah bottom nav.
 *
 * Satu tombol membuka tiga jenis "unggahan" yang berbeda bentuknya — karya ke
 * feed, foto ke portfolio, dan paket booking — daripada memberi creator tiga
 * tombol terpisah yang berebut tempat di bilah bawah.
 */
@Composable
fun CreatorUploadSheet(
    onDismiss: () -> Unit,
    onUploadMedia: () -> Unit,
    onNewPackage: () -> Unit,
    onManagePortfolio: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Buat Baru", style = MaterialTheme.typography.headlineSmall, color = AppColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Hanya creator yang bisa mengunggah di JepretAja.",
                style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
            )
            Spacer(Modifier.height(20.dp))

            UploadOption(
                icon = Icons.Default.PermMedia,
                title = "Unggah Foto / Video",
                subtitle = "Tayang di feed Explore setelah lolos moderasi",
                onClick = onUploadMedia,
            )
            Spacer(Modifier.height(10.dp))
            UploadOption(
                icon = Icons.Default.PhotoLibrary,
                title = "Tambah ke Portfolio",
                subtitle = "Langsung tampil di profil, tanpa moderasi",
                onClick = onManagePortfolio,
            )
            Spacer(Modifier.height(10.dp))
            UploadOption(
                icon = Icons.Default.Inventory2,
                title = "Buat Paket Booking",
                subtitle = "Paket yang bisa dipesan konsumen dari profilmu",
                onClick = onNewPackage,
            )
        }
    }
}

@Composable
private fun UploadOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(AppColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(AppColors.PrimarySoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
    }
}
