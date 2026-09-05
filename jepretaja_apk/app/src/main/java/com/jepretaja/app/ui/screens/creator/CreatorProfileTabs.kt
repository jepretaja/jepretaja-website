package com.jepretaja.app.ui.screens.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.jepretaja.app.core.theme.AppColors
import com.jepretaja.app.core.util.Formatters
import com.jepretaja.app.data.model.ExplorePostModel
import com.jepretaja.app.data.model.PackageModel
import com.jepretaja.app.data.model.PortfolioModel
import com.jepretaja.app.ui.components.EmptyState
import com.jepretaja.app.ui.components.PremiumCard
import com.jepretaja.app.ui.components.SkeletonBox

/* --------------------------------------------------------------------------
 * Kenapa isi tab jadi extension LazyGridScope
 *
 * Sebelumnya tiap tab adalah composable mandiri berisi LazyVerticalGrid-nya
 * sendiri, dan layar induk menampungnya di dalam `Box(Modifier.height(520.dp))`
 * di tengah halaman yang JUGA menggulir. Akibatnya ada dua area gulir yang
 * bertumpuk: menggeser di dalam kotak menggerakkan grid tapi tidak halamannya,
 * menggeser di luar kotak menggerakkan halaman tapi tidak gridnya, dan
 * portofolio berisi 40 foto terkurung di jendela setinggi separuh layar.
 *
 * Sekarang seluruh halaman adalah SATU LazyVerticalGrid tiga kolom: kepala
 * profil dan blok keterangan jadi item selebar penuh, dan ubin portofolio
 * masuk sebagai item biasa di grid yang sama. Satu gulir, satu daftar, dan
 * kemalasan LazyGrid tetap terjaga — hanya ubin yang terlihat yang memuat
 * gambarnya, hal yang tidak bisa didapat kalau grid diganti kolom biasa.
 * ----------------------------------------------------------------------- */

/** Satu item selebar seluruh grid. */
fun LazyGridScope.itemPenuh(kunci: String? = null, isi: @Composable () -> Unit) {
    item(key = kunci, span = { GridItemSpan(maxLineSpan) }) { isi() }
}

/* --------------------------------------------------------------------------
 * Portfolio
 * ----------------------------------------------------------------------- */

/**
 * Grid portofolio.
 *
 * Ubinnya bisa diketuk untuk dibuka besar. Pada halaman yang seluruh gunanya
 * memamerkan hasil kerja, foto seukuran perangko yang tidak bisa dibuka berarti
 * karyanya tidak pernah benar-benar terlihat.
 */
fun LazyGridScope.isiPortofolio(
    items: List<PortfolioModel>?,
    onBuka: (PortfolioModel) -> Unit,
) {
    when {
        // `null` = belum ada jawaban. Kalau nilai awalnya emptyList(), profil
        // yang portofolionya penuh tetap menampilkan "Belum ada portfolio"
        // sekejap setiap kali tab dibuka.
        items == null -> kerangkaUbin(9)

        items.isEmpty() -> itemPenuh("portofolio_kosong") {
            EmptyState(
                icon = Icons.Default.PhotoLibrary,
                title = "Belum ada portfolio",
                description = "Creator ini belum mengunggah contoh hasil kerjanya.",
            )
        }

        else -> items(items, key = { it.portfolioId }) { item ->
            UbinPortofolio(item = item, onClick = { onBuka(item) })
        }
    }
}

@Composable
private fun UbinPortofolio(item: PortfolioModel, onClick: () -> Unit) {
    val thumb = item.media.firstOrNull()
    Box(
        Modifier
            .aspectRatio(0.75f)
            .background(AppColors.SurfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = item.title.ifBlank { "Portofolio" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.Image, contentDescription = null,
                tint = AppColors.TextSecondary, modifier = Modifier.align(Alignment.Center),
            )
        }
        // Penanda kalau satu entri berisi lebih dari satu foto — tanpa itu,
        // ubin yang membuka galeri terlihat sama persis dengan yang membuka
        // satu gambar.
        if (item.media.size > 1) {
            Icon(
                Icons.Default.Collections, contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp),
            )
        }
    }
}

/**
 * Pratinjau layar penuh untuk satu entri portofolio.
 *
 * Dibuat sebagai dialog, bukan rute baru: ini murni melihat gambar, tidak ada
 * yang perlu dibagikan lewat tautan atau dipulihkan setelah aplikasi ditutup,
 * jadi menambahkannya ke back stack hanya memperpanjang jalan pulang.
 */
