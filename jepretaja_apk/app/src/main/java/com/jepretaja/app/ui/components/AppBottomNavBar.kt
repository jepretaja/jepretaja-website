package com.jepretaja.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jepretaja.app.core.theme.AppColors

data class NavTab(val route: String, val label: String, val icon: ImageVector, val iconOutline: ImageVector)

/**
 * Bottom nav yang "melayang" di atas konten (kartu bulat + shadow, terpisah
 * dari tepi layar) menggantikan `NavigationBar` Material default yang datar
 * menempel di tepi.
 *
 * [centerAction] adalah tombol + di TENGAH deretan menu, dan sengaja hanya
 * diberikan untuk akun creator: di aplikasi ini hanya creator yang boleh
 * mengunggah karya maupun membuat paket booking, jadi konsumen dan tamu cukup
 * melihat empat menu tanpa tombol yang tidak akan pernah bisa mereka pakai.
 */
@Composable
fun AppBottomNavBar(
    tabs: List<NavTab>,
    currentRoute: String?,
    onSelect: (String) -> Unit,
    centerAction: (() -> Unit)? = null,
    centerActionLabel: String = "Unggah",
) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.18f))
                .clip(RoundedCornerShape(28.dp))
                .background(AppColors.Surface)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Tombol + disisipkan tepat di tengah deretan, bukan ditempel di
            // ujung — posisinya yang simetris itu yang membuatnya terbaca
            // sebagai aksi utama, bukan sekadar menu kelima.
            val middle = tabs.size / 2
            tabs.forEachIndexed { index, tab ->
                if (centerAction != null && index == middle) {
                    CenterUploadButton(label = centerActionLabel, onClick = centerAction)
                }
                NavItem(tab = tab, selected = currentRoute == tab.route, onClick = { onSelect(tab.route) })
            }
        }
    }
}

@Composable
private fun NavItem(tab: NavTab, selected: Boolean, onClick: () -> Unit) {
    val indicatorColor by animateColorAsState(if (selected) AppColors.PrimarySoft else Color.Transparent, label = "navIndicator")
    val contentColor by animateColorAsState(if (selected) AppColors.Primary else AppColors.TextSecondary, label = "navContent")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
    ) {
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(indicatorColor).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Icon(if (selected) tab.icon else tab.iconOutline, contentDescription = tab.label, tint = contentColor, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
}

/** Tombol unggah creator — lingkaran gradient yang sedikit menonjol ke atas. */
@Composable
private fun CenterUploadButton(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .shadow(10.dp, CircleShape, spotColor = AppColors.Primary.copy(alpha = 0.5f))
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryDark))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = label, tint = AppColors.OnPrimary, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AppColors.Primary)
    }
}
