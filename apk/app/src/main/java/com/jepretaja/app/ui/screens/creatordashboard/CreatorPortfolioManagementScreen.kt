package com.jepretaja.app.ui.screens.creatordashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.AppConstants
import com.jepretaja.app.data.model.PortfolioModel
import com.jepretaja.app.data.model.PortfolioStatus
import com.jepretaja.app.ui.components.AppTopBar
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.premiumShadow
import com.jepretaja.app.ui.components.rememberAppTopBarScrollBehavior
import com.jepretaja.app.ui.state.AuthViewModel

/** Saringan status di atas grid. */
private enum class SaringStatus(val judul: String, val status: String?) {
    SEMUA("Semua", null),
    DRAFT("Draft", PortfolioStatus.DRAFT),
    MENUNGGU("Menunggu Review", PortfolioStatus.MENUNGGU),
    DISETUJUI("Disetujui", PortfolioStatus.DISETUJUI),
    DITOLAK("Ditolak", PortfolioStatus.DITOLAK),
}

private data class TampilanStatus(val label: String, val ikon: ImageVector, val warna: Color)

@Composable
private fun tampilanStatus(status: String): TampilanStatus = when (status) {
    PortfolioStatus.DRAFT -> TampilanStatus("Draft", Icons.Default.EditNote, AppColors.TextSecondary)
    PortfolioStatus.MENUNGGU -> TampilanStatus("Menunggu Review", Icons.Default.HourglassTop, AppColors.Warning)
    PortfolioStatus.DITOLAK -> TampilanStatus("Ditolak", Icons.Default.Cancel, AppColors.Danger)
    PortfolioStatus.DISEMBUNYIKAN -> TampilanStatus("Disembunyikan", Icons.Default.VisibilityOff, AppColors.Danger)
    else -> TampilanStatus("Disetujui", Icons.Default.CheckCircle, AppColors.Success)
}

