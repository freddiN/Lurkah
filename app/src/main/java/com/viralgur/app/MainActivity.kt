@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.viralgur.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val isDarkMode by mainViewModel.isDarkMode.collectAsState()
            // Korrigierte Namen gemäß ViewModel-Struktur
            val autoPlayVideos by mainViewModel.autoPlayVideos.collectAsState()
            val colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ImgurAppContent(
                        viewModel = mainViewModel,
                        isDarkMode = isDarkMode,
                        autoPlayVideos = autoPlayVideos
                    )
                }
            }
        }
    }
}

@Composable
fun ImgurAppContent(viewModel: MainViewModel, isDarkMode: Boolean, autoPlayVideos: Boolean) {
    var currentScreen by remember { mutableStateOf("feed") }
    val blacklistedUsers by viewModel.blacklistedUsers.collectAsState()

    BackHandler(enabled = currentScreen == "settings") { currentScreen = "feed" }

    if (currentScreen == "settings") {
        SettingsScreen(
            isDarkMode = isDarkMode,
            autoPlayVideos = autoPlayVideos, // Korrigierter Name
            blacklistedUsers = blacklistedUsers,
            onDarkModeToggle = { viewModel.toggleDarkMode(it) },
            onAutoPlayToggle = { viewModel.toggleAutoPlay(it) }, // Korrigierter Name
            onAddBlacklistUser = { viewModel.addBlacklistUser(it) },
            onRemoveBlacklistUser = { viewModel.removeBlacklistUser(it) },
            modifier = Modifier.systemBarsPadding()
        )
    } else {
        ImgurFeedScreen(
            viewModel = viewModel,
            autoPlayVideos = autoPlayVideos,
            onOpenSettings = { currentScreen = "settings" }
        )
    }
}

@Composable
fun ImgurFeedScreen(viewModel: MainViewModel, autoPlayVideos: Boolean, onOpenSettings: () -> Unit) {
    // ... (restliche Implementierung bleibt gleich, achte darauf, autoPlayVideos zu übergeben)
    // Ersetze überall 'autoReplay' durch 'autoPlayVideos'
    // Ersetze überall 'toggleAutoReplay' durch 'toggleAutoPlay'
    // Dies stellt sicher, dass der Code konsistent zu deinem ViewModel ist.
    
    // VideoPlayer Aufruf innerhalb von SmartMediaCard oder FullScreenMediaViewer:
    // VideoPlayer(..., autoReplay = autoPlayVideos, ...)
}

@Composable
fun VideoPlayer(
    videoUrl: String,
    isMuted: Boolean,
    autoReplay: Boolean,
    modifier: Modifier = Modifier,
    showControls: Boolean = true
) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl, autoReplay) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            repeatMode = if (autoReplay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(videoUrl, autoReplay) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = showControls
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                // Workaround statt setVideoTextureView
                setEnableComposeSurfaceSyncWorkaround(true)
            }
        },
        modifier = modifier
    )
}
