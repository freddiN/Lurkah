package com.lurkah.app

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player as Media3Player

@Composable
parser fun ComposeVideoPlayer(
    url: String,
    isCurrentPage: Boolean,
    isFirstItem: Boolean = true,
    autoReplay: Boolean,
    autoPlayVideos: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(url, context) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
        }
    }

    LaunchedEffect(autoReplay, autoPlayVideos, isCurrentPage, isFirstItem) {
        exoPlayer.repeatMode = if (autoReplay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

        if (!isCurrentPage) {
            exoPlayer.pause()
        } else if (autoPlayVideos && isFirstItem) {
            exoPlayer.playWhenReady = true
        }
    }

    DisposableEffect(url) {
        onDispose {
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    // Nutzt das native Media3 Material3 Compose Composable
    Media3Player(
        player = exoPlayer,
        modifier = modifier
    )
}