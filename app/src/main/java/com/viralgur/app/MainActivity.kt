@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.viralgur.app

import android.net.Uri
import android.os.Bundle
import android.view.TextureView
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

    BackHandler(enabled = currentScreen == "settings") {
        currentScreen = "feed"
    }

    if (currentScreen == "settings") {
        SettingsScreen(
            isDarkMode = isDarkMode,
            autoReplay = autoReplay,
            blacklistedUsers = blacklistedUsers,
            onDarkModeToggle = { viewModel.toggleDarkMode(it) },
            onAutoReplayToggle = { viewModel.toggleAutoReplay(it) },
            onAddBlacklistUser = { viewModel.addBlacklistUser(it) },
            onRemoveBlacklistUser = { viewModel.removeBlacklistUser(it) },
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

    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()

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
                title = { Text("ViralGur") },
                actions = {
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
                modifier = Modifier.fillMaxSize()
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
                PostDetailBottomSheet(
                    initialIndex = initialIndex,
                    viewModel = viewModel,
                    autoReplay = autoReplay,
                    onDismiss = { selectedPostIndex = null },
                    onDoubleClick = { index -> fullScreenPostIndex = index }
                )
            }

            fullScreenPostIndex?.let { initialIndex ->
                FullScreenMediaViewer(
                    initialIndex = initialIndex,
                    posts = viewModel.posts,
                    autoReplay = autoReplay,
                    onDismiss = { fullScreenPostIndex = null }
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
                        .crossfade(true)
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
                            .fillMaxSize()
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
    onDismiss: () -> Unit
) {
    val safeInitialPage = initialIndex.coerceIn(0, (posts.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { posts.size })

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
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val post = posts.getOrNull(page) ?: return@HorizontalPager
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
                        if (post.isVideo && post.mediaUrl != null) {
                            if (pagerState.currentPage == page) {
                                VideoPlayer(
                                    videoUrl = post.mediaUrl!!,
                                    isMuted = false,
                                    autoReplay = autoReplay,
                                    showControls = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            AsyncImage(
                                model = post.mediaUrl,
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
    onDoubleClick: (Int) -> Unit
) {
    val safeInitialPage = initialIndex.coerceIn(0, (viewModel.posts.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { viewModel.posts.size })

    LaunchedEffect(pagerState.currentPage) {
        val currentPost = viewModel.posts.getOrNull(pagerState.currentPage)
        currentPost?.let { viewModel.loadCommentsForPost(it.id) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(0.9f)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) { page ->
            val post = viewModel.posts.getOrNull(page) ?: return@HorizontalPager

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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        if (post.isVideo && post.mediaUrl != null) {
                            if (pagerState.currentPage == page) {
                                VideoPlayer(
                                    videoUrl = post.mediaUrl!!,
                                    isMuted = false,
                                    autoReplay = autoReplay,
                                    showControls = true,
                                    onDoubleClick = { onDoubleClick(page) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 350.dp)
                                )
                            }
                        } else {
                            AsyncImage(
                                model = post.mediaUrl,
                                contentDescription = post.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 350.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { onDoubleClick(page) }
                                        )
                                    }
                            )
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
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    onDoubleClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)

    val exoPlayer = remember(videoUrl, autoReplay) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            repeatMode = if (autoReplay) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            volume = if (isMuted) 0f else 1f
            pauseAtEndOfMediaItems = !autoReplay
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(videoUrl, autoReplay) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = showControls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // Nutzen einer direkten TextureView via PlayerView-Zuweisung um das Aufblitzen zu verhindern
                    val textureView = TextureView(ctx)
                    setVideoTextureView(textureView)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (currentOnDoubleClick != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { currentOnDoubleClick?.invoke() }
                        )
                    }
            )
        }
    }
}
