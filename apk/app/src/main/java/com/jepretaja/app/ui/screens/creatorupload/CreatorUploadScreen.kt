package com.jepretaja.app.ui.screens.creatorupload

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.AppConstants
import com.jepretaja.app.core.util.MediaInfo
import com.jepretaja.app.core.util.MediaTerpilih
import com.jepretaja.app.core.util.VideoTools
import com.jepretaja.app.data.work.UploadQueue
import com.jepretaja.app.ui.components.AppAvatar
import com.jepretaja.app.ui.components.BigPrimaryButton
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.VideoPlayer
import com.jepretaja.app.ui.components.premiumShadow
import com.jepretaja.app.ui.screens.camera.CameraCaptureScreen
import com.jepretaja.app.ui.state.AuthViewModel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val MAKS_FOTO = 10

/**
 * Unggah karya: pilih atau rekam media, atur, lalu titipkan ke antrean.
 *
 * Perbedaan mendasar dari versi sebelumnya: layar ini tidak lagi menjalankan
 * unggahannya sendiri. Ia menyiapkan isi lalu menyerahkannya ke WorkManager,
 * sehingga menutup layar — atau berpindah aplikasi — tidak lagi membatalkan
 * unggahan yang sedang berjalan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorUploadScreen(
    onBack: () -> Unit,
    onUploaded: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorUploadViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val error by viewModel.error.collectAsState()
    val drafts by viewModel.drafts.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var mediaUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isVideo by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(AppConstants.SERVICE_CATEGORIES.first()) }
    var target by remember { mutableStateOf("Explore") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var mentions by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var saranCreator by remember { mutableStateOf<List<com.jepretaja.app.data.model.CreatorModel>>(emptyList()) }
    var lokasi by remember { mutableStateOf("") }
    var kebijakanKomentar by remember { mutableStateOf("all") }
    var bolehSimpan by remember { mutableStateOf(true) }
    var infoMedia by remember { mutableStateOf(MediaTerpilih()) }
    var alasanTolak by remember { mutableStateOf<String?>(null) }
    var draftAktif by remember { mutableStateOf<String?>(null) }

    var bukaKamera by remember { mutableStateOf(false) }
    var bukaDraft by remember { mutableStateOf(false) }
    var bukaJadwal by remember { mutableStateOf(false) }
    var jadwalMillis by remember { mutableStateOf<Long?>(null) }
    var paketDipilih by remember { mutableStateOf<com.jepretaja.app.data.model.PackageModel?>(null) }
    var menuPaket by remember { mutableStateOf(false) }
    val paketSaya by remember(authState.uid) {
        authState.uid?.let { viewModel.paketSaya(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Potong & sampul (khusus video)
    var potongMulaiMs by remember { mutableStateOf(0f) }
    var potongSelesaiMs by remember { mutableStateOf(0f) }
    var sampulPosisiMs by remember { mutableStateOf(0f) }
    var sampulPratinjau by remember { mutableStateOf<Uri?>(null) }
    var sedangMenyiapkan by remember { mutableStateOf(false) }

    val antrean by remember { UploadQueue.stream(context) }.collectAsState(initial = emptyList())

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { daftar ->
        if (daftar.isNotEmpty()) {
            mediaUris = daftar.take(MAKS_FOTO)
            isVideo = false
        }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { mediaUris = listOf(uri); isVideo = true; target = "Explore" }
    }

    // Media diperiksa begitu dipilih, bukan saat tombol kirim ditekan: creator
    // di jaringan seluler tidak perlu menghabiskan kuota untuk berkas yang
    // sudah pasti ditolak.
    LaunchedEffect(mediaUris, isVideo) {
        val utama = mediaUris.firstOrNull()
        if (utama == null) {
            infoMedia = MediaTerpilih(); alasanTolak = null
        } else {
            infoMedia = MediaInfo.baca(context, utama, isVideo)
            alasanTolak = MediaInfo.alasanDitolak(infoMedia, isVideo)
            if (isVideo) {
                val durasiMs = (infoMedia.durationSeconds ?: 0L) * 1000f
                potongMulaiMs = 0f
                potongSelesaiMs = durasiMs
                sampulPosisiMs = 0f
            }
        }
    }

    // Sampul mengikuti penggeser, dengan jeda kecil supaya menggeser cepat tidak
    // memicu puluhan pembacaan frame sekaligus.
    LaunchedEffect(sampulPosisiMs, mediaUris, isVideo) {
        val utama = mediaUris.firstOrNull()
        if (utama != null && isVideo) {
            delay(250)
            sampulPratinjau = VideoTools.frameAt(context, utama, sampulPosisiMs.toLong())
        } else {
            sampulPratinjau = null
        }
    }

    val tokenSebutan = remember(caption) {
        caption.substringAfterLast(' ').substringAfterLast('\n').takeIf { it.startsWith("@") }?.drop(1)
    }
    LaunchedEffect(tokenSebutan) {
        val kunci = tokenSebutan
        if (kunci != null && kunci.length >= 2) viewModel.cariCreator(kunci) { saranCreator = it }
        else saranCreator = emptyList()
    }

    LaunchedEffect(message) {
        if (message != null) {
            onUploaded()
            viewModel.clearMessage()
        }
    }

    // Kegagalan menyiapkan media TIDAK menutup layar: creator tetap di sini
    // dengan pilihan medianya utuh, jadi ia bisa langsung mencoba lagi.
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (bukaKamera) {
        CameraCaptureScreen(
            onCaptured = { uri, video ->
                mediaUris = listOf(uri)
                isVideo = video
                if (video) target = "Explore"
                bukaKamera = false
            },
            onClose = { bukaKamera = false },
        )
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
        TopAppBar(
            title = { Text("Upload Konten", style = MaterialTheme.typography.headlineSmall) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali") }
            },
            actions = {
                TextButton(onClick = { viewModel.refreshDrafts(); bukaDraft = true }) {
                    Text(if (drafts.isEmpty()) "Draft" else "Draft (${drafts.size})")
                }
            },
        )
    }) { padding ->
        // Hanya creator yang boleh mengunggah. Penjagaan ditaruh di layarnya
        // sendiri supaya jalur masuk lain (deep link, back stack) tidak lolos.
        if (!authState.isCreator) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Lock,
                    title = "Khusus Creator",
                    description = "Hanya akun creator yang bisa mengunggah karya ke Explore. " +
                        "Daftar sebagai creator untuk mulai memamerkan karyamu.",
                )
            }
            return@Scaffold
        }

        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState())) {

            if (antrean.isNotEmpty()) {
                AntreanSection(antrean, onBatal = { UploadQueue.batal(context, it) })
                Spacer(Modifier.height(18.dp))
            }

            Box(
                Modifier.fillMaxWidth().height(220.dp)
                    .premiumShadow(6.dp, MaterialTheme.shapes.large)
                    .clip(MaterialTheme.shapes.large)
                    .background(AppColors.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val utama = mediaUris.firstOrNull()
                when {
                    utama != null && isVideo -> VideoPlayer(
                        url = utama.toString(),
                        playWhenActive = true,
                        muted = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                    utama != null -> AsyncImage(
                        model = utama, contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                    )
                    else -> Text("Belum ada media dipilih", color = AppColors.TextSecondary)
                }
            }

            // Beberapa foto dalam satu post: mediaUrls memang sudah berupa List
            // sejak awal, tapi UI-nya dulu hanya menerima satu berkas.
            if (!isVideo && mediaUris.size > 1) {
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(mediaUris) { uri ->
                        Box {
                            AsyncImage(
                                model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                            )
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(2.dp)
                                    .background(AppColors.Danger, RoundedCornerShape(percent = 50))
                                    .clickable { mediaUris = mediaUris - uri },
                            ) {
                                Icon(
                                    Icons.Default.Close, contentDescription = "Hapus",
                                    tint = AppColors.OnPrimary, modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    "${mediaUris.size} foto — yang pertama jadi sampul.",
                    style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row {
                OutlinedButton(
                    onClick = { pickImages.launch("image/*") },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Icon(Icons.Default.Image, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Foto") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = { pickVideo.launch("video/*") },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Icon(Icons.Default.Videocam, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Video") }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { bukaKamera = true },
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Buka Kamera")
            }

            if (mediaUris.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                val ringkasan = buildList {
                    infoMedia.durationSeconds?.let { add("${it / 60}m ${it % 60}d") }
                    infoMedia.sizeBytes?.let { add("${it / 1024 / 1024} MB") }
                    if (infoMedia.width != null && infoMedia.height != null) add("${infoMedia.width}x${infoMedia.height}")
                }.joinToString(" | ")
                if (ringkasan.isNotBlank()) {
                    Text(ringkasan, style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
                }
                alasanTolak?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.Danger)
                }
            }

            if (isVideo && (infoMedia.durationSeconds ?: 0L) > 0) {
                val durasiMs = (infoMedia.durationSeconds ?: 0L) * 1000f
                Spacer(Modifier.height(18.dp))
                Text("Potong video", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                RangeSlider(
                    value = potongMulaiMs..potongSelesaiMs,
                    onValueChange = { potongMulaiMs = it.start; potongSelesaiMs = it.endInclusive },
                    valueRange = 0f..durasiMs,
                )
                Text(
                    "Dari ${detik(potongMulaiMs)} sampai ${detik(potongSelesaiMs)} " +
                        "(${detik(potongSelesaiMs - potongMulaiMs)} terpakai)",
                    style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                )

                Spacer(Modifier.height(14.dp))
                Text("Sampul video", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = sampulPratinjau,
                        contentDescription = "Pratinjau sampul",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 64.dp, height = 84.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .border(1.dp, AppColors.Border, MaterialTheme.shapes.medium),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Slider(
                            value = sampulPosisiMs,
                            onValueChange = { sampulPosisiMs = it },
                            valueRange = potongMulaiMs..potongSelesaiMs.coerceAtLeast(potongMulaiMs + 1f),
                        )
                        Text(
                            "Geser untuk memilih frame sampul (${detik(sampulPosisiMs)})",
                            style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                caption, { caption = it },
                label = { Text("Caption") },
                supportingText = { Text("Pakai #tagar supaya karyamu muncul di halaman tagar, dan @ untuk menyebut creator lain.") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (saranCreator.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth()) {
                    saranCreator.forEach { c ->
                        ListItem(
                            headlineContent = { Text(c.displayName) },
                            leadingContent = { AppAvatar(url = c.photoUrl, name = c.displayName, size = 32.dp) },
                            colors = ListItemDefaults.colors(containerColor = AppColors.SurfaceVariant),
                            modifier = Modifier.clickable {
                                caption = caption.substringBeforeLast("@") + "@" + c.displayName + " "
                                mentions = (mentions + mapOf("name" to c.displayName, "creatorId" to c.creatorId))
                                    .distinctBy { it["creatorId"] }
                                saranCreator = emptyList()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            ExposedDropdownMenuBox(expanded = categoryMenuExpanded, onExpandedChange = { categoryMenuExpanded = it }) {
                OutlinedTextField(
                    value = category, onValueChange = {}, readOnly = true, label = { Text("Kategori") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                    AppConstants.SERVICE_CATEGORIES.forEach { c ->
                        DropdownMenuItem(text = { Text(c) }, onClick = { category = c; categoryMenuExpanded = false })
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SingleChoiceSegmentedButtonRow {
                listOf("Explore", "Portfolio").forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = target == t,
                        onClick = { if (!(isVideo && t == "Portfolio")) target = t },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                        enabled = !(isVideo && t == "Portfolio"),
                    ) { Text(t) }
                }
            }
            if (isVideo) {
                Spacer(Modifier.height(6.dp))
                Text("Video hanya bisa diunggah ke Explore.", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                lokasi, { lokasi = it },
                label = { Text("Lokasi / venue (opsional)") },
                placeholder = { Text("Contoh: Hotel Tentrem, Yogyakarta") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Menautkan paket ke karya: orang yang terpikat sebuah foto biasanya
            // ingin memesan hal yang persis sama, dan tanpa tautan ini mereka
            // harus menebak sendiri paket mana yang menghasilkannya.
            if (paketSaya.isNotEmpty() && target == "Explore") {
                Spacer(Modifier.height(14.dp))
                ExposedDropdownMenuBox(expanded = menuPaket, onExpandedChange = { menuPaket = it }) {
                    OutlinedTextField(
                        value = paketDipilih?.name ?: "Tidak ditautkan",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Paket yang dipakai (opsional)") },
                        supportingText = { Text("Muncul di kartu karya sebagai tombol booking langsung.") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = menuPaket, onDismissRequest = { menuPaket = false }) {
                        DropdownMenuItem(
                            text = { Text("Tidak ditautkan") },
                            onClick = { paketDipilih = null; menuPaket = false },
                        )
                        paketSaya.forEach { p ->
                            DropdownMenuItem(
                                text = { Text("${p.name} — ${com.jepretaja.app.core.util.Formatters.currency(p.price)}") },
                                onClick = { paketDipilih = p; menuPaket = false },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Pengaturan Post", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text("Siapa yang boleh komentar", style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("all" to "Semua", "followers" to "Pengikut", "off" to "Nonaktif")
                    .forEachIndexed { i, (nilai, label) ->
                        SegmentedButton(
                            selected = kebijakanKomentar == nilai,
                            onClick = { kebijakanKomentar = nilai },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 3),
                        ) { Text(label) }
                    }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Izinkan orang menyimpan karya ini", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
                    Text(
                        "Kalau dimatikan, tombol simpan disembunyikan dan ditolak juga di sisi server.",
                        style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                    )
                }
                Switch(checked = bolehSimpan, onCheckedChange = { bolehSimpan = it })
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Jadwalkan tayang", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextPrimary)
                    Text(
                        jadwalMillis?.let { "Diunggah otomatis ${formatWaktu(it)}" }
                            ?: "Kirim sekarang juga",
                        style = MaterialTheme.typography.bodySmall, color = AppColors.TextSecondary,
                    )
                }
                Switch(
                    checked = jadwalMillis != null,
                    onCheckedChange = { aktif -> if (aktif) bukaJadwal = true else jadwalMillis = null },
                )
            }

            Spacer(Modifier.height(28.dp))
            BigPrimaryButton(
                text = when {
                    sedangMenyiapkan -> "Menyiapkan video..."
                    jadwalMillis != null -> "Jadwalkan"
                    else -> "Upload"
                },
                loading = sedangMenyiapkan,
                enabled = mediaUris.isNotEmpty() && alasanTolak == null && !sedangMenyiapkan,
                onClick = { sedangMenyiapkan = true },
            )

            // Pemotongan video dijalankan di sini, terpisah dari onClick, karena
            // meng-encode ulang butuh coroutine dan bisa memakan puluhan detik.
            if (sedangMenyiapkan) {
                LaunchedEffect(Unit) {
                    val uid = authState.uid
                    val utama = mediaUris.firstOrNull()
                    if (uid == null || utama == null) {
                        sedangMenyiapkan = false
                        return@LaunchedEffect
                    }
                    val durasiMs = (infoMedia.durationSeconds ?: 0L) * 1000f
                    val dipotong = isVideo && (potongMulaiMs > 0f || potongSelesaiMs < durasiMs)
                    val berkas = if (dipotong) {
                        VideoTools.trim(context, utama, potongMulaiMs.toLong(), potongSelesaiMs.toLong()) ?: utama
                    } else {
                        utama
                    }
                    val durasiAkhir = if (dipotong) {
                        ((potongSelesaiMs - potongMulaiMs) / 1000).toLong()
                    } else {
                        infoMedia.durationSeconds
                    }

                    viewModel.kirim(
                        context = context,
                        creatorId = uid,
                        creatorName = authState.profile?.name ?: "",
                        uris = if (isVideo) listOf(berkas) else mediaUris,
                        isVideo = isVideo,
                        caption = caption.trim(),
                        category = category,
                        target = target,
                        mentions = mentions,
                        coverUri = sampulPratinjau,
                        pengaturan = PengaturanPost(
                            location = lokasi.trim().takeIf { it.isNotBlank() },
                            commentPolicy = kebijakanKomentar,
                            allowSave = bolehSimpan,
                            durationSeconds = durasiAkhir,
                            scheduledAt = jadwalMillis,
                            packageId = paketDipilih?.packageId,
                            packageName = paketDipilih?.name,
                            packagePrice = paketDipilih?.price,
                        ),
                    )
                    draftAktif?.let { viewModel.hapusDraft(it) }
                    sedangMenyiapkan = false
                }
            }

            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = {
                    viewModel.simpanDraft(draftAktif, caption, category, target, mediaUris, isVideo)
                    onBack()
                },
                enabled = caption.isNotBlank() || mediaUris.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Simpan sebagai draft") }
            Spacer(Modifier.height(20.dp))
        }

        if (bukaDraft) {
            ModalBottomSheet(onDismissRequest = { bukaDraft = false }, containerColor = AppColors.Surface) {
                Text(
                    "Draft",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                )
                if (drafts.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada draft tersimpan.", color = AppColors.TextSecondary)
                    }
                } else {
                    drafts.forEach { d ->
                        ListItem(
                            leadingContent = {
                                AsyncImage(
                                    model = d.mediaUris.firstOrNull(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                                        .background(AppColors.SurfaceVariant),
                                )
                            },
                            headlineContent = {
                                Text(d.caption.ifBlank { "(tanpa caption)" }, maxLines = 1)
                            },
                            supportingContent = {
                                Text("${d.category} — ${formatWaktu(d.savedAt)}", style = MaterialTheme.typography.bodySmall)
                            },
                            trailingContent = {
                                TextButton(onClick = { viewModel.hapusDraft(d.id) }) { Text("Hapus", color = AppColors.Danger) }
                            },
                            colors = ListItemDefaults.colors(containerColor = AppColors.Surface),
                            modifier = Modifier.clickable {
                                caption = d.caption
                                category = d.category.ifBlank { category }
                                target = d.target
                                isVideo = d.isVideo
                                mediaUris = d.mediaUris.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
                                draftAktif = d.id
                                bukaDraft = false
                            },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }

        if (bukaJadwal) {
            PemilihJadwal(
                onDismiss = { bukaJadwal = false },
                onPilih = { millis -> jadwalMillis = millis; bukaJadwal = false },
            )
        }
    }
}

/** Antrean unggahan yang sedang berjalan, menunggu jaringan, atau gagal. */
@Composable
private fun AntreanSection(antrean: List<com.jepretaja.app.data.work.AntreanUnggah>, onBatal: (java.util.UUID) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Antrean unggah", style = MaterialTheme.typography.titleSmall, color = AppColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        antrean.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (item.state) {
                            WorkInfo.State.ENQUEUED -> "Menunggu giliran / jaringan"
                            WorkInfo.State.RUNNING -> "Mengunggah ${(item.progress * 100).toInt()}%"
                            WorkInfo.State.SUCCEEDED -> "Terkirim, menunggu moderasi"
                            WorkInfo.State.FAILED -> item.error ?: "Gagal"
                            WorkInfo.State.BLOCKED -> "Tertunda"
                            WorkInfo.State.CANCELLED -> "Dibatalkan"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.state == WorkInfo.State.FAILED) AppColors.Danger else AppColors.TextSecondary,
                    )
                    if (item.state == WorkInfo.State.RUNNING) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { item.progress },
                            color = AppColors.Primary,
                            trackColor = AppColors.SurfaceVariant,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                        )
                    }
                }
                if (item.state == WorkInfo.State.ENQUEUED || item.state == WorkInfo.State.RUNNING) {
                    TextButton(onClick = { onBatal(item.id) }) { Text("Batal") }
                }
            }
        }
    }
}

/** Pemilih tanggal lalu jam untuk penjadwalan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PemilihJadwal(onDismiss: () -> Unit, onPilih: (Long) -> Unit) {
    var tanggalMillis by remember { mutableStateOf<Long?>(null) }
    val stateTanggal = rememberDatePickerState()
    val stateJam = rememberTimePickerState(is24Hour = true)

    if (tanggalMillis == null) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { tanggalMillis = stateTanggal.selectedDateMillis }) { Text("Lanjut") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        ) { DatePicker(state = stateTanggal) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Jam tayang") },
            text = { TimePicker(state = stateJam) },
            confirmButton = {
                TextButton(onClick = {
                    val kalender = Calendar.getInstance().apply {
                        timeInMillis = tanggalMillis!!
                        set(Calendar.HOUR_OF_DAY, stateJam.hour)
                        set(Calendar.MINUTE, stateJam.minute)
                        set(Calendar.SECOND, 0)
                    }
                    onPilih(kalender.timeInMillis)
                }) { Text("Jadwalkan") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        )
    }
}

private fun detik(ms: Float): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

private fun formatWaktu(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale("in", "ID")).format(Date(millis))
