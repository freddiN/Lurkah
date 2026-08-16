@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
@file:Suppress("UnstableApiUsage")

package com.lurkah.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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

        if (needDrawScrollbar && firstVisibleElementIndex != null) {
            val elementHeight = size.height / state.layoutInfo.totalItemsCount
            val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
            val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

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
    var selectedPostIndex by remember { mutableStateOf<Int?>(null) }
    var fullScreenPostIndex by remember { mutableStateOf<Int?>(null) }
    var userToBlock by remember { mutableStateOf<String?>(null) }
    var lastVideoPosition by remember { mutableLongStateOf(0L) }

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

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.loadViralPosts(isRefresh = true)
        }
    }

    LaunchedEffect(viewModel.isRefreshing) {
        if (!viewModel.isRefreshing) {
            pullToRefreshState.endRefresh()
        }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
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
                        onClick = { selectedPostIndex = index },
                        onDoubleClick = {
                            lastVideoPosition = 0L
                            fullScreenPostIndex = index
                        },
                        onAccountClick = { author -> userToBlock = author }
                    )
                }

                if (viewModel.isLoadingMore) {
                    item(key = "loading_indicator") {
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

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )

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

            selectedPostIndex?.let { initialIndex ->
                PostDetailBottomSheet(
                    initialIndex = initialIndex,
                    viewModel = viewModel,
                    autoReplay = autoReplay,
                    onDismiss = { selectedPostIndex = null },
                    onDoubleClick = { index, position ->
                        lastVideoPosition = position
                        fullScreenPostIndex = index
                    }
                )
            }

            fullScreenPostIndex?.let { initialIndex ->
                FullScreenMediaViewer(
                    initialIndex = initialIndex,
                    posts = viewModel.posts,
                    autoReplay = autoReplay,
                    initialPlaybackPosition = lastVideoPosition,
                    viewModel = viewModel,
                    onDismiss = { index ->
                        fullScreenPostIndex = null
                        selectedPostIndex = index
                    }
                )
            }
        }
    }
}

