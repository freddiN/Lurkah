@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.lurkah.app

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun VideoPlayer(
    videoUrl: String,
    isMuted: Boolean,
    autoReplay: Boolean,
    autoPlayVideos: Boolean,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    onDoubleClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)

    val exoPlayer = remember(videoUrl, autoReplay, autoPlayVideos) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            repeatMode = if (autoReplay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (isMuted) 0f else 1f
            pauseAtEndOfMediaItems = !autoReplay
            prepare()
            playWhenReady = autoPlayVideos
        }
    }

    DisposableEffect(videoUrl, autoReplay, autoPlayVideos) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearVideoSurface()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // Die Gesten-Erkennung wird sauber über den Compose Modifier geregelt,
    // das verhindert Konflikte mit dem Android View TouchListener.
    Box(
        modifier = modifier
            .background(Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        currentOnDoubleClick?.invoke()
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = showControls
                    controllerAutoShow = false
                    hideController()

                    // Korrekter Resize-Mode, der Verzerrungen verhindert
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                }
            },
            modifier = Modifier.matchParentSize()
        )
    }
}