@Composable
fun PratinjauPortfolio(item: PortfolioModel, onTutup: () -> Unit) {
    var indeks by remember(item.portfolioId) { mutableIntStateOf(0) }
    val media = item.media

    Dialog(
        onDismissRequest = onTutup,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f))) {
            AsyncImage(
                model = media.getOrNull(indeks),
                contentDescription = item.title.ifBlank { "Portofolio" },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(vertical = 80.dp),
            )

            IconButton(
                onClick = onTutup,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) { Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White) }

            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            ) {
                if (media.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(media.size) { i ->
                            AsyncImage(
                                model = media[i],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(54.dp)
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
 * Explore
 * ----------------------------------------------------------------------- */

fun LazyGridScope.isiExplore(
    posts: List<ExplorePostModel>?,
    pinnedPostId: String?,
    onPostClick: (String) -> Unit,
) {
    when {
        posts == null -> kerangkaUbin(9)

        posts.isEmpty() -> itemPenuh("explore_kosong") {
            EmptyState(
                icon = Icons.Default.GridView,
                title = "Belum ada konten Explore",
                description = "Karya yang diunggah ke Explore akan muncul di sini.",
            )
        }

        else -> {
            // Karya yang disematkan naik ke urutan pertama. Diurutkan di sini,
            // bukan lewat query: Firestore tidak bisa mengurutkan berdasarkan
            // "dokumen ini duluan", dan daftarnya sudah ada di tangan kita.
            val disematkan = posts.firstOrNull { it.postId == pinnedPostId }
            val urut = if (disematkan == null) posts else listOf(disematkan) + posts.filterNot { it.postId == pinnedPostId }
            items(urut, key = { it.postId }) { post ->
                UbinExplore(
                    post = post,
                    disematkan = post.postId == pinnedPostId,
                    onClick = { onPostClick(post.postId) },
                )
            }
        }
    }
}

@Composable
private fun UbinExplore(post: ExplorePostModel, disematkan: Boolean, onClick: () -> Unit) {
    val thumb = post.thumbnailUrl ?: post.mediaUrls.firstOrNull()
    Box(
        Modifier
            .aspectRatio(0.75f)
            .background(AppColors.SurfaceVariant)
            .clickable(onClick = onClick),
    ) {
        if (thumb != null) {
            AsyncImage(
                model = thumb,
                contentDescription = post.caption.ifBlank { "Karya" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (disematkan) {
            Row(
                Modifier.align(Alignment.TopStart).padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.small)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PushPin, contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text("Disematkan", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (post.type == "video") {
            Icon(
                Icons.Default.PlayCircle, contentDescription = "Video", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(18.dp),
            )
        }
    }
}

/* --------------------------------------------------------------------------
 * Paket
 * ----------------------------------------------------------------------- */

fun LazyGridScope.isiPaket(
    packages: List<PackageModel>?,
    onPackageClick: (String) -> Unit,
    onChat: () -> Unit,
) {
    when {
        packages == null -> items(3, span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                SkeletonBox(Modifier.fillMaxWidth().height(112.dp), RoundedCornerShape(18.dp))
            }
        }

        packages.isEmpty() -> itemPenuh("paket_kosong") {
            EmptyState(
                icon = Icons.Default.Inventory2,
                title = "Belum ada paket jasa",
                // Paket kosong bukan jalan buntu: creator yang belum memasang
                // harga biasanya masih menerima pekerjaan lewat percakapan.
                description = "Kamu masih bisa menanyakan harga langsung lewat chat.",
                actionLabel = "Tanya harga",
                onAction = onChat,
            )
        }

        else -> {
            val termurah = packages.minByOrNull { it.price }?.packageId
            items(
                packages,
                key = { it.packageId },
                span = { GridItemSpan(maxLineSpan) },
            ) { pkg ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    KartuPaket(
                        pkg = pkg,
                        termurah = pkg.packageId == termurah && packages.size > 1,
                        onClick = { onPackageClick(pkg.packageId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KartuPaket(pkg: PackageModel, termurah: Boolean, onClick: () -> Unit) {
    PremiumCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pkg.name,
                style = MaterialTheme.typography.titleSmall,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (termurah) {
                Spacer(Modifier.width(8.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(percent = 50))
                        .background(AppColors.Success.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.LocalOffer, contentDescription = null,
                        tint = AppColors.Success, modifier = Modifier.size(11.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("Termurah", style = MaterialTheme.typography.labelSmall, color = AppColors.Success)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BarisRinci(Icons.Default.Schedule, pkg.duration.ifBlank { "-" })
            Spacer(Modifier.width(12.dp))
            BarisRinci(Icons.Default.Groups, pkg.personnel?.takeIf { it.isNotBlank() } ?: "-")
        }
        pkg.output?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            BarisRinci(Icons.Default.Redeem, it)
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Formatters.currency(pkg.price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.W700,
                color = AppColors.Primary,
                modifier = Modifier.weight(1f),
            )
            // Petunjuk bahwa kartunya bisa diketuk. Kartu paket memang punya
            // onClick, tapi tanpa tanda visual jalur pemesanan tersembunyi di
            // balik tebakan.
            Text("Pilih paket", style = MaterialTheme.typography.labelLarge, color = AppColors.Primary)
            Icon(
                Icons.Default.ChevronRight, contentDescription = null,
                tint = AppColors.Primary, modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BarisRinci(ikon: ImageVector, teks: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ikon, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            teks,
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* --------------------------------------------------------------------------
 * Bersama
 * ----------------------------------------------------------------------- */

/** Kerangka ubin, bentuknya sama persis dengan ubin aslinya. */
private fun LazyGridScope.kerangkaUbin(jumlah: Int) {
    items(jumlah) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(0.75f), RoundedCornerShape(0.dp))
    }
}