@Composable
fun SmartMediaCard(
    post: ImgurPost,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onAccountClick: (String) -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
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
                            onTap = { currentOnClick() },
                            onDoubleTap = { currentOnDoubleClick() }
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
fun FullScreenMediaViewer(
    initialIndex: Int, // Das ist jetzt der gewählte Album-Index!
    post: ImgurPost,
    autoReplay: Boolean,
    initialPlaybackPosition: Long = 0L,
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
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
            val fixedLink = if (img.link.endsWith(".gifv")) img.link.removeSuffix(".gifv") + ".jpg" else img.link
            val fixedMp4 = img.mp4 ?: if (img.link.endsWith(".mp4")) img.link else null
            img.copy(link = fixedLink, mp4 = fixedMp4)
        }
    }

    val safeInitialPage = initialIndex.coerceIn(0, (displayItems.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { displayItems.size })
    val autoPlayVideos by viewModel.autoPlayVideos.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                beyondBoundsPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val img = displayItems.getOrNull(page) ?: return@HorizontalPager
                val isCurrentPage = pagerState.currentPage == page

                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                val itemUrl = img.mp4 ?: img.link
                val isItemVideo = (img.type ?: "").startsWith("video/") || itemUrl.endsWith(".mp4")

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(scale) {
                            if (scale > 1f) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isItemVideo) {
                            VideoPlayer(
                                videoUrl = itemUrl,
                                isMuted = false,
                                autoReplay = autoReplay,
                                autoPlayVideos = autoPlayVideos && isCurrentPage,
                                showControls = true,
                                startPositionMs = if (page == safeInitialPage) initialPlaybackPosition else 0L,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(itemUrl)
                                    .crossfade(true)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                contentDescription = post.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = {
                                                if (scale > 1f) {
                                                    scale = 1f
                                                    offsetX = 0f
                                                    offsetY = 0f
                                                } else {
                                                    scale = 2.5f
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            // Close Button
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

@Composable
fun PostDetailBottomSheet(
    initialIndex: Int,
    viewModel: MainViewModel,
    autoReplay: Boolean,
    onDismiss: () -> Unit,
    onDoubleClick: (Int, Long) -> Unit
) {
    val safeInitialPage = initialIndex.coerceIn(0, (viewModel.posts.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { viewModel.posts.size })
    val autoPlayVideos by viewModel.autoPlayVideos.collectAsState()

    var activePlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(pagerState.currentPage) {
        val currentPost = viewModel.posts.getOrNull(pagerState.currentPage)
        currentPost?.let { post ->
            viewModel.loadCommentsForPost(post.id)
            viewModel.loadFullAlbumDetails(post.id)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        HorizontalPager(
            state = pagerState,
            beyondBoundsPageCount = 1,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) { page ->
            val post = viewModel.posts.getOrNull(page) ?: return@HorizontalPager
            val isCurrentPage = pagerState.currentPage == page

            val currentImages = viewModel.albumImagesCache[post.id] ?: post.images

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 64.dp)
            ) {
                item {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val imageCount = currentImages?.size ?: post.images?.size ?: 1
                    val typeLabelText = if (imageCount > 1) {
                        "📁 ALBUM ($imageCount items)"
                    } else {
                        "${post.typeLabel} • File size: ${post.formattedSize}"
                    }

                    Text(
                        text = typeLabelText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (post.tags.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(post.tags.take(5)) { tag ->
                                SuggestionChip(
                                    onClick = { onDismiss() },
                                    label = { Text("#$tag") }
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
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
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        } else {
                            val albumPagerState = rememberPagerState(pageCount = { displayItems.size })

                            HorizontalPager(
                                state = albumPagerState,
                                beyondBoundsPageCount = 1,
                                modifier = Modifier.fillMaxSize()
                            ) { albumPageIndex ->
                                val img = displayItems[albumPageIndex]
                                val shouldPlayVideo = isCurrentPage && albumPagerState.currentPage == albumPageIndex

                                val itemUrl = img.mp4 ?: img.link
                                val isItemVideo = (img.type ?: "").startsWith("video/") || itemUrl.endsWith(".mp4") || itemUrl.endsWith(".gifv")

                                if (!isItemVideo) {
                                    val imageUrl = if (img.link.endsWith(".gifv")) {
                                        img.link.removeSuffix(".gifv") + ".jpg"
                                    } else {
                                        img.link
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        // Übertägt den exakten Index des Bildes im Album
                                                        onDoubleClick(albumPageIndex, 0L)
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(imageUrl)
                                                .placeholderMemoryCacheKey(post.thumbnailUrl)
                                                .crossfade(true)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .build(),
                                            contentDescription = post.title,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                } else {
                                    DetailMediaItem(
                                        img = img,
                                        imgIndex = albumPageIndex,
                                        post = post,
                                        isCurrentPage = shouldPlayVideo,
                                        autoReplay = autoReplay,
                                        autoPlayVideos = autoPlayVideos,
                                        onDoubleClick = { currentPos ->
                                            // Stoppt den Player sofort, um doppeltes Audio zu vermeiden
                                            activePlayer?.playWhenReady = false
                                            onDoubleClick(albumPageIndex, currentPos)
                                        },
                                        onPlayerReady = { player ->
                                            if (player != null) {
                                                if (!shouldPlayVideo) {
                                                    player.playWhenReady = false
                                                } else {
                                                    activePlayer = player
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            if (displayItems.size > 1) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "${albumPagerState.currentPage + 1} / ${displayItems.size}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Comments",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                }

                if (viewModel.isLoadingComments) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (viewModel.selectedPostComments.isEmpty()) {
                    item {
                        Text(
                            text = "No comments available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }

                items(
                    items = viewModel.selectedPostComments,
                    key = { comment -> comment.id }
                ) { comment ->
                    CommentItem(comment = comment, depth = 0)
                }
            }
        }
    }
}

@Composable
fun DetailMediaItem(
    img: ImgurImage,
    imgIndex: Int,
    post: ImgurPost,
    isCurrentPage: Boolean,
    autoReplay: Boolean,
    autoPlayVideos: Boolean,
    onDoubleClick: (Long) -> Unit,
    onPlayerReady: (ExoPlayer?) -> Unit
) {
    val itemUrl = img.mp4 ?: img.link
    val isItemVideo = (img.type ?: "").startsWith("video/") || itemUrl.endsWith(".mp4")

    if (isItemVideo) {
        val context = LocalContext.current
        var localPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

        val exoPlayer = remember(itemUrl) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(itemUrl)))
                prepare()
            }
        }

        LaunchedEffect(exoPlayer) {
            localPlayer = exoPlayer
            if (isCurrentPage) onPlayerReady(exoPlayer)
        }

        LaunchedEffect(autoReplay, autoPlayVideos, isCurrentPage) {
            exoPlayer.repeatMode = if (autoReplay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            exoPlayer.playWhenReady = autoPlayVideos && isCurrentPage
            if (isCurrentPage) onPlayerReady(exoPlayer)
        }

        DisposableEffect(itemUrl) {
            onDispose {
                exoPlayer.release()
                onPlayerReady(null)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 350.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        controllerAutoShow = false
                        controllerShowTimeoutMs = 2500
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)

                        // Erzwingt das Ausblenden beim Initialisieren
                        hideController()

                        val gestureDetector = android.view.GestureDetector(
                            ctx,
                            object : android.view.GestureDetector.SimpleOnGestureListener() {
                                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                                    if (isControllerFullyVisible) {
                                        hideController()
                                    } else {
                                        showController()
                                    }
                                    return true
                                }

                                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                                    val currentPos = localPlayer?.currentPosition ?: 0L
                                    onDoubleClick(currentPos)
                                    return true
                                }
                            }
                        )
                        setOnTouchListener { _, event ->
                            gestureDetector.onTouchEvent(event)
                            true
                        }
                    }
                },
                update = { playerView ->
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                        playerView.hideController()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(itemUrl)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = "${post.title} - ${imgIndex + 1}",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 350.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onDoubleClick(0L) }
                    )
                }
        )
    }
}

@Composable
fun CommentItem(comment: ImgurComment, depth: Int = 0) {
    val maxDepth = 4
    val currentIndent = depth.coerceAtMost(maxDepth)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        if (currentIndent > 0) {
            repeat(currentIndent) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    if (depth > 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    else Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "@${comment.author}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                val score = comment.ups - comment.downs
                Text(
                    text = "▲ $score",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = comment.comment,
                style = MaterialTheme.typography.bodyMedium
            )

            comment.children?.forEach { childComment ->
                Spacer(modifier = Modifier.height(4.dp))
                CommentItem(comment = childComment, depth = depth + 1)
            }
        }
    }
}