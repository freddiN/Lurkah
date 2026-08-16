package com.lurkah.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
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

data class ImgurAlbumResponse(val data: ImgurAlbumData, val success: Boolean)
data class ImgurAlbumData(val images: List<ImgurImage>?)

data class ImgurPost(
    val id: String,
    val title: String,
    @SerializedName("account_url") val accountUrl: String?,
    val images: List<ImgurImage>?,
    @SerializedName("tags") val rawTags: List<ImgurTag>? = emptyList(),
    val size: Long?
) {
    val tags: List<String>
        get() = rawTags?.map { it.name } ?: emptyList()

    private val mainMedia: ImgurImage? get() = images?.firstOrNull()

    val mediaUrl: String?
        get() = mainMedia?.mp4 ?: mainMedia?.link ?: if (images == null) "https://i.imgur.com/$id.mp4" else null

    val thumbnailUrl: String
        get() = "https://i.imgur.com/${mainMedia?.id ?: id}m.jpg"

    val isVideo: Boolean
        get() = (mainMedia?.type ?: "").startsWith("video/") || mediaUrl?.endsWith(".mp4") == true || mediaUrl?.endsWith(".gifv") == true

    val isGif: Boolean
        get() = mainMedia?.type == "image/gif" || mediaUrl?.endsWith(".gif") == true

    val sizeInBytes: Long
        get() = mainMedia?.size ?: size ?: 0L

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

data class ImgurTag(
    val name: String
)

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

    @GET("3/album/{id}")
    suspend fun getAlbumDetails(
        @Header("Authorization") authHeader: String,
        @Path("id") albumId: String
    ): ImgurAlbumResponse
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val clientId = "Client-ID 546c25a59c58ad7"
    private val settingsManager = SettingsManager(application)

    // Retrofit Setup
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.imgur.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: ImgurApiService = retrofit.create(ImgurApiService::class.java)

    // Exposed Settings Flows
    val isDarkMode: StateFlow<Boolean> = settingsManager.isDarkMode
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val autoPlayVideos: StateFlow<Boolean> = settingsManager.autoPlayVideos
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val autoReplay: StateFlow<Boolean> = settingsManager.autoReplay
        .stateIn(viewModelScope, SharingStarted.Lazily, true)

    val blacklistedUsers: StateFlow<Set<String>> = settingsManager.blacklistedUsers
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    val blacklistedTags: StateFlow<Set<String>> = settingsManager.blacklistedTags
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    // UI States
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        errorMessage = null
    }

    var posts = mutableStateListOf<ImgurPost>()
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
        viewModelScope.launch {
            blacklistedUsers.collect {
                loadViralPosts(isRefresh = true)
            }
        }
        viewModelScope.launch {
            blacklistedTags.collect {
                loadViralPosts(isRefresh = true)
            }
        }
    }

    fun loadViralPosts(isRefresh: Boolean = false) {
        if (isLoadingMore && !isRefresh) return

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
                    val currentBlacklist = blacklistedUsers.value.map { it.lowercase() }
                    val currentBlacklistTags = blacklistedTags.value.map { it.lowercase() }

                    val filtered = response.data.filter { post ->
                        val author = post.accountUrl?.lowercase() ?: ""
                        val hasBlockedTag = post.tags.any { currentBlacklistTags.contains(it.lowercase()) }

                        !currentBlacklist.contains(author) && !hasBlockedTag && post.mediaUrl != null
                    }

                    if (isRefresh) {
                        posts.clear()
                    }
                    val existingIds = posts.map { it.id }.toSet()
                    val newUniquePosts = filtered.filter { !existingIds.contains(it.id) }

                    posts.addAll(newUniquePosts)
                    currentPage++
                } else {
                    errorMessage = "Failed to load viral posts."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "Network error: ${e.localizedMessage ?: "Please check your connection."}"
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
                errorMessage = "Could not load comments."
            } finally {
                isLoadingComments = false
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setDarkMode(enabled) }
    }

    fun toggleAutoPlayVideos(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setAutoPlayVideos(enabled) }
    }

    fun toggleAutoReplay(enabled: Boolean) { // <--- Hier umbenannt
        viewModelScope.launch { settingsManager.setAutoReplay(enabled) }
    }

    fun addBlacklistUser(username: String) {
        viewModelScope.launch { settingsManager.addBlacklistedUser(username) }
    }

    fun removeBlacklistUser(username: String) {
        viewModelScope.launch { settingsManager.removeBlacklistedUser(username) }
    }

    fun addBlacklistTag(tag: String) {
        viewModelScope.launch { settingsManager.addBlacklistedTag(tag) }
    }

    fun removeBlacklistTag(tag: String) {
        viewModelScope.launch { settingsManager.removeBlacklistedTag(tag) }
    }

    val albumImagesCache = androidx.compose.runtime.mutableStateMapOf<String, List<ImgurImage>>()

    fun loadFullAlbumDetails(postId: String) {
        if (albumImagesCache.containsKey(postId)) return

        viewModelScope.launch {
            try {
                val response = api.getAlbumDetails(authHeader = clientId, albumId = postId)
                if (response.success && !response.data.images.isNullOrEmpty()) {
                    // Warnung behoben: Kein '!!' notwendig, da durch isNullOrEmpty() bereits als non-null smart-gecastet
                    albumImagesCache[postId] = response.data.images
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}