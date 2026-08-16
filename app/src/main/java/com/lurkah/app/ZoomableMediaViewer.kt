package com.lurkah.app

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage

@Composable
fun ZoomableMediaViewer(
    url: String,
    contentDesc: String,
    isFullScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (url.isBlank()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(text = "Media not available", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val context = LocalContext.current

    val safeUrl = remember(url) {
        var clean = url.replace("http://", "https://")
        if (clean.contains("imgur.com") && !clean.contains("i.imgur.com")) {
            clean = clean.replace("imgur.com", "i.imgur.com")
        }
        if (!clean.endsWith(".jpg") && !clean.endsWith(".png") && !clean.endsWith(".gif") && !clean.endsWith(".mp4")) {
            "$clean.jpg"
        } else {
            clean
        }
    }

    val imageRequest = remember(safeUrl) {
        ImageRequest.Builder(context)
            .data(safeUrl)
            .crossfade(true)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .listener(
                onError = { _, result ->
                    Log.e("ImageLoadError", "Fehler beim Laden von: $safeUrl\nGrund: ${result.throwable.message}")
                }
            )
            .build()
    }

    // Beide Ansichten nutzen jetzt ZoomableAsyncImage für volle Zoom-Funktionalität
    if (isFullScreen) {
        ZoomableAsyncImage(
            model = imageRequest,
            contentDescription = contentDesc,
            modifier = modifier.fillMaxSize()
        )
    } else {
        ZoomableAsyncImage(
            model = imageRequest,
            contentDescription = contentDesc,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 800.dp)
        )
    }
}