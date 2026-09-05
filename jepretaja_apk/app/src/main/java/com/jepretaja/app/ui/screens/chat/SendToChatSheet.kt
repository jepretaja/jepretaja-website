package com.jepretaja.app.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppAvatar

/**
 * Kirim satu post ke percakapan yang sudah ada.
 *
 * Sengaja hanya menampilkan percakapan yang SUDAH ada, tidak bisa memulai chat
 * baru dari sini. Di JepretAja percakapan tumbuh dari transaksi — membiarkan
 * siapa pun membuka chat baru lewat tombol bagikan akan menjadikannya jalur
 * pesan tak diminta ke creator mana pun.
 */
@Composable
fun SendToChatSheet(
    myUserId: String,
    pesan: String,
    onDismiss: () -> Unit,
    viewModel: SendToChatViewModel = hiltViewModel(),
) {
    val chats by remember(myUserId) { viewModel.chats(myUserId) }.collectAsState(initial = emptyList())
    var terkirim by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = AppColors.Surface) {
        Text(
            "Kirim ke",
            style = MaterialTheme.typography.titleMedium,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        )
        if (chats.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Belum ada percakapan. Chat terbuka setelah kamu memesan atau menerima booking.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(chats) { chat ->
                    val sudah = terkirim == chat.chatId
                    ListItem(
                        leadingContent = {
                            AppAvatar(url = chat.otherPartyPhotoUrl, name = chat.otherPartyName, size = 40.dp)
                        },
                        headlineContent = { Text(chat.otherPartyName.ifBlank { "Percakapan" }) },
                        trailingContent = {
                            if (sudah) {
                                Text("Terkirim", color = AppColors.Success, style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = AppColors.Primary)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = AppColors.Surface),
                        modifier = Modifier.clickable(enabled = !sudah) {
                            viewModel.send(chat.chatId, myUserId, pesan)
                            terkirim = chat.chatId
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
