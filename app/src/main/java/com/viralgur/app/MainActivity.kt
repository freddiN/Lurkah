package com.viralgur.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
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
            if (sizeInBytes <= 0) return "unbekannt"
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
            else -> "🖼️ BILD"
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ImgurFeedScreen()
                }
            }
        }
    }
}

// --- UI COMPONENTS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImgurFeedScreen(viewModel: ImgurViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    var selectedPost by remember { mutableStateOf<ImgurPost?>(null) }
    var accountToBlacklist by remember { mutableStateOf<String?>(null) }
    var showBlacklistDialog by remember { mutableStateOf(false) }

    val pullToRefreshState = rememberPullToRefreshState()
    val gridState = rememberLazyGridState()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) { viewModel.loadViralPosts(isRefresh = true) }
    }

    LaunchedEffect(viewModel.isRefreshing) {
        if (!viewModel.isRefreshing) pullToRefreshState.endRefresh()
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !viewModel.isLoadingMore && !viewModel.isRefreshing) {
            viewModel.loadViralPosts(isRefresh = false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Viralgur - Most Viral") },
                actions = {
                    TextButton(onClick = { showBlacklistDialog = true }) {
                        Text("🚫 (${viewModel.blacklistedAccounts.size})")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.posts) { post ->
                    SmartMediaCard(
                        post = post,
                        onClick = { selectedPost = post },
                        onAccountClick = { author -> accountToBlacklist = author }
                    )
                }

                if (viewModel.isLoadingMore) {
                    item {
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

            accountToBlacklist?.let { author ->
                AlertDialog(
                    onDismissRequest = { accountToBlacklist = null },
                    title = { Text("Account blockieren?") },
                    text = { Text("Möchtest du '$author' zur Blacklist hinzufügen?") },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.addAccountToBlacklist(author)
                            accountToBlacklist = null
                        }) { Text("Blockieren") }
                    },
                    dismissButton = {
                        TextButton(onClick = { accountToBlacklist = null }) { Text("Abbrechen") }
                    }
                )
            }

            if (showBlacklistDialog) {
                ManageBlacklistDialog(
                    viewModel = viewModel,
                    onDismiss = { showBlacklistDialog = false }
                )
            }

            selectedPost?.let { post ->
                PostDetailBottomSheet(
                    post = post,
                    viewModel = viewModel,
                    onDismiss = { selectedPost = null }
                )
            }
        }
    }
}

@Composable
fun ManageBlacklistDialog(viewModel: ImgurViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Geblockte Accounts") },
        text = {
            if (viewModel.blacklistedAccounts.isEmpty()) {
                Text("Deine Blacklist ist aktuell leer.")
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
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

@Composable
fun SmartMediaCard(post: ImgurPost, onClick: () -> Unit, onAccountClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                // 1. Thumbnail / Vorschaubild (Kein Autoplay)
                AsyncImage(
                    model = post.thumbnailUrl,
                    contentDescription = post.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // 2. Play-Button Badge für Videos
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

                // 3. Typ- & Größen-Badge oben links auf der Karte
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailBottomSheet(post: ImgurPost, viewModel: ImgurViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(post.id) { viewModel.loadCommentsForPost(post.id) }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight(0.9f)) {
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

                // Größen- und Typanzeige in der Detailansicht
                Text(
                    text = "${post.typeLabel} • Dateigröße: ${post.formattedSize}",
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
                        VideoPlayer(
                            videoUrl = post.mediaUrl!!, 
                            isMuted = false, 
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                        )
                    } else {
                        AsyncImage(
                            model = post.mediaUrl, 
                            contentDescription = post.title, 
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 350.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Kommentare", 
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
                        text = "Keine Kommentare vorhanden.", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(viewModel.selectedPostComments) { comment ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = comment.author, 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = comment.comment, 
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(videoUrl: String, isMuted: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = if (isMuted) 0f else 1f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(videoUrl) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true // Mit Video-Play/Pause-Steuerung im Detailmenü
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier
    )
}
