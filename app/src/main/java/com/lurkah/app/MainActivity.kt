@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@file:Suppress("UnstableApiUsage")

package com.lurkah.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val isDarkMode by mainViewModel.isDarkMode.collectAsState()
            val autoReplay by mainViewModel.autoReplay.collectAsState()
            val colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ImgurAppContent(
                        viewModel = mainViewModel,
                        isDarkMode = isDarkMode,
                        autoReplay = autoReplay
                    )
                }
            }
        }
    }
}

fun Modifier.verticalScrollbar(
    state: LazyGridState,
    width: Dp = 4.dp
): Modifier = composed {
    val targetAlpha = if (state.isScrollInProgress) 1f else 0f
    val duration = if (state.isScrollInProgress) 150 else 500
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration),
        label = "scrollbarAlpha"
    )

    drawWithContent {
        drawContent()
        val firstVisibleElementIndex = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index
        val needDrawScrollbar = state.isScrollInProgress || alpha > 0f
        val totalCount = state.layoutInfo.totalItemsCount

        if (needDrawScrollbar && firstVisibleElementIndex != null && totalCount > 0) {
            val totalRows = (totalCount + 1) / 2
            val firstVisibleRow = firstVisibleElementIndex / 2
            val visibleRows = (state.layoutInfo.visibleItemsInfo.size + 1) / 2

            val elementHeight = size.height / totalRows
            val scrollbarOffsetY = firstVisibleRow * elementHeight
            val scrollbarHeight = visibleRows * elementHeight

            drawRoundRect(
                color = Color.Gray,
                topLeft = Offset(size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2),
                alpha = alpha
            )
        }
    }
}

@Composable
fun ImgurAppContent(viewModel: MainViewModel, isDarkMode: Boolean, autoReplay: Boolean) {
    var currentScreen by remember { mutableStateOf("feed") }
    val blacklistedUsers by viewModel.blacklistedUsers.collectAsState()
    val blacklistedTags by viewModel.blacklistedTags.collectAsState()
    val autoPlayVideos by viewModel.autoPlayVideos.collectAsState()

    BackHandler(enabled = currentScreen == "settings") {
        currentScreen = "feed"
    }

    if (currentScreen == "settings") {
        SettingsScreen(
            isDarkMode = isDarkMode,
            autoPlayVideos = autoPlayVideos,
            autoReplay = autoReplay,
            blacklistedUsers = blacklistedUsers,
            blacklistedTags = blacklistedTags,
            onDarkModeToggle = { viewModel.toggleDarkMode(it) },
            onAutoPlayVideosToggle = { viewModel.toggleAutoPlayVideos(it) },
            onAutoReplayToggle = { viewModel.toggleAutoReplay(it) },
            onRemoveBlacklistUser = { viewModel.removeBlacklistUser(it) },
            onRemoveBlacklistTag = { viewModel.removeBlacklistTag(it) },
            modifier = Modifier.systemBarsPadding()
        )
    } else {
        ImgurFeedScreen(
            viewModel = viewModel,
            autoReplay = autoReplay,
            onOpenSettings = { currentScreen = "settings" }
        )
    }
}

@Composable
fun ImgurFeedScreen(
    viewModel: MainViewModel,
    autoReplay: Boolean,
    onOpenSettings: () -> Unit
) {
    var fullScreenFeedIndex by remember { mutableStateOf<Int?>(null) }
    var userToBlock by remember { mutableStateOf<String?>(null) }

    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()

    viewModel.errorMessage?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Notice") },
            text = { Text(errorMsg) },
            confirmButton = {
                Button(onClick = { viewModel.clearError() }) { Text("OK") }
            }
        )
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !viewModel.isLoadingMore && !viewModel.isRefreshing) {
            viewModel.loadViralPosts(isRefresh = false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lurkah") },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadViralPosts(isRefresh = true) }
                    ) {
                        Text("↻", fontSize = 18.sp)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Text("⚙️", fontSize = 18.sp)
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.loadViralPosts(isRefresh = true) },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScrollbar(gridState)
            ) {
                itemsIndexed(
                    items = viewModel.posts,
                    key = { _, post -> post.id }
                ) { index, post ->
                    SmartMediaCard(
                        post = post,
                        onClick = { fullScreenFeedIndex = index },
                        onAccountClick = { author -> userToBlock = author }
                    )
                }

                if (viewModel.isLoadingMore) {
                    item(
                        key = "loading_indicator",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        userToBlock?.let { author ->
            AlertDialog(
                onDismissRequest = { userToBlock = null },
                title = { Text("Block User?") },
                text = { Text("Do you want to add '@$author' to your blocked list?") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.addBlacklistUser(author)
                        userToBlock = null
                    }) { Text("Block") }
                },
                dismissButton = {
                    TextButton(onClick = { userToBlock = null }) { Text("Cancel") }
                }
            )
        }

        fullScreenFeedIndex?.let { startIndex ->
            FullScreenFeedViewer(
                initialIndex = startIndex,
                viewModel = viewModel,
                autoReplay = autoReplay,
                onDismiss = { fullScreenFeedIndex = null }
            )
        }
    }
}

