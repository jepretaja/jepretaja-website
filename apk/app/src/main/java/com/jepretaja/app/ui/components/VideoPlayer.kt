package com.jepretaja.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

/**
 * Pemutar video feed Explore.
 *
 * `media3-exoplayer` sudah lama ada di daftar dependensi tapi tidak pernah
 * dipakai satu file pun: post bertipe "video" hanya menampilkan `thumbnailUrl`
 * sebagai gambar diam, sehingga video yang sudah diunggah creator praktis tidak
 * bisa ditonton siapa pun. Komponen ini yang membuatnya benar-benar berjalan.
 *
 * Perilakunya mengikuti kebiasaan feed vertikal: hanya post yang sedang aktif
 * di layar yang diputar ([playWhenActive]), sisanya di-pause supaya tidak ada
 * banyak video berbunyi bersamaan dan baterai/kuota tidak terbuang. Player juga
 * dilepas saat komposisi berakhir dan di-pause saat aplikasi masuk latar.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    playWhenActive: Boolean = true,
    thumbnailUrl: String? = null,
    muted: Boolean = false,
    /** Menampilkan bar posisi yang bisa digeser. */
    showScrubber: Boolean = false,
    speed: Float = 1f,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (muted) 0f else 1f
            prepare()
        }
    }

    // Hanya post yang sedang dilihat yang berputar.
    LaunchedEffect(playWhenActive) {
        player.playWhenReady = playWhenActive
        if (!playWhenActive) player.seekTo(0)
    }

    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }

    // Posisi diambil dengan polling 300ms, bukan lewat listener: ExoPlayer tidak
    // memberi peristiwa untuk setiap perubahan posisi, dan 300ms sudah cukup
    // halus untuk bar setipis ini sambil tetap ringan.
    var posisiMs by remember(url) { mutableLongStateOf(0L) }
    var durasiMs by remember(url) { mutableLongStateOf(0L) }
    var sedangGeser by remember(url) { mutableStateOf(false) }
    LaunchedEffect(player, showScrubber) {
        if (!showScrubber) return@LaunchedEffect
        while (true) {
            if (!sedangGeser) {
                posisiMs = player.currentPosition
                durasiMs = player.duration.coerceAtLeast(0L)
            }
            kotlinx.coroutines.delay(300)
        }
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = playWhenActive
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    Box(modifier) {
        // Thumbnail tetap digambar di belakang sebagai poster: frame pertama
        // ExoPlayer baru muncul setelah buffering, tanpa ini layar berkedip hitam.
        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (showScrubber && durasiMs > 0) {
            Slider(
                value = posisiMs.toFloat().coerceIn(0f, durasiMs.toFloat()),
                onValueChange = { nilai ->
                    sedangGeser = true
                    posisiMs = nilai.toLong()
                },
                onValueChangeFinished = {
                    player.seekTo(posisiMs)
                    sedangGeser = false
                },
                valueRange = 0f..durasiMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                ),
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}
