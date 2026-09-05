package com.jepretaja.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.jepretaja.app.core.theme.AppColors

/**
 * Header standar aplikasi.
 *
 * Dua hal yang diperbaiki dibanding pemakaian TopAppBar langsung sebelumnya:
 *
 * 1. **Ikut menggulung saat layar digulir.** Sebelumnya header diam menempel di
 *    atas dan memakan ruang terus-menerus — di layar HP yang tinggi layarnya
 *    terbatas, itu berarti sebagian isi selalu tertutup tanpa alasan. Sekarang
 *    header menyingkir saat pengguna menggulir ke bawah dan langsung kembali
 *    begitu digulir ke atas ([rememberAppTopBarScrollBehavior]).
 *
 * 2. **Ukuran judul lebih terkendali.** Judul memakai titleLarge, bukan
 *    headlineSmall serif yang dipakai sebelumnya di hampir semua layar. Fraunces
 *    berukuran besar bagus sebagai aksen di satu-dua tempat (wordmark Home,
 *    judul section), tapi ketika dipakai di SETIAP header ia terbaca ramai dan
 *    berat, bukan mewah.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppColors.Background,
            // Warna saat sebagian tergulung: sedikit berbeda dari latar supaya
            // header tetap terbaca sebagai lapisan di atas konten.
            scrolledContainerColor = AppColors.Surface,
            titleContentColor = AppColors.TextPrimary,
            navigationIconContentColor = AppColors.TextPrimary,
            actionIconContentColor = AppColors.TextPrimary,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Perilaku "sembunyi saat gulir ke bawah, muncul lagi saat gulir ke atas".
 *
 * Dipisah jadi fungsi sendiri supaya setiap layar memakai perilaku yang sama
 * persis, dan supaya jelas bahwa Scaffold-nya WAJIB memasang
 * `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` — tanpa itu
 * header tidak akan bergerak sama sekali meski scrollBehavior sudah diberikan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberAppTopBarScrollBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.enterAlwaysScrollBehavior()

/**
 * Header yang SELALU menempel di atas, tidak ikut menyingkir saat digulir.
 *
 * Dipakai layar yang headernya bukan sekadar judul: di Home ia memuat nama
 * pengguna beserta pintasan Chat dan Notifikasi, jadi menyembunyikannya saat
 * menggulir justru membuat kedua tombol itu hilang tepat ketika pengguna sedang
 * menelusuri isi halaman. Warna latarnya tetap berubah begitu konten mulai
 * bergulir, supaya batas antara header dan isi tetap terbaca.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberPinnedAppTopBarScrollBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.pinnedScrollBehavior()