/**
 * Kelola Portfolio.
 *
 * **Yang berubah.** Versi sebelumnya adalah grid tiga kolom berisi foto tunggal
 * tanpa satu pun keterangan: tidak ada cara membuka gambarnya besar, tidak ada
 * penanda video, tidak ada urutan yang bisa diatur, dan status setiap album
 * selalu `active` — artinya apa pun yang diunggah langsung tayang di profil
 * publik, termasuk foto yang baru separuh diunggah dari sesi pemotretan yang
 * belum selesai.
 *
 * Sekarang: unggah banyak berkas sekaligus menjadi satu album, pratinjau layar
 * penuh, urutan yang bisa digeser dengan tekan-tahan, lencana status di tiap
 * kartu, dan penanda video.
 *
 * **Soal status.** Empat tahap — Draft, Menunggu Review, Disetujui, Ditolak —
 * dijaga di sisi klien lewat penyaringan di [com.jepretaja.app.data.repository.CreatorRepository.streamPortfolio],
 * jadi hanya album Disetujui yang bisa terlihat orang lain. `active` dan
 * `hidden` dipertahankan sebagai nama status Disetujui dan Disembunyikan karena
 * itulah kosakata yang sudah dipakai panel moderasi admin; album lama tetap
 * terbaca tanpa migrasi apa pun.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorPortfolioManagementScreen(
    onBack: () -> Unit,
    authViewModel: AuthViewModel,
    viewModel: CreatorPortfolioManagementViewModel = hiltViewModel(),
) {
    val authState by authViewModel.uiState.collectAsState()
    val uid = authState.uid

    var editing by remember { mutableStateOf<PortfolioModel?>(null) }
    var deleting by remember { mutableStateOf<PortfolioModel?>(null) }
    var pratinjau by remember { mutableStateOf<PortfolioModel?>(null) }
    var saring by remember { mutableStateOf(SaringStatus.SEMUA) }

    val progres by viewModel.progres.collectAsState()
    val scrollBehavior = rememberAppTopBarScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    val pesan by viewModel.pesan.collectAsState()

    LaunchedEffect(pesan) {
        pesan?.let { snackbarHostState.showSnackbar(it); viewModel.pesanDibaca() }
    }

    // Semua album, termasuk draft dan yang ditolak. Layar kelola justru satu-
    // satunya tempat yang HARUS melihat semuanya.
    val semua by remember(uid) {
        if (uid != null) viewModel.albumSaya(uid) else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // Salinan lokal supaya penggeseran terasa seketika. Disegarkan dari Firestore
    // hanya ketika tidak ada yang sedang diseret — kalau tidak, kartu akan
    // melompat kembali ke tempat semula di tengah jari pengguna.
    var urutan by remember { mutableStateOf<List<PortfolioModel>>(emptyList()) }
    val gridState = rememberLazyGridState()
    val reorder = rememberPenggeser(gridState) { dari, ke ->
        urutan = urutan.toMutableList().apply { add(ke, removeAt(dari)) }
    }
    LaunchedEffect(semua, reorder.indexDiseret) {
        if (reorder.indexDiseret == null) urutan = semua
    }

    // Multi-pick: satu pemilihan menghasilkan satu album, bukan sepuluh album
    // terpisah tanpa judul.
    val pilihMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty() && uid != null) {
            viewModel.unggahAlbum(uid, uris, urutanBerikutnya = (urutan.size).toLong())
        }
    }

    val tersaring = remember(urutan, saring) {
        urutan.filter { saring.status == null || it.status == saring.status }
    }
    val bolehGeser = saring == SaringStatus.SEMUA

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = AppColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Kelola Portfolio",
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = { pilihMedia.launch("*/*") },
                        enabled = !progres.sedangJalan,
                    ) { Icon(Icons.Default.Add, contentDescription = "Tambah album") }
                },
            )
        },
    ) { padding ->
        if (uid == null) return@Scaffold

        Column(Modifier.padding(padding).fillMaxSize()) {

            if (progres.sedangJalan) {
                // Kemajuan per berkas, bukan sekadar lingkaran berputar: pada
                // unggahan dua belas foto, indikator tanpa angka tidak bisa
                // dibedakan dari aplikasi yang menggantung.
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        "Mengunggah ${progres.selesai + 1} dari ${progres.total}...",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppColors.TextSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { if (progres.total == 0) 0f else progres.selesai.toFloat() / progres.total },
                        color = AppColors.Primary,
                        trackColor = AppColors.SurfaceVariant,
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(percent = 50)),
                    )
                }
            }

            // Saringan status ditaruh DI LUAR grid supaya indeks item grid sama
            // persis dengan indeks daftar — perhitungan geser bergantung padanya.
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SaringStatus.entries) { s ->
                    val jumlah = if (s.status == null) urutan.size else urutan.count { it.status == s.status }
                    FilterChip(
                        selected = saring == s,
                        onClick = { saring = s },
                        label = { Text(if (jumlah > 0) "${s.judul} · $jumlah" else s.judul) },
                        shape = RoundedCornerShape(percent = 50),
                    )
                }
            }

            if (bolehGeser && tersaring.size > 1) {
                Text(
                    "Tekan lama sebuah album untuk menggeser urutannya.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }

            if (tersaring.isEmpty()) {
                EmptyState(
                    title = if (saring == SaringStatus.SEMUA) "Belum ada portfolio" else "Tidak ada album ${saring.judul.lowercase()}",
                    description = if (saring == SaringStatus.SEMUA) {
                        "Tambahkan karya terbaikmu lewat tombol + di pojok kanan atas. Bisa pilih beberapa berkas sekaligus."
                    } else {
                        "Ganti saringan di atas untuk melihat album lainnya."
                    },
                    icon = Icons.Default.PhotoLibrary,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (bolehGeser) {
                                Modifier.pointerInput(tersaring.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { posisi -> reorder.mulai(posisi) },
                                        onDrag = { perubahan, jarak ->
                                            perubahan.consume()
                                            reorder.seret(jarak)
                                        },
                                        onDragEnd = {
                                            reorder.selesai()
                                            viewModel.simpanUrutan(urutan.map { it.portfolioId })
                                        },
                                        onDragCancel = { reorder.selesai() },
                                    )
                                }
                            } else {
                                Modifier
                            }
                        ),
                ) {
                    itemsIndexed(tersaring, key = { _, item -> item.portfolioId }) { indeks, item ->
                        val diseret = reorder.indexDiseret == indeks
                        KartuAlbum(
                            item = item,
                            bolehGeser = bolehGeser,
                            modifier = Modifier
                                .zIndex(if (diseret) 1f else 0f)
                                .graphicsLayer {
                                    if (diseret) {
                                        translationX = reorder.geser.x
                                        translationY = reorder.geser.y
                                        scaleX = 1.04f
                                        scaleY = 1.04f
                                    }
                                },
                            onBuka = { pratinjau = item },
                            onEdit = { editing = item },
                            onHapus = { deleting = item },
                            onAjukan = { viewModel.ajukanReview(item.portfolioId) },
                            onJadikanDraft = { viewModel.jadikanDraft(item.portfolioId) },
                        )
                    }
                }
            }
        }
    }

    pratinjau?.let { item ->
        PratinjauLayarPenuh(item = item, onTutup = { pratinjau = null })
    }

    editing?.let { item ->
        DialogEditAlbum(
            item = item,
            onBatal = { editing = null },
            onSimpan = { judul, kategori ->
                viewModel.updatePortfolioItem(item.portfolioId, judul, kategori)
                editing = null
            },
        )
    }

    // Menghapus karya tidak bisa dibatalkan — selalu lewat konfirmasi.
    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus album ini?") },
            text = {
                Text(
                    "${item.media.size} media di dalamnya ikut hilang dari portfolio " +
                        "dan tidak bisa dikembalikan.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePortfolioItem(item.portfolioId)
                    deleting = null
                }) { Text("Hapus", color = AppColors.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } },
        )
    }
}

