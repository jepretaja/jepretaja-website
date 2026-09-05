package com.jepretaja.app.ui.screens.help

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SectionHeader
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior

private val faqs = listOf(
    "Bagaimana cara booking creator?" to "Cari creator lewat Search/Explore, buka profilnya, pilih paket, isi tanggal & lokasi, lalu bayar lewat halaman Payment.",
    "Kapan dana ke creator dicairkan?" to "Setelah Anda menekan \"Konfirmasi Selesai\" di halaman Booking, atau otomatis beberapa hari setelah layanan selesai bila Anda tidak konfirmasi manual.",
    "Bagaimana kalau ada masalah dengan creator?" to "Buka detail booking terkait, tekan \"Buka Dispute\", jelaskan masalahnya — tim JepretAja akan meninjau dan memutuskan.",
    "Bagaimana cara refund?" to "Ajukan lewat detail booking sebelum layanan selesai, atau lewat dispute bila sudah terlanjur berjalan.",
)

/** Bantuan (section 28) — FAQ + kontak nyata (bukan tombol dekoratif). */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppColors.Background,
        topBar = { AppTopBar(title = "Bantuan", onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(20.dp))
            SectionHeader(title = "Pertanyaan Umum", subtitle = "Jawaban cepat untuk hal yang sering ditanyakan")
            Spacer(Modifier.height(12.dp))
            PremiumCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                elevation = 3.dp,
                contentPadding = PaddingValues(0.dp),
            ) {
                faqs.forEachIndexed { index, (question, answer) ->
                    FaqItem(question, answer)
                    if (index != faqs.lastIndex) HorizontalDivider(color = AppColors.Border, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            SectionHeader(title = "Hubungi Kami", subtitle = "Tim kami siap membantu")
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ContactRow(
                    icon = Icons.Default.Email,
                    label = "support@jepretaja.app",
                    onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@jepretaja.app?subject=Bantuan%20JepretAja"))
                        context.startActivity(intent)
                    },
                )
                ContactRow(
                    icon = Icons.Filled.Chat,
                    label = "WhatsApp Support",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/6280000000000"))
                        context.startActivity(intent)
                    },
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                question,
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = AppColors.TextSecondary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                answer,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun ContactRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 3.dp, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(40.dp).background(AppColors.PrimarySoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.TextSecondary)
        }
    }
}
