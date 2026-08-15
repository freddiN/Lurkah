@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.viralgur.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.util.Locale

// --- DATA MODELS ---
data class ImgurResponse(val data: List<ImgurPost>, val success: Boolean)
data class ImgurCommentsResponse(val data: List<ImgurComment>, val success: Boolean)

data class ImgurPost(
    val id: String,
    val title: String,
    @SerializedName("account_url") val accountUrl: String?,
    val images: List<ImgurImage>?
) {
    private val mainMedia: ImgurImage? get() = images?.firstOrNull()

    val mediaUrl: String?
        get() = mainMedia?.mp4 ?: mainMedia?.link ?: if (images == null) "https://i.imgur.com/$id.mp4" else null

    val thumbnailUrl: String
        get() = "https://i.imgur.com/${mainMedia?.id ?: id}m.jpg"

    val isVideo: Boolean
        get() = (mainMedia?.type ?: "").startsWith("video/") || mediaUrl?.endsWith(".mp4") == true

    val isGif: Boolean
        get() = mainMedia?.type == "image/gif" || mediaUrl?.endsWith(".gif") == true

    val sizeInBytes: Long
        get() = mainMedia?.size ?: 0L

    val formattedSize: String
        get() {
            if (sizeInBytes <= 0) return "unknown"
            val kb = sizeInBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB", mb)
            } else {
                String.format(Locale.US, "%.0f KB", kb)
            }
        }

    val typeLabel: String
        get() = when {
            isVideo -> "🎥 MP4"
            isGif -> "🎞️ GIF"
            else -> "🖼️ IMAGE"
        }
}

data class ImgurImage(
    val id: String,
    val link: String,
    val mp4: String?,
    val type: String?,
    val size: Long?
)

data class ImgurComment(
    val id: Long,
    val comment: String,
    val author: String,
    val ups: Int,
    val downs: Int,
    val children: List<ImgurComment>? = emptyList()
)

// --- API SERVICE ---
interface ImgurApiService {
    @GET("3/gallery/hot/viral/{page}")
    suspend fun getMostViral(
        @Header("Authorization") authHeader: String,
        @Path("page") page: Int = 0
    ): ImgurResponse

    @GET("3/gallery/{galleryHash}/comments/best")
    suspend fun getComments(
        @Header("Authorization") authHeader: String,
        @Path("galleryHash") galleryHash: String
    ): ImgurCommentsResponse
}

// --- VIEWMODEL ---
class ImgurViewModel : ViewModel() {
    private val clientId = "Client-ID 546c25a59c58ad7"

    private val api: ImgurApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.imgur.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ImgurApiService::class.java)
    }

    var posts = mutableStateListOf<ImgurPost>()
        private set

    var blacklistedAccounts = mutableStateListOf<String>()
        private set

    var selectedPostComments = mutableStateListOf<ImgurComment>()
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    var isLoadingComments by mutableStateOf(false)
        private set

    private var currentPage = 0

    init {
        loadViralPosts(isRefresh = true)
    }

    fun loadViralPosts(isRefresh: Boolean = false) {
        if (isLoadingMore) return

        viewModelScope.launch {
            if (isRefresh) {
                isRefreshing = true
                currentPage = 0
            } else {
                isLoadingMore = true
            }

            try {
                val response = api.getMostViral(authHeader = clientId, page = currentPage)
                if (response.success) {
                    val filtered = response.data.filter { post ->
                        val author = post.accountUrl?.lowercase() ?: ""
                        !blacklistedAccounts.map { it.lowercase() }.contains(author) && post.mediaUrl != null
                    }

                    if (isRefresh) {
                        posts.clear()
                    }
                    posts.addAll(filtered)
                    currentPage++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRefreshing = false
                isLoadingMore = false
            }
        }
    }

    fun loadCommentsForPost(postId: String) {
        viewModelScope.launch {
            isLoadingComments = true
            selectedPostComments.clear()
            try {
                val response = api.getComments(authHeader = clientId, galleryHash = postId)
                if (response.success) {
                    selectedPostComments.addAll(response.data)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingComments = false
            }
        }
    }

    fun addAccountToBlacklist(accountName: String) {
        if (accountName.isNotBlank() && !blacklistedAccounts.contains(accountName)) {
            blacklistedAccounts.add(accountName)
            loadViralPosts(isRefresh = true)
        }
    }

    fun removeAccountFromBlacklist(accountName: String) {
        blacklistedAccounts.remove(accountName)
        loadViralPosts(isRefresh = true)
    }
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var isDarkMode by remember { mutableStateOf(true) }
            val colorScheme = if (isDarkMode) darkColorScheme() else lightColorScheme()

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ImgurFeedScreen(
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { isDarkMode = !isDarkMode }
                    )
                }
            }
        }
    }
}

// --- UI COMPONENTS ---
@Composable
fun ImgurFeedScreen(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    viewModel: ImgurViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    var selectedPostIndex by remember { mutableStateOf<Int?>(null) }
    var fullScreenPostIndex by remember { mutableStateOf<Int?>(null) }
    var accountToBlacklist by remember { mutableStateOf<String?>(null) }
    var showBlacklistDialog by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()

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
                    IconButton(onClick = onToggleDarkMode) {
                        Text(
                            text = if (isDarkMode) "☀️" else "🌙",
                            fontSize = 18.sp
                        )
                    }
                    TextButton(onClick = { showBlacklistDialog = true }) {
                        Text("🚫 (${viewModel.blacklistedAccounts.size})")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = { viewModel.loadViralPosts(isRefresh = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                        onAccountClick = { author -> accountToBlacklist = author }
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

            accountToBlacklist?.let { author ->
                AlertDialog(
                    onDismissRequest = { accountToBlacklist = null },
                    title = { Text("Block Account?") },
                    text = { Text("Do you want to add '$author' to your blacklist?") },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.addAccountToBlacklist(author)
                            accountToBlacklist = null
                        }) { Text("Block") }
                    },
                    dismissButton = {
                        TextButton(onClick = { accountToBlacklist = null }) { Text("Cancel") }
                    }
                )
            }

            if (showBlacklistDialog) {
                ManageBlacklistDialog(
                    viewModel = viewModel,
                    onDismiss = { showBlacklistDialog = false }
                )
            }

            selectedPostIndex?.let { initialIndex ->
                PostDetailBottomSheet(
                    initialIndex = initialIndex,
                    viewModel = viewModel,
                    onDismiss = { selectedPostIndex = null },
                    onDoubleClick = { index -> fullScreenPostIndex = index }
                )
            }

            fullScreenPostIndex?.let { initialIndex ->
                FullScreenMediaViewer(
                    initialIndex = initialIndex,
                    posts = viewModel.posts,
                    onDismiss = { fullScreenPostIndex = null }
                )
            }
        }
    }
}

@Composable
fun ManageBlacklistDialog(viewModel: ImgurViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked Accounts") },
        text = {
            if (viewModel.blacklistedAccounts.isEmpty()) {
                Text("Your blacklist is currently empty.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(viewModel.blacklistedAccounts) { account ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "@$account",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { viewModel.removeAccountFromBlacklist(account) }
                            ) {
                                Text(text = "❌", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
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
                                    showControls = false,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            AsyncImage(
                                model = post.mediaUrl,
                                contentDescription = post.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
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
    viewModel: ImgurViewModel,
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
            modifier = Modifier.fillMaxSize()
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
    modifier: Modifier = Modifier,
    showControls: Boolean = true,
    onDoubleClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = if (isMuted) 0f else 1f
            pauseAtEndOfMediaItems = true
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = showControls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
