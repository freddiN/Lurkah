@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@file:Suppress("UnstableApiUsage")

package com.lurkah.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility

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
    var tagToBlock by remember { mutableStateOf<String?>(null) }

    val gridState = rememberLazyGridState()
    val pullToRefreshState = rememberPullToRefreshState()
    val coroutineScope = rememberCoroutineScope()

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
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            coroutineScope.launch { gridState.animateScrollToItem(0) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Lurkah Logo",
                            tint = Color(0xFF1BB76E),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lurkah",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadViralPosts(isRefresh = true) }
                    ) {
                        Text(
                            text = "↻",
                            fontSize = 18.sp,
                            color = Color(0xFF1BB76E)
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Text(
                            text = "⚙",
                            fontSize = 18.sp,
                            color = Color(0xFF1BB76E)
                        )
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
                        onAccountClick = { author -> userToBlock = author },
                        onTagClick = { tag -> tagToBlock = tag }
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

        tagToBlock?.let { tag ->
            AlertDialog(
                onDismissRequest = { tagToBlock = null },
                title = { Text("Block Tag?") },
                text = { Text("Do you want to add '#$tag' to your blocked list? Posts with this tag will be hidden.") },
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

        fullScreenFeedIndex?.let { startIndex ->
            FullScreenFeedViewer(
                initialIndex = startIndex,
                viewModel = viewModel,
                autoReplay = autoReplay,
                gridState = gridState,
                onDismiss = { fullScreenFeedIndex = null }
            )
        }
    }
}

@Composable
fun SmartMediaCard(
    post: ImgurPost,
    onClick: () -> Unit,
    onAccountClick: (String) -> Unit,
    onTagClick: (String) -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val context = LocalContext.current

    val totalCount = post.imagesCount ?: post.images?.size ?: 1
    val labelText = if (post.isAlbum == true && totalCount > 1) {
        "📁 ALBUM ($totalCount) • ${post.formattedSize}"
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

            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            post.accountUrl?.let { author ->
                Text(
                    text = "@$author",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                        .clickable { onAccountClick(author) }
                )
            }

            if (post.tags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(post.tags) { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable { onTagClick(tag) }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenFeedViewer(
    initialIndex: Int,
    viewModel: MainViewModel,
    autoReplay: Boolean,
    gridState: LazyGridState,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) {
        onDismiss()
    }

    var expandedAlbumImage by remember { mutableStateOf<ImgurImage?>(null) }

    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (viewModel.posts.size - 1).coerceAtLeast(0)),
        pageCount = { viewModel.posts.size }
    )
    val autoPlayVideos by viewModel.autoPlayVideos.collectAsState()

    LaunchedEffect(pagerState.currentPage) {
        gridState.scrollToItem(pagerState.currentPage)

        val currentPost = viewModel.posts.getOrNull(pagerState.currentPage)
        currentPost?.let { post ->
            if (post.isAlbum == true || (post.images?.size ?: 1) > 1 || post.images.isNullOrEmpty()) {
                viewModel.loadFullAlbumDetails(post.id, post)
            }
        }

        if (pagerState.currentPage >= viewModel.posts.size - 3) {
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
                val displayItems = remember(cachedImages, post.images, post.mediaUrl, post.cover) {
                    val rawList = when {
                        !cachedImages.isNullOrEmpty() -> cachedImages
                        !post.images.isNullOrEmpty() -> post.images
                        post.mediaUrl != null -> listOf(
                            ImgurImage(
                                id = post.coverId,
                                link = post.mediaUrl!!,
                                mp4 = if (post.isVideo) post.mediaUrl else null,
                                type = if (post.isVideo) "video/mp4" else "image/jpeg",
                                size = post.sizeInBytes
                            )
                        )
                        else -> emptyList()
                    }

                    rawList.map { img ->
                        val fixedLink = when {
                            img.link.endsWith(".gifv") -> img.link.removeSuffix(".gifv") + ".mp4"
                            img.link.isBlank() && post.mediaUrl != null -> post.mediaUrl!!
                            else -> img.link
                        }
                        val fixedMp4 = img.mp4.takeIf { !it.isNullOrBlank() } ?: if (fixedLink.endsWith(".mp4")) fixedLink else null
                        img.copy(link = fixedLink, mp4 = fixedMp4)
                    }.filter { it.link.isNotBlank() }
                }

                if (displayItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (displayItems.size == 1) {
                    val img = displayItems.first()
                    val itemUrl = img.mp4.takeIf { !it.isNullOrBlank() } ?: img.link
                    val isItemVideo = (img.type ?: "").startsWith("video/") || itemUrl.endsWith(".mp4")

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp, bottom = 64.dp)
                    ) {
                        Text(
                            text = post.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        if (isItemVideo) {
                            ComposeVideoPlayer(
                                url = itemUrl,
                                isCurrentPage = isCurrentPage,
                                isFirstItem = true,
                                autoReplay = autoReplay,
                                autoPlayVideos = autoPlayVideos,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        } else {
                            ZoomableMediaViewer(
                                url = itemUrl,
                                contentDesc = post.title,
                                isFullScreen = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
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
                            val itemUrl = img.mp4.takeIf { !it.isNullOrBlank() } ?: img.link
                            val isItemVideo = (img.type ?: "").startsWith("video/") || itemUrl.endsWith(".mp4")
                            val isItemGif = img.type == "image/gif" || itemUrl.endsWith(".gif")

                            val itemTypeLabel = when {
                                isItemVideo -> "🎥 MP4"
                                isItemGif -> "🎞️ GIF"
                                else -> "🖼️ IMAGE"
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (isItemVideo) {
                                    ComposeVideoPlayer(
                                        url = itemUrl,
                                        isCurrentPage = isCurrentPage,
                                        isFirstItem = index == 0,
                                        autoReplay = autoReplay,
                                        autoPlayVideos = autoPlayVideos,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 200.dp, max = 800.dp)
                                    )
                                } else {
                                    ZoomableMediaViewer(
                                        url = itemUrl,
                                        contentDesc = post.title,
                                        isFullScreen = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedAlbumImage = img }
                                    )
                                }

                                Text(
                                    text = "${index + 1} / ${displayItems.size} • $itemTypeLabel",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(top = 8.dp)
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

        expandedAlbumImage?.let { img ->
            val itemUrl = img.mp4.takeIf { !it.isNullOrBlank() } ?: img.link

            val coroutineScope = rememberCoroutineScope()
            val offsetY = remember { Animatable(0f) }

            val backgroundAlpha = (1f - (abs(offsetY.value) / 1000f)).coerceIn(0f, 1f)

            Dialog(
                onDismissRequest = { expandedAlbumImage = null },
                properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = backgroundAlpha))
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (abs(offsetY.value) > 300f) {
                                        expandedAlbumImage = null
                                    } else {
                                        coroutineScope.launch { offsetY.animateTo(0f) }
                                    }
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    coroutineScope.launch { offsetY.snapTo(offsetY.value + dragAmount) }
                                }
                            )
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    ) {
                        ZoomableMediaViewer(
                            url = itemUrl,
                            contentDesc = "Expanded Image",
                            isFullScreen = true,
                            modifier = Modifier.fillMaxSize()
                        )

                        IconButton(
                            onClick = { expandedAlbumImage = null },
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
        }
    }
}