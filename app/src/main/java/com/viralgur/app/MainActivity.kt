package com.viralgur.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

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
}

data class ImgurImage(val id: String, val link: String, val mp4: String?, val type: String?)

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
    // Öffentliche Client-ID (Flameshot OpenSource Fallback)
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

    var isLoadingComments by mutableStateOf(false)
        private set

    init {
        loadViralPosts()
    }

    fun loadViralPosts() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                val response = api.getMostViral(authHeader = clientId)
                if (response.success) {
                    val filtered = response.data.filter { post ->
                        val author = post.accountUrl?.lowercase() ?: ""
                        !blacklistedAccounts.map { it.lowercase() }.contains(author) && post.mediaUrl != null
                    }
                    posts.clear()
                    posts.addAll(filtered)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isRefreshing = false
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
            loadViralPosts()
        }
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

    val pullToRefreshState = rememberPullToRefreshState()

    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) { viewModel.loadViralPosts() }
    }

    LaunchedEffect(viewModel.isRefreshing) {
        if (!viewModel.isRefreshing) pullToRefreshState.endRefresh()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Viralgur - Most Viral") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
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
fun SmartMediaCard(post: ImgurPost, onClick: () -> Unit, onAccountClick: (String) -> Unit) {
    var isVisibleOnScreen by remember { mutableStateOf(false) }

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
                if (post.isVideo) {
                    Image(
                        painter = rememberAsyncImagePainter(post.thumbnailUrl),
                        contentDescription = post.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    DisposableEffect(post.id) {
                        isVisibleOnScreen = true
                        onDispose { isVisibleOnScreen = false }
                    }

                    if (isVisibleOnScreen && post.mediaUrl != null) {
                        VideoPlayer(videoUrl = post.mediaUrl!!, isMuted = true, modifier = Modifier.fillMaxSize())
                    }
                } else {
                    AsyncImage(
                        model = post.mediaUrl,
                        contentDescription = post.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
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
        LazyColumn(modifier = Modifier.padding(horizontal = 16.dp)) {
            item {
                Text(text = post.title, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                if (post.isVideo && post.mediaUrl != null) {
                    VideoPlayer(videoUrl = post.mediaUrl!!, isMuted = false, modifier = Modifier.fillMaxWidth().height(250.dp))
                } else {
                    AsyncImage(model = post.mediaUrl, contentDescription = post.title, modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Kommentare", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            if (viewModel.isLoadingComments) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(viewModel.selectedPostComments) { comment ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(text = comment.author, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text(text = comment.comment, style = MaterialTheme.typography.bodyMedium)
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
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = modifier
    )
}
