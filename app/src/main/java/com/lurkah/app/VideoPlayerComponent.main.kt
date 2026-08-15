@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.lurkah.app

import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.*

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
            exoPlayer.pause()
            exoPlayer.clearVideoSurface() // Wichtig: Trennt die Oberfläche, verhindert den schwarzen Aufblitzeffekt
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier.background(Color.Transparent) // Auf Transparent setzen statt Surface-Farbe
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = showControls
                    controllerAutoShow = false
                    hideController()
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

                    // Verhindert das schwarze Aufblitzen des Standard-Shutters vom PlayerView
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)

                    val gestureDetector = android.view.GestureDetector(
                        ctx,
                        object : android.view.GestureDetector.SimpleOnGestureListener() {
                            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                                currentOnDoubleClick?.invoke()
                                return true
                            }
                        }
                    )

                    setOnTouchListener { _, event ->
                        gestureDetector.onTouchEvent(event)
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}