package com.jepretaja.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors

@Composable
fun StatusBadge(status: String) {
    val color = when (status.lowercase()) {
        "completed", "confirmed", "upcoming", "paid", "customer_confirmed", "funds_released",
        "released", "reviewed", "success", "active", "published", "approved" -> AppColors.Success
        "draft", "pending", "pending_payment", "funds_held", "processing", "requested",
        "refund_requested", "waiting_customer", "waiting_creator" -> AppColors.Warning
        "cancelled", "failed", "rejected", "disputed", "suspended", "processing_failed" -> AppColors.Danger
        else -> AppColors.Info
    }
    // Badge selalu pill (fully-rounded) terlepas dari skala radius kartu —
    // konvensi umum: status chip tidak ikut skala sudut permukaan.
    Text(
        status.replace("_", " ").uppercase(),
        color = color, style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.clip(RoundedCornerShape(percent = 50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