@Composable
fun SmartMediaCard(
    post: ImgurPost,
    onClick: () -> Unit,
    onAccountClick: (String) -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val context = LocalContext.current

    val imageCount = post.images?.size ?: 1
    val labelText = if (imageCount > 1) {
        "📁 ALBUM ($imageCount)"
    } else {
        "${post.typeLabel} • ${post.formattedSize}"
    }

    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { currentOnClick() }
                        )
                    }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(post.thumbnailUrl)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (post.isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = "▶",
                                color = Color.White,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = labelText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            post.accountUrl?.let { author ->
                Text(
                    text = "@$author",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable { onAccountClick(author) }
                )
            }
        }
    }
}

@Composable
fun FullScreenFeedViewer(
    initialIndex: Int,
    viewModel: MainViewModel,
    autoReplay: Boolean,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) {
        onDismiss()
    }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (viewModel.posts.size - 1).coerceAtLeast(0)),
        pageCount = { viewModel.posts.size }
    )
    val autoPlayVideos by viewModel.autoPlayVideos.collectAsState()

    LaunchedEffect(pagerState.currentPage) {
        val currentPost = viewModel.posts.getOrNull(pagerState.currentPage)
        currentPost?.let { post ->
            if ((post.images?.size ?: 1) > 1 || post.images == null) {
                // FIX: Kurze Verzögerung, um sicherzustellen, dass die Bilder geladen sind
                delay(300)
                viewModel.loadFullAlbumDetails(post.id)
            }
        }

        if (pagerState.currentPage >= viewModel.posts.size - 3) {
            // FIX: Verzögerung beim Weiterblättern, um sicherzustellen, dass die Bilder geladen sind
            delay(300)
            viewModel.loadViralPosts(isRefresh = false)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val post = viewModel.posts.getOrNull(page) ?: return@HorizontalPager
                val isCurrentPage = pagerState.currentPage == page

                val cachedImages = viewModel.albumImagesCache[post.id]
                val displayItems = remember(cachedImages, post.images, post.mediaUrl) {
                    val rawList = when {
                        !cachedImages.isNullOrEmpty() -> cachedImages
                        !post.images.isNullOrEmpty() -> post.images
                        post.mediaUrl != null -> listOf(
                            ImgurImage(
                                id = post.id,
                                link = post.mediaUrl!!,
                                mp4 = if (post.isVideo) post.mediaUrl else null,
                                type = if (post.isVideo) "video/mp4" else "image/jpeg",
                                size = post.sizeInBytes
                            )
                        )
                        else -> emptyList()
                    }

                    rawList.map { img ->
                        val fixedLink = if (img.link.endsWith(".gifv")) img.link.removeSuffix(".gifv") + ".mp4" else img.link
                        val fixedMp4 = img.mp4 ?: if (fixedLink.endsWith(".mp4")) fixedLink else null
                        img.copy(link = fixedLink, mp4 = fixedMp4)
                    }
                }

                if (displayItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (displayItems.size == 1) {
                    val img = displayItems.first()
                    val itemUrl = img.mp4 ?: img.link
                    val isItemVideo = (img.type ?: "").startsWith("video/") || itemUrl.endsWith(".mp4")

                    if (isItemVideo) {
                        ComposeVideoPlayer(
                            url = itemUrl,
                            isCurrentPage = isCurrentPage,
                            isFirstItem = true,
                            autoReplay = autoReplay,
                            autoPlayVideos = autoPlayVideos,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ZoomableMediaViewer(
                            url = itemUrl,
                            contentDesc = post.title,
                            isFullScreen = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 64.dp)
                    ) {
                        item {
                            Text(
                                text = post.title,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        itemsIndexed(displayItems, key = { index, img -> "${img.id}_$index" }) { index, img ->
                            val itemUrl = img.mp4 ?: img.link
                            val isItemVideo = (img.type ?: "").startsWith("video/") || itemUrl.endsWith(".mp4")

                            if (isItemVideo) {
                                ComposeVideoPlayer(
                                    url = itemUrl,
                                    isCurrentPage = isCurrentPage,
                                    isFirstItem = index == 0,
                                    autoReplay = autoReplay,
                                    autoPlayVideos = autoPlayVideos,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(350.dp)
                                        .padding(vertical = 8.dp)
                                )
                            } else {
                                ZoomableMediaViewer(
                                    url = itemUrl,
                                    contentDesc = post.title,
                                    isFullScreen = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
            ) {
                Text(text = "✕", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}