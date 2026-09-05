package com.jepretaja.app.ui.screens.booking

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.ui.components.InfoRow
import com.jepretaja.app.ui.components.PremiumCard

@Composable
fun PriceBreakdownCard(state: BookingFormState) {
    val breakdownMap = state.priceBreakdown?.get("breakdown") as? Map<*, *>
    val total = (state.priceBreakdown?.get("total") as? Number)?.toLong()

    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp) {
        when {
            state.previewLoading -> Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            breakdownMap == null -> Text("Menghitung harga...", color = AppColors.TextSecondary)
            else -> Column {
                fun num(key: String) = (breakdownMap[key] as? Number)?.toLong() ?: 0L
                InfoRow("Harga paket", Formatters.currency(num("packagePrice")))
                if (num("addOnsTotal") > 0) InfoRow("Add-on", Formatters.currency(num("addOnsTotal")))
                if (num("travelFee") > 0) InfoRow("Biaya perjalanan", Formatters.currency(num("travelFee")))
                if (num("discount") > 0) InfoRow("Diskon voucher", "- ${Formatters.currency(num("discount"))}", valueColor = AppColors.Success)
                InfoRow("Platform fee (${num("platformFeePercent")}%)", Formatters.currency(num("platformFee")))
                HorizontalDivider(color = AppColors.Border, modifier = Modifier.padding(vertical = 8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", style = MaterialTheme.typography.titleMedium, color = AppColors.TextPrimary)
                    Text(Formatters.currency(total ?: 0), style = MaterialTheme.typography.headlineSmall, color = AppColors.Primary)
                }
            }
        }
    }
}
