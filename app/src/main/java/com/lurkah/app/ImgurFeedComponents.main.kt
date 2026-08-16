@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.lurkah.app

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.RoundedCornerShape

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
                PostDetailBottomSheet(
                    initialIndex = initialIndex,
                    viewModel = viewModel,
                    autoReplay = autoReplay,
                    onDismiss = { selectedPostIndex = null },
                    onDoubleClick = { index ->
                        // BottomSheet schließen und sofort den Vollbild-Viewer mit demselben Index öffnen
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
                    onDismiss = { index ->
                        // Vollbild schließen und das BottomSheet beim selben Index wieder öffnen!
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