package com.jepretaja.app.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.premiumShadow
import com.jepretaja.app.ui.state.AuthViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val LOKAL_ID = Locale("in", "ID")
private val JAM = SimpleDateFormat("HH:mm", LOKAL_ID)

/**
 * Ruang chat.
 *
 * Lima hal yang ditambahkan:
 *
 * 1. **Pratinjau gambar sebelum kirim.** Dulu memilih foto langsung
 *    mengunggahnya. Salah pilih di galeri berarti foto itu sudah terkirim, dan
 *    tidak ada cara menariknya kembali.
 * 2. **Status per gelembung** — mengirim, terkirim, dibaca, gagal. Sebelumnya
 *    hanya ada centang untuk pesan yang sudah ada di server; selama pengiriman
 *    berlangsung, layar tidak berubah sama sekali.
 * 3. **Kirim ulang di tempat.** Pesan gagal tetap berada di posisinya dengan
 *    tombol coba lagi, bukan dikembalikan ke kolom ketik sebagai teks mentah.
 * 4. **Pemisah tanggal**, supaya "jam 09.14" punya arti.
 * 5. **Muat pesan lama.** Query lama menarik seluruh riwayat tiap kali ruang
 *    dibuka — satu pembacaan per pesan, tanpa batas atas.
 */
