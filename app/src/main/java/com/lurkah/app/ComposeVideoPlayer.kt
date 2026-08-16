package com.lurkah.app

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.core.net.toUri

@Composable
fun ComposeVideoPlayer(
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
            setMediaItem(MediaItem.fromUri(url.toUri()))
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

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier.clickable {
            exoPlayer.playWhenReady = !exoPlayer.playWhenReady
        }
    ) {
        Media3Player(
            player = exoPlayer,
            modifier = Modifier.fillMaxSize()
        )
    }
}