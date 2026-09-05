package com.jepretaja.app.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.data.model.ChatModel
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.SkeletonList
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

private val LOKAL_ID = Locale("in", "ID")

/**
 * Daftar percakapan.
 *
 * Versi sebelumnya menampilkan tiga hal saja — avatar, nama, dan pesan terakhir —
 * masing-masing dalam kartu bayangan terpisah dengan jarak 10dp. Yang hilang
 * adalah semua yang membuat daftar chat bisa dipindai: **kapan** pesan terakhir
 * datang, dan **percakapan mana yang masih menunggu jawaban**. Tanpa keduanya,
 * urutan daftar (yang memang diurut waktu) tidak bisa dibaca sebagai urutan
 * waktu, dan pesan baru tidak berbeda tampilannya dari percakapan bulan lalu.
 *
 * Kartu bertumpuk juga diganti baris rapat berpemisah tipis — bentuk yang
 * dipakai hampir semua aplikasi pesan, karena di sini yang dicari orang adalah
 * satu nama di antara puluhan, bukan satu kartu untuk dinikmati.
 */
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    authViewModel: AuthViewModel,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val myUid = authState.uid

    val scrollBehavior = rememberAppTopBarScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppColors.Background,
        topBar = { AppTopBar(title = "Chat", scrollBehavior = scrollBehavior) },
    ) { padding ->
        if (myUid == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Masuk untuk melihat chat")
            }
            return@Scaffold
        }

        val chats by remember(myUid) { viewModel.chats(myUid) }.collectAsState()
        val belumDibaca by remember(myUid) { viewModel.belumDibaca(myUid) }.collectAsState()
        val aktifTerakhir by viewModel.aktifTerakhir.collectAsState()

        // Titik hijau hanya untuk lawan bicara yang berstatus creator: hanya
        // dokumen creator yang punya catatan waktu aktif terakhir.
        LaunchedEffect(chats) {
            viewModel.pantauKehadiran(chats.map { it.creatorId }.filter { it != myUid })
        }

        var sudahPernahIsi by remember { mutableStateOf(false) }
        LaunchedEffect(chats) { if (chats.isNotEmpty()) sudahPernahIsi = true }

        when {
            // Daftar kosong pada detik pertama belum tentu berarti tidak ada
            // percakapan — StateFlow-nya memang mulai dari daftar kosong.
            chats.isEmpty() && !sudahPernahIsi -> Box(Modifier.padding(padding).fillMaxSize()) {
                SkeletonList(count = 7, modifier = Modifier.padding(top = 8.dp))
            }

            chats.isEmpty() -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.ChatBubbleOutline,
                    title = "Belum ada percakapan",
                    description = "Hubungi creator lewat profilnya untuk mulai mengobrol.",
                )
            }

            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(chats, key = { it.chatId }) { chat ->
                    BarisChat(
                        chat = chat,
                        belumDibaca = belumDibaca[chat.chatId] ?: 0,
                        online = chat.creatorId != myUid && sedangOnline(aktifTerakhir[chat.creatorId]),
                        onClick = { onChatClick(chat.chatId) },
                    )
                    HorizontalDivider(
                        color = AppColors.Border,
                        modifier = Modifier.padding(start = 84.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarisChat(
    chat: ChatModel,
    belumDibaca: Int,
    online: Boolean,
    onClick: () -> Unit,
) {
    val adaYangBaru = belumDibaca > 0

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AppAvatar(url = chat.otherPartyPhotoUrl, name = chat.otherPartyName.ifEmpty { "?" }, size = 52.dp)
            if (online) {
                // Cincin warna latar di sekeliling titik: tanpa itu, titik hijau
                // di atas foto berlatar hijau menghilang sama sekali.
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(AppColors.Background),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(AppColors.Success))
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.otherPartyName.ifEmpty { "Percakapan" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (adaYangBaru) FontWeight.W700 else FontWeight.W600,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    waktuSingkat(chat.updatedAt?.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    // Waktu ikut menyala saat ada pesan baru — dua penanda untuk
                    // satu keadaan, supaya tidak bergantung pada warna saja.
                    color = if (adaYangBaru) AppColors.Primary else AppColors.TextSecondary,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val teks = chat.lastMessage
                if (teks.startsWith("\uD83D\uDCF7")) {
                    Icon(
                        Icons.Default.Image, contentDescription = null,
                        tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    teks.removePrefix("\uD83D\uDCF7 ").ifEmpty { "Belum ada pesan" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (adaYangBaru) AppColors.TextPrimary else AppColors.TextSecondary,
                    fontWeight = if (adaYangBaru) FontWeight.W600 else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (adaYangBaru) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(AppColors.Primary)
                            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (belumDibaca > 99) "99+" else "$belumDibaca",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.W700,
                            color = AppColors.OnPrimary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "14:32" hari ini, "Kemarin", nama hari dalam sepekan, sisanya tanggal.
 *
 * Jam penuh untuk pesan sebulan lalu tidak menjawab apa pun, dan tanggal untuk
 * pesan sepuluh menit lalu memaksa orang menghitung sendiri.
 */
private fun waktuSingkat(waktu: Date?): String {
    if (waktu == null) return ""
    val sekarang = Calendar.getInstance()
    val itu = Calendar.getInstance().apply { time = waktu }

    val hariSama = sekarang.get(Calendar.YEAR) == itu.get(Calendar.YEAR) &&
        sekarang.get(Calendar.DAY_OF_YEAR) == itu.get(Calendar.DAY_OF_YEAR)
    if (hariSama) return SimpleDateFormat("HH:mm", LOKAL_ID).format(waktu)

    val kemarin = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val kemarinSama = kemarin.get(Calendar.YEAR) == itu.get(Calendar.YEAR) &&
        kemarin.get(Calendar.DAY_OF_YEAR) == itu.get(Calendar.DAY_OF_YEAR)
    if (kemarinSama) return "Kemarin"

    val selisihHari = (sekarang.timeInMillis - itu.timeInMillis) / (24 * 60 * 60 * 1000)
    if (selisihHari in 0..6) return SimpleDateFormat("EEEE", LOKAL_ID).format(waktu)

    return SimpleDateFormat("d MMM yyyy", LOKAL_ID).format(waktu)
}