/* --------------------------------------------------------------------------
 * Kartu album
 * ----------------------------------------------------------------------- */

@Composable
private fun KartuAlbum(
    item: PortfolioModel,
    bolehGeser: Boolean,
    modifier: Modifier = Modifier,
    onBuka: () -> Unit,
    onEdit: () -> Unit,
    onHapus: () -> Unit,
    onAjukan: () -> Unit,
    onJadikanDraft: () -> Unit,
) {
    val bentuk = RoundedCornerShape(16.dp)
    val t = tampilanStatus(item.status)
    val sampul = item.thumbnailUrl ?: item.media.firstOrNull()
    val video = item.type == "video"

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .premiumShadow(4.dp, bentuk)
                .clip(bentuk)
                .background(AppColors.SurfaceVariant)
                .clickable(onClick = onBuka),
        ) {
            if (sampul != null && !video) {
                AsyncImage(
                    model = sampul,
                    contentDescription = item.title.ifBlank { "Album portfolio" },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (sampul != null) {
                // Album video tanpa sampul: Coil tidak bisa menggambar berkas
                // video, jadi ditampilkan latar gelap berikon, bukan kotak abu
                // kosong yang terlihat seperti gambar rusak.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)))
            } else {
                Icon(
                    Icons.Default.Image, contentDescription = null,
                    tint = AppColors.TextSecondary, modifier = Modifier.align(Alignment.Center),
                )
            }

            if (video) {
                Icon(
                    Icons.Default.PlayCircle, contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                )
            }

            // Lencana status di kiri atas — hal pertama yang perlu diketahui
            // creator tentang sebuah album adalah apakah ia sudah tayang.
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.ikon, contentDescription = null, tint = t.warna, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(t.label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
            }

            Row(Modifier.align(Alignment.TopEnd).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (bolehGeser) {
                    Icon(
                        Icons.Default.DragIndicator, contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                TombolBulat(Icons.Default.Edit, "Edit album", onEdit)
                Spacer(Modifier.width(4.dp))
                TombolBulat(Icons.Default.Close, "Hapus album", onHapus)
            }

            if (item.media.size > 1) {
                Row(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Collections, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${item.media.size}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            item.title.ifBlank { "Tanpa judul" },
            style = MaterialTheme.typography.labelLarge,
            color = if (item.title.isBlank()) AppColors.TextSecondary else AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        item.category.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = AppColors.TextSecondary, maxLines = 1)
        }

        // Alasan penolakan ditampilkan di kartunya sendiri. Status "Ditolak"
        // tanpa sebab tidak memberi tahu apa yang harus diperbaiki, dan album
        // itu akan diajukan ulang apa adanya.
        if (item.status == PortfolioStatus.DITOLAK) {
            item.rejectedReason?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.Danger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        when (item.status) {
            PortfolioStatus.DRAFT, PortfolioStatus.DITOLAK -> TombolStatus(
                ikon = Icons.Default.Send,
                label = "Ajukan review",
                warna = AppColors.Primary,
                onClick = onAjukan,
            )
            PortfolioStatus.MENUNGGU -> TombolStatus(
                ikon = Icons.Default.EditNote,
                label = "Tarik pengajuan",
                warna = AppColors.TextSecondary,
                onClick = onJadikanDraft,
            )
            PortfolioStatus.DISETUJUI -> TombolStatus(
                ikon = Icons.Default.VisibilityOff,
                label = "Sembunyikan",
                warna = AppColors.TextSecondary,
                onClick = onJadikanDraft,
            )
            else -> Unit
        }
    }
}

@Composable
private fun TombolStatus(ikon: ImageVector, label: String, warna: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ikon, contentDescription = null, tint = warna, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = warna)
    }
}

@Composable
private fun TombolBulat(ikon: ImageVector, deskripsi: String, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.45f))) {
        IconButton(onClick = onClick, modifier = Modifier.size(26.dp)) {
            Icon(ikon, contentDescription = deskripsi, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/* --------------------------------------------------------------------------
 * Pratinjau layar penuh
 * ----------------------------------------------------------------------- */

/**
 * Pratinjau album.
 *
 * Sebelumnya tidak ada sama sekali: satu-satunya cara memeriksa foto yang sudah
 * diunggah adalah membuka profil publik sendiri. Untuk layar yang gunanya
 * memilih karya terbaik, tidak bisa melihat karyanya besar adalah kekurangan
 * yang cukup mendasar.
 */
@Composable
private fun PratinjauLayarPenuh(item: PortfolioModel, onTutup: () -> Unit) {
    var indeks by remember(item.portfolioId) { mutableIntStateOf(0) }
    val media = item.media
    val video = item.type == "video"

    Dialog(onDismissRequest = onTutup, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))) {
            if (video) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Default.PlayCircle, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    // Jujur soal batasnya daripada memutar apa pun: pemutar
                    // video di layar kelola belum ada, dan tombol putar yang
                    // tidak memutar lebih buruk daripada keterangan ini.
                    Text(
                        "Pratinjau video belum tersedia di layar kelola.\nVideo tetap tayang normal di profil publik.",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = media.getOrNull(indeks),
                    contentDescription = item.title.ifBlank { "Karya portfolio" },
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(vertical = 90.dp),
                )
            }

            IconButton(onClick = onTutup, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
            }

            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                if (media.size > 1 && !video) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(media.size) { i ->
                            AsyncImage(
                                model = media[i],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (i == indeks) 2.dp else 0.dp,
                                        color = if (i == indeks) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable { indeks = i },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                val t = tampilanStatus(item.status)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(t.ikon, contentDescription = null, tint = t.warna, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
                if (item.title.isNotBlank()) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                }
                if (item.category.isNotBlank()) {
                    Text(
                        item.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/* --------------------------------------------------------------------------
 * Dialog edit
 * ----------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogEditAlbum(
    item: PortfolioModel,
    onBatal: () -> Unit,
    onSimpan: (String, String) -> Unit,
) {
    var judul by remember(item.portfolioId) { mutableStateOf(item.title) }
    var kategori by remember(item.portfolioId) { mutableStateOf(item.category) }
    var menuTerbuka by remember(item.portfolioId) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onBatal,
        title = { Text("Edit Album") },
        text = {
            Column {
                OutlinedTextField(
                    judul, { judul = it },
                    label = { Text("Judul") },
                    supportingText = { Text("Judul membantu calon pelanggan mengenali jenis pemotretannya.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                ExposedDropdownMenuBox(expanded = menuTerbuka, onExpandedChange = { menuTerbuka = it }) {
                    OutlinedTextField(
                        value = kategori, onValueChange = {}, readOnly = true,
                        label = { Text("Kategori") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = menuTerbuka, onDismissRequest = { menuTerbuka = false }) {
                        AppConstants.SERVICE_CATEGORIES.forEach { c ->
                            DropdownMenuItem(text = { Text(c) }, onClick = { kategori = c; menuTerbuka = false })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSimpan(judul.trim(), kategori) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onBatal) { Text("Batal") } },
    )
}

/* --------------------------------------------------------------------------
 * Geser urutan
 * ----------------------------------------------------------------------- */

/**
 * Keadaan penggeseran untuk LazyVerticalGrid.
 *
 * Cara kerjanya: saat tekan-tahan dimulai, item di bawah jari dicari lewat
 * `layoutInfo`. Selama diseret, titik tengah item yang sedang dipegang dihitung
 * ulang dan dibandingkan dengan kotak item lain yang terlihat; begitu ia masuk
 * ke wilayah item lain, keduanya ditukar dan pergeserannya di-nol-kan karena
 * acuannya sudah pindah.
 *
 * Posisi item dibaca ulang dari `layoutInfo` setiap kali, tidak disimpan:
 * daftarnya berubah tiap kali terjadi pertukaran, sehingga info yang di-cache
 * langsung usang satu tukaran kemudian.
 */
private class PenggeserGrid(
    private val gridState: LazyGridState,
    private val onPindah: (Int, Int) -> Unit,
) {
    var indexDiseret by mutableStateOf<Int?>(null)
        private set
    var geser by mutableStateOf(Offset.Zero)
        private set

    fun mulai(posisi: Offset) {
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            posisi.x.toInt() in info.offset.x..(info.offset.x + info.size.width) &&
                posisi.y.toInt() in info.offset.y..(info.offset.y + info.size.height)
        }
        indexDiseret = item?.index
        geser = Offset.Zero
    }

    fun seret(jarak: Offset) {
        val dari = indexDiseret ?: return
        geser += jarak
        val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == dari } ?: return
        val pusatX = info.offset.x + info.size.width / 2f + geser.x
        val pusatY = info.offset.y + info.size.height / 2f + geser.y
        val tujuan = gridState.layoutInfo.visibleItemsInfo.firstOrNull { t ->
            t.index != dari &&
                pusatX in t.offset.x.toFloat()..(t.offset.x + t.size.width).toFloat() &&
                pusatY in t.offset.y.toFloat()..(t.offset.y + t.size.height).toFloat()
        } ?: return
        onPindah(dari, tujuan.index)
        indexDiseret = tujuan.index
        geser = Offset.Zero
    }

    fun selesai() {
        indexDiseret = null
        geser = Offset.Zero
    }
}

@Composable
private fun rememberPenggeser(
    gridState: LazyGridState,
    onPindah: (Int, Int) -> Unit,
): PenggeserGrid {
    val pindahTerbaru by rememberUpdatedState(onPindah)
    return remember(gridState) { PenggeserGrid(gridState) { a, b -> pindahTerbaru(a, b) } }
}
