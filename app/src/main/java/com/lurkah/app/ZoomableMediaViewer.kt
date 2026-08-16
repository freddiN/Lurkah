package com.lurkah.app

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val context = LocalContext.current

    // FIX (Theorie 2): URL säubern. Falls Imgur einen Link ohne Endung liefert, machen wir ein JPG daraus.
    val safeUrl = remember(url) {
        var clean = url.replace("http://", "https://")
        // Wenn es kein i.imgur.com link ist, mach es zu einem
        if (clean.contains("imgur.com") && !clean.contains("i.imgur.com")) {
            clean = clean.replace("imgur.com", "i.imgur.com")
        }
        // Wenn keine Bild-Endung vorhanden ist (und es kein Video ist), hänge .jpg an
        if (!clean.endsWith(".jpg") && !clean.endsWith(".png") && !clean.endsWith(".gif") && !clean.endsWith(".mp4")) {
            "$clean.jpg"
        } else {
            clean
        }
    }

    // FIX (Theorie 3): ImageRequest mit Fake-Header und Error-Logging bauen
    val imageRequest = remember(safeUrl) {
        ImageRequest.Builder(context)
            .data(safeUrl)
            .crossfade(true)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)") // Umgeht CDN-Blockaden
            .listener(
                onError = { _, result ->
                    Log.e("ImageLoadError", "Fehler beim Laden von: $safeUrl\nGrund: ${result.throwable.message}")
                },
                onSuccess = { _, _ ->
                    Log.d("ImageLoadSuccess", "Erfolgreich geladen: $safeUrl")
                }
            )
            .build()
    }

    // FIX (Theorie 1): Wenn es NICHT Fullscreen ist (also im LazyColumn Album), nutze natives AsyncImage!
    if (isFullScreen) {
        ZoomableAsyncImage(
            model = imageRequest,
            contentDescription = contentDesc,
            modifier = modifier.fillMaxSize()
        )
    } else {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDesc,
            contentScale = ContentScale.Inside, // Stellt sicher, dass hohe Bilder korrekt eingepasst werden
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 800.dp) // Großzügigere Bounds für Alben
        )
    }
}