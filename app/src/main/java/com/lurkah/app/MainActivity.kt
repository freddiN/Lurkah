@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.lurkah.app

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.Dp

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
            onAutoReplayToggle = { viewModel.toggleAutoPlay(it) },
            onAddBlacklistUser = { viewModel.addBlacklistUser(it) },
            onRemoveBlacklistUser = { viewModel.removeBlacklistUser(it) },
            onAddBlacklistTag = { viewModel.addBlacklistTag(it) },
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
    var lastVideoPosition by remember { mutableStateOf(0L) }

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
                        onDoubleClick = { fullScreenPostIndex = index },
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
                // Variable, um sich die aktuelle Videoposition im BottomSheet zu merken

                PostDetailBottomSheet(
                    initialIndex = initialIndex,
                    viewModel = viewModel,
                    autoReplay = autoReplay,
                    onDismiss = { selectedPostIndex = null },
                    onDoubleClick = { index, position ->
                        lastVideoPosition = position // Position zwischenspeichern
                        selectedPostIndex = null
                        fullScreenPostIndex = index
                    }
                )
            }

            fullScreenPostIndex?.let { initialIndex ->
                FullScreenMediaViewer(
                    initialIndex = initialIndex,
                    posts = viewModel.posts,
                    autoReplay = autoReplay,
                    initialPlaybackPosition = lastVideoPosition, // Falls gewünscht übergeben
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
                        //.crossfade(true)
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
                        text = "${post.typeLabel} • ${post.formattedSize}",
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
    initialIndex: Int,
    posts: List<ImgurPost>,
    autoReplay: Boolean,
    initialPlaybackPosition: Long = 0L,
    viewModel: MainViewModel,
    onDismiss: (Int) -> Unit
) {
    val safeInitialPage = initialIndex.coerceIn(0, (posts.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { posts.size })

    val autoPlayVideos by viewModel.autoPlayVideos.collectAsState()

    Dialog(
        onDismissRequest = { onDismiss(pagerState.currentPage) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val post = posts.getOrNull(page) ?: return@HorizontalPager
                val isCurrentPage = pagerState.currentPage == page
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
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
                        val displayItems = remember(post.images, post.mediaUrl) {
                            val rawList = if (!post.images.isNullOrEmpty()) {
                                post.images!!
                            } else if (post.mediaUrl != null) {
                                listOf(
                                    ImgurImage(
                                        id = post.id,
                                        link = post.mediaUrl!!,
                                        mp4 = if (post.isVideo) post.mediaUrl else null,
                                        type = if (post.isVideo) "video/mp4" else "image/jpeg",
                                        size = post.sizeInBytes
                                    )
                                )
                            } else {
                                emptyList<ImgurImage>()
                            }

                            rawList.map { img ->
                                val fixedLink = if (img.link.endsWith(".gifv")) img.link.removeSuffix(".gifv") + ".mp4" else img.link
                                val fixedMp4 = img.mp4 ?: if (fixedLink.endsWith(".mp4")) fixedLink else null
                                img.copy(link = fixedLink, mp4 = fixedMp4)
                            }
                        }

                        if (displayItems.isNotEmpty()) {
                            val firstImg = displayItems.first()
                            val itemUrl = firstImg.mp4 ?: firstImg.link
                            val isItemVideo = (firstImg.type ?: "").startsWith("video/") || itemUrl?.endsWith(".mp4") == true

                            if (isItemVideo && itemUrl != null) {
                                VideoPlayer(
                                    videoUrl = itemUrl,
                                    isMuted = false,
                                    autoReplay = autoReplay,
                                    autoPlayVideos = autoPlayVideos && isCurrentPage,
                                    showControls = true,
                                    startPositionMs = if (page == safeInitialPage) initialPlaybackPosition else 0L, // <--- Position anwenden
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (itemUrl != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(itemUrl)
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
            }

            IconButton(
                onClick = { onDismiss(pagerState.currentPage) },
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
                        .fillMaxHeight()
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .align(Alignment.CenterStart)
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
            viewModel.loadFullAlbumDetails(post.id, pagerState.currentPage)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(0.9f)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) { page ->
            val post = viewModel.posts.getOrNull(page) ?: return@HorizontalPager
            val isCurrentPage = pagerState.currentPage == page

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "${post.typeLabel} • File size: ${post.formattedSize}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var tagToBlock by remember { mutableStateOf<String?>(null) }

                    if (post.tags.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            modifier = Modifier.padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(post.tags.take(5)) { tag ->
                                SuggestionChip(
                                    onClick = { tagToBlock = tag },
                                    label = { Text("#$tag") }
                                )
                            }
                        }
                    }

                    tagToBlock?.let { tag ->
                        AlertDialog(
                            onDismissRequest = { tagToBlock = null },
                            title = { Text("Block Tag?") },
                            text = { Text("Do you want to block the tag '#$tag'? Entries with this tag will be hidden.") },
                            confirmButton = {
                                Button(onClick = {
                                    viewModel.addBlacklistTag(tag)
                                    tagToBlock = null
                                }) { Text("Block") }
                            },
                            dismissButton = {
                                TextButton(onClick = { tagToBlock = null }) { Text("Cancel") }
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        val displayItems = remember(post.images, post.mediaUrl) {
                            val rawList = if (!post.images.isNullOrEmpty()) {
                                post.images!!
                            } else if (post.mediaUrl != null) {
                                listOf(
                                    ImgurImage(
                                        id = post.id,
                                        link = post.mediaUrl!!,
                                        mp4 = if (post.isVideo) post.mediaUrl else null,
                                        type = if (post.isVideo) "video/mp4" else "image/jpeg",
                                        size = post.sizeInBytes
                                    )
                                )
                            } else {
                                emptyList<ImgurImage>()
                            }

                            rawList.map { img ->
                                val fixedLink = if (img.link.endsWith(".gifv")) img.link.removeSuffix(".gifv") + ".mp4" else img.link
                                val fixedMp4 = img.mp4 ?: if (fixedLink.endsWith(".mp4")) fixedLink else null
                                img.copy(link = fixedLink, mp4 = fixedMp4)
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            displayItems.forEachIndexed { imgIndex, img ->
                                DetailMediaItem(
                                    img = img,
                                    imgIndex = imgIndex,
                                    post = post,
                                    isCurrentPage = isCurrentPage,
                                    autoReplay = autoReplay,
                                    autoPlayVideos = autoPlayVideos,
                                    onDoubleClick = { currentPos ->
                                        onDoubleClick(page, currentPos)
                                    },
                                    onPlayerReady = { player ->
                                        if (player != null) {
                                            activePlayer = player
                                        }
                                    }
                                )
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
fun VideoPlayer(
    videoUrl: String,
    isMuted: Boolean,
    autoReplay: Boolean,
    autoPlayVideos: Boolean,
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    startPositionMs: Long = 0L, // <--- Neu
    onDoubleClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            if (startPositionMs > 0L) {
                seekTo(startPositionMs) // <--- Springt zur exakten Position beim Start
            }
            prepare()
        }
    }

    LaunchedEffect(autoReplay, autoPlayVideos, isMuted) {
        exoPlayer.repeatMode = if (autoReplay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        exoPlayer.volume = if (isMuted) 0f else 1f
        exoPlayer.playWhenReady = autoPlayVideos
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = showControls
                    controllerAutoShow = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)

                    val gestureDetector = android.view.GestureDetector(
                        ctx,
                        object : android.view.GestureDetector.SimpleOnGestureListener() {
                            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                                onDoubleClick?.invoke()
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
            update = { playerView ->
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                }
                playerView.useController = showControls
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

fun Modifier.verticalScrollbar(
    state: LazyGridState,
    thumbColor: Color = Color.Gray.copy(alpha = 0.5f),
    thumbWidth: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()

    val layoutInfo = state.layoutInfo
    val totalItemsCount = layoutInfo.totalItemsCount
    if (totalItemsCount == 0) return@drawWithContent

    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return@drawWithContent

    val firstVisibleItem = visibleItems.first()
    val lastVisibleItem = visibleItems.last()

    if (firstVisibleItem.index == 0 && lastVisibleItem.index >= totalItemsCount - 1) {
        return@drawWithContent
    }

    val totalRows = (totalItemsCount + 1) / 2
    val averageItemHeight = visibleItems.sumOf { it.size.height }.toFloat() / visibleItems.size
    val totalEstimatedHeight = totalRows * averageItemHeight
    val viewportHeight = size.height

    if (totalEstimatedHeight <= viewportHeight) return@drawWithContent

    val scrollOffset = firstVisibleItem.index.toFloat() / 2 * averageItemHeight - firstVisibleItem.offset.y
    val scrollFraction = (scrollOffset / (totalEstimatedHeight - viewportHeight)).coerceIn(0f, 1f)

    val scrollbarHeight = (viewportHeight * (viewportHeight / totalEstimatedHeight)).coerceAtLeast(40f)
    val scrollbarY = scrollFraction * (viewportHeight - scrollbarHeight)
    val scrollbarX = size.width - thumbWidth.toPx() - 2.dp.toPx()

    drawRoundRect(
        color = thumbColor,
        topLeft = Offset(scrollbarX, scrollbarY),
        size = Size(thumbWidth.toPx(), scrollbarHeight),
        cornerRadius = CornerRadius(thumbWidth.toPx() / 2, thumbWidth.toPx() / 2)
    )
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
    val isItemVideo = (img.type ?: "").startsWith("video/") || itemUrl?.endsWith(".mp4") == true

    if (isItemVideo && itemUrl != null) {
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
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        controllerAutoShow = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)

                        val gestureDetector = android.view.GestureDetector(
                            ctx,
                            object : android.view.GestureDetector.SimpleOnGestureListener() {
                                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                                    val currentPos = localPlayer?.currentPosition ?: 0L
                                    onDoubleClick(currentPos)
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
                update = { playerView ->
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (itemUrl != null) {
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