@Composable
fun ChatRoomScreen(
    chatId: String,
    authViewModel: AuthViewModel,
    onBack: () -> Unit = {},
    viewModel: ChatRoomViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val myId = authState.uid
    val chat by remember(chatId) { viewModel.chat(chatId) }.collectAsState()
    val messages by remember(chatId, myId) {
        if (myId != null) viewModel.messages(chatId, myId)
        else MutableStateFlow<List<com.jepretaja.app.data.model.MessageModel>>(emptyList())
    }.collectAsState()
    val tertunda by viewModel.tertunda.collectAsState()
    val memuatLama by viewModel.memuatLama.collectAsState()
    val adaLagi by viewModel.adaLagi.collectAsState()

    var text by remember { mutableStateOf("") }
    var pratinjauKirim by remember { mutableStateOf<android.net.Uri?>(null) }
    var gambarDilihat by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val otherPartyId = remember(chat, myId) {
        chat?.let { if (it.customerId == myId) it.creatorId else it.customerId }
    }
    // Kehadiran hanya bisa dibaca untuk creator — hanya dokumen creator yang
    // punya catatan waktu aktif terakhir.
    val idCreatorLawan = remember(chat, myId) { chat?.creatorId?.takeIf { it != myId && it.isNotBlank() } }
    val aktifTerakhir by remember(idCreatorLawan) {
        if (idCreatorLawan != null) viewModel.aktifTerakhir(idCreatorLawan) else MutableStateFlow<Long?>(null)
    }.collectAsState()

    LaunchedEffect(chatId, myId, messages.size) { if (myId != null) viewModel.markRead(chatId, myId) }

    val blockedFlow = remember(myId, otherPartyId) {
        if (myId != null && otherPartyId != null) viewModel.isBlocked(myId, otherPartyId) else MutableStateFlow(false)
    }
    val blocked by blockedFlow.collectAsState()

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pratinjauKirim = uri
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val pesan by viewModel.pesan.collectAsState()
    LaunchedEffect(pesan) { pesan?.let { snackbarHostState.showSnackbar(it); viewModel.pesanDibaca() } }

    val baris = remember(messages, tertunda, myId) { susunBaris(messages, tertunda, myId) }

    Scaffold(
        containerColor = AppColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                title = {
                    // Kepala ruang chat menampilkan siapa lawan bicaranya, bukan
                    // kata "Chat". Nama dan wajah adalah satu-satunya cara
                    // memastikan pesan tidak dikirim ke percakapan yang keliru.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppAvatar(
                            url = chat?.otherPartyPhotoUrl,
                            name = chat?.otherPartyName?.ifEmpty { "?" } ?: "?",
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                chat?.otherPartyName?.ifEmpty { "Percakapan" } ?: "Percakapan",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            idCreatorLawan?.let {
                                val online = sedangOnline(aktifTerakhir)
                                Text(
                                    if (online) "Online" else keteranganAktif(aktifTerakhir),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (online) AppColors.Success else AppColors.TextSecondary,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (myId != null && otherPartyId != null && otherPartyId.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val blockedByMe = viewModel.isBlockedByMe(myId, otherPartyId)
                                viewModel.toggleBlock(myId, otherPartyId, blockedByMe)
                            }
                        }) {
                            Icon(
                                if (blocked) Icons.Default.Block else Icons.Default.MoreVert,
                                contentDescription = if (blocked) "Buka blokir" else "Menu",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.Background,
                    titleContentColor = AppColors.TextPrimary,
                    navigationIconContentColor = AppColors.TextPrimary,
                    actionIconContentColor = AppColors.TextPrimary,
                ),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            if (baris.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.WavingHand,
                        title = "Belum ada pesan",
                        description = "Sapa creator untuk menanyakan ketersediaan tanggal dan detail paket.",
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    items(baris, key = { it.kunci }) { item ->
                        when (item) {
                            is BarisRuang.Pemisah -> PemisahTanggal(item.label)
                            is BarisRuang.Balon -> GelembungPesan(
                                balon = item,
                                onKirimUlang = {
                                    if (myId != null) viewModel.kirimUlang(chatId, myId, item.localId.orEmpty())
                                },
                                onBatal = { viewModel.batalkanTertunda(item.localId.orEmpty()) },
                                onLihatGambar = { url -> gambarDilihat = url },
                            )
                        }
                    }

                    // reverseLayout: item terakhir dalam daftar tampil paling
                    // ATAS, jadi di sinilah tombol "muat pesan lama" berada —
                    // tepat di tempat riwayatnya terputus.
                    if (adaLagi) {
                        item(key = "muat_lama") {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (memuatLama) {
                                    CircularProgressIndicator(
                                        Modifier.size(22.dp), strokeWidth = 2.dp, color = AppColors.Primary,
                                    )
                                } else {
                                    TextButton(onClick = { viewModel.muatLebihLama() }) {
                                        Text("Muat pesan lama", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (blocked) {
                Box(Modifier.fillMaxWidth().background(AppColors.Danger.copy(alpha = 0.08f)).padding(14.dp)) {
                    Text(
                        "Percakapan ini diblokir. Buka blokir lewat menu di kanan atas untuk kirim pesan lagi.",
                        color = AppColors.Danger, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    )
                }
            } else {
                pratinjauKirim?.let { uri ->
                    PratinjauSebelumKirim(
                        uri = uri,
                        onBatal = { pratinjauKirim = null },
                        onKirim = {
                            if (myId != null) viewModel.sendImage(chatId, myId, uri)
                            pratinjauKirim = null
                        },
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = "Kirim gambar")
                    }
                    OutlinedTextField(
                        text, { text = it },
                        placeholder = { Text("Tulis pesan...") },
                        shape = RoundedCornerShape(percent = 50),
                        maxLines = 4,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (text.isNotBlank() && myId != null) {
                                viewModel.sendMessage(chatId, myId, text.trim())
                                text = ""
                            }
                        },
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(AppColors.Primary),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim", tint = AppColors.OnPrimary) }
                }
            }
        }
    }

    gambarDilihat?.let { url ->
        Dialog(onDismissRequest = { gambarDilihat = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
                AsyncImage(
                    model = url,
                    contentDescription = "Foto dalam percakapan",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                )
                IconButton(
                    onClick = { gambarDilihat = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) { Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White) }
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Model tampilan
 * ----------------------------------------------------------------------- */

sealed interface BarisRuang {
    val kunci: String

    data class Pemisah(override val kunci: String, val label: String) : BarisRuang

    data class Balon(
        override val kunci: String,
        val localId: String?,
        val milikSaya: Boolean,
        val teks: String?,
        val mediaUrl: String?,
        val mediaUri: android.net.Uri?,
        val waktu: Long?,
        val status: StatusKirim,
    ) : BarisRuang
}

/**
 * Menyusun daftar tampilan: terbaru lebih dulu (LazyColumn-nya reverseLayout).
 *
 * Pemisah tanggal disisipkan SESUDAH gelembung terakhir hari itu di dalam
 * daftar, karena dengan reverseLayout indeks yang lebih besar digambar lebih ke
 * atas — jadi posisi itulah yang secara visual berada di atas pesan-pesan
 * harinya.
 */
private fun susunBaris(
    messages: List<com.jepretaja.app.data.model.MessageModel>,
    tertunda: List<PesanTertunda>,
    myId: String?,
): List<BarisRuang> {
    val balon = mutableListOf<BarisRuang.Balon>()

    // Yang belum terkirim selalu paling baru.
    tertunda.sortedByDescending { it.waktu }.forEach { p ->
        balon += BarisRuang.Balon(
            kunci = "tertunda_${p.localId}",
            localId = p.localId,
            milikSaya = true,
            teks = p.teks,
            mediaUrl = null,
            mediaUri = p.gambarUri,
            waktu = p.waktu,
            status = if (p.gagal) StatusKirim.GAGAL else StatusKirim.MENGIRIM,
        )
    }

    messages.forEach { m ->
        balon += BarisRuang.Balon(
            kunci = "pesan_${m.messageId}",
            localId = null,
            milikSaya = m.senderId == myId,
            teks = m.text,
            mediaUrl = m.mediaUrl?.takeIf { m.type == "image" },
            mediaUri = null,
            waktu = m.createdAt?.toDate()?.time,
            status = if (m.readAt != null) StatusKirim.TERBACA else StatusKirim.TERKIRIM,
        )
    }

    val hasil = mutableListOf<BarisRuang>()
    balon.forEachIndexed { i, b ->
        hasil += b
        val hariIni = hariDari(b.waktu)
        val berikut = balon.getOrNull(i + 1)
        // Pesan tanpa createdAt (server timestamp yang belum di-ack) dianggap
        // hari ini; menaruhnya di bawah pemisah tanggal lain akan berbohong.
        if (berikut == null || hariDari(berikut.waktu) != hariIni) {
            hasil += BarisRuang.Pemisah(kunci = "pemisah_${hariIni}_${b.kunci}", label = labelHari(b.waktu))
        }
    }
    return hasil
}

private fun hariDari(millis: Long?): String {
    val kal = Calendar.getInstance().apply { timeInMillis = millis ?: System.currentTimeMillis() }
    return "${kal.get(Calendar.YEAR)}-${kal.get(Calendar.DAY_OF_YEAR)}"
}

private fun labelHari(millis: Long?): String {
    val waktu = Date(millis ?: System.currentTimeMillis())
    val itu = Calendar.getInstance().apply { time = waktu }
    val sekarang = Calendar.getInstance()
    val kemarin = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    fun sama(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    return when {
        sama(itu, sekarang) -> "Hari ini"
        sama(itu, kemarin) -> "Kemarin"
        else -> SimpleDateFormat("d MMMM yyyy", LOKAL_ID).format(waktu)
    }
}

internal fun keteranganAktif(millis: Long?): String {
    val t = millis ?: return "Belum pernah aktif"
    val menit = (System.currentTimeMillis() - t) / 60000
    return when {
        menit < 2 -> "Online"
        menit < 60 -> "Aktif $menit menit lalu"
        menit < 60 * 24 -> "Aktif ${menit / 60} jam lalu"
        else -> "Aktif ${menit / 1440} hari lalu"
    }
}

/* --------------------------------------------------------------------------
 * Potongan tampilan
 * ----------------------------------------------------------------------- */

@Composable
private fun PemisahTanggal(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = AppColors.Border)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = AppColors.TextSecondary,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(AppColors.SurfaceVariant)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        HorizontalDivider(Modifier.weight(1f), color = AppColors.Border)
    }
}

@Composable
private fun GelembungPesan(
    balon: BarisRuang.Balon,
    onKirimUlang: () -> Unit,
    onBatal: () -> Unit,
    onLihatGambar: (String) -> Unit,
) {
    val isMe = balon.milikSaya
    val adaGambar = balon.mediaUrl != null || balon.mediaUri != null
    val gagal = balon.status == StatusKirim.GAGAL

    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
    ) {
        // Sudut "ekor" di sisi pengirim sengaja lebih kecil supaya arah pesan
        // terbaca sekilas tanpa bergantung pada warna saja.
        val bentuk = RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (isMe) 18.dp else 4.dp,
            bottomEnd = if (isMe) 4.dp else 18.dp,
        )
        Box(
            Modifier
                .widthIn(max = 280.dp)
                .premiumShadow(2.dp, bentuk)
                .clip(bentuk)
                .background(if (isMe) AppColors.Primary else AppColors.Surface)
                // Pesan yang gagal diberi tepi merah, bukan sekadar ikon kecil:
                // dalam daftar panjang, gelembungnya sendiri yang harus menonjol.
                .then(if (gagal) Modifier.border(1.5.dp, AppColors.Danger, bentuk) else Modifier)
                .padding(if (adaGambar) 4.dp else 0.dp),
        ) {
            when {
                balon.mediaUrl != null -> AsyncImage(
                    model = balon.mediaUrl,
                    contentDescription = "Foto dalam percakapan",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(180.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onLihatGambar(balon.mediaUrl) },
                )
                balon.mediaUri != null -> Box {
                    AsyncImage(
                        model = balon.mediaUri,
                        contentDescription = "Foto sedang dikirim",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(180.dp).clip(MaterialTheme.shapes.small),
                    )
                    if (balon.status == StatusKirim.MENGIRIM) {
                        Box(
                            Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(26.dp), strokeWidth = 2.dp, color = Color.White,
                            )
                        }
                    }
                }
                else -> Text(
                    balon.teks.orEmpty(),
                    color = if (isMe) AppColors.OnPrimary else AppColors.TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(3.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            balon.waktu?.let {
                Text(
                    JAM.format(Date(it)),
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary,
                )
            }
            if (isMe) {
                Spacer(Modifier.width(4.dp))
                PenandaStatus(balon.status)
            }
        }

        // Coba lagi ditaruh langsung di bawah pesannya, bukan sebagai snackbar
        // yang lewat: pesan gagal tetap gagal setelah snackbar hilang, dan
        // pengguna butuh tahu YANG MANA yang harus dikirim ulang.
        if (gagal) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Gagal terkirim",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.Danger,
                )
                TextButton(onClick = onKirimUlang, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Coba lagi", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = onBatal, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Hapus", style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun PenandaStatus(status: StatusKirim) {
    when (status) {
        StatusKirim.MENGIRIM -> Icon(
            Icons.Default.Schedule, contentDescription = "Sedang dikirim",
            tint = AppColors.TextSecondary, modifier = Modifier.size(13.dp),
        )
        StatusKirim.TERKIRIM -> Icon(
            Icons.Default.Check, contentDescription = "Terkirim",
            tint = AppColors.TextSecondary, modifier = Modifier.size(13.dp),
        )
        StatusKirim.TERBACA -> Icon(
            Icons.Default.DoneAll, contentDescription = "Sudah dibaca",
            tint = AppColors.Info, modifier = Modifier.size(13.dp),
        )
        StatusKirim.GAGAL -> Icon(
            Icons.Default.ErrorOutline, contentDescription = "Gagal terkirim",
            tint = AppColors.Danger, modifier = Modifier.size(13.dp),
        )
    }
}

/**
 * Pratinjau foto sebelum dikirim.
 *
 * Sebelumnya memilih foto dari galeri langsung mengunggahnya. Satu ketukan
 * keliru di galeri berarti foto itu sudah ada di percakapan orang lain, dan
 * tidak ada cara menariknya kembali.
 */
@Composable
private fun PratinjauSebelumKirim(
    uri: android.net.Uri,
    onBatal: () -> Unit,
    onKirim: () -> Unit,
) {
    Surface(color = AppColors.Surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Pratinjau foto yang akan dikirim",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Kirim foto ini?", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                Text(
                    "Foto yang sudah terkirim tidak bisa ditarik kembali.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextSecondary,
                )
            }
            TextButton(onClick = onBatal) { Text("Batal") }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onKirim,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    contentColor = AppColors.OnPrimary,
                ),
            ) { Text("Kirim") }
        }
    }
}
