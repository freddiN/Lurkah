package com.lurkah.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.util.Locale

data class ImgurResponse(val data: List<ImgurPost>, val success: Boolean)

data class ImgurAlbumResponse(val data: ImgurAlbumData, val success: Boolean)
data class ImgurAlbumData(val images: List<ImgurImage>?)

data class ImgurPost(
    val id: String,
    val title: String,
    @SerializedName("account_url") val accountUrl: String?,
    val images: List<ImgurImage>?,
    @SerializedName("tags") val rawTags: List<ImgurTag>? = emptyList(),
    val size: Long?,
    val link: String?,
    val mp4: String?,
    val type: String?,
    @SerializedName("is_album") val isAlbum: Boolean?,
    @SerializedName("images_count") val imagesCount: Int?,
    val cover: String?
) {
    val tags: List<String>
        get() = rawTags?.map { it.name } ?: emptyList()

    private val mainMedia: ImgurImage? get() = images?.firstOrNull()

    val coverId: String
        get() = cover ?: mainMedia?.id ?: id

    val mediaUrl: String?
        get() = mainMedia?.mp4 ?: mainMedia?.link ?: mp4 ?: link

    val thumbnailUrl: String
        get() = "https://i.imgur.com/${coverId}m.jpg"

    val isVideo: Boolean
        get() = (mainMedia?.type ?: type ?: "").startsWith("video/") || mediaUrl?.endsWith(".mp4") == true || mediaUrl?.endsWith(".gifv") == true

    val isGif: Boolean
        get() = mainMedia?.type == "image/gif" || type == "image/gif" || mediaUrl?.endsWith(".gif") == true

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

data class ImgurItemResponse(
    val data: ImgurPost
)

interface ImgurApiService {
    @GET("3/gallery/hot/viral/{page}")
    suspend fun getMostViral(
        @Header("Authorization") authHeader: String,
        @Path("page") page: Int = 0
    ): ImgurResponse

    @GET("3/album/{id}")
    suspend fun getAlbumDetails(
        @Header("Authorization") authHeader: String,
        @Path("id") albumId: String
    ): ImgurAlbumResponse

    @GET("3/gallery/{id}")
    suspend fun getGalleryItem(
        @Header("Authorization") authHeader: String,
        @Path("id") galleryId: String
    ): ImgurItemResponse
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val clientId = "Client-ID 546c25a59c58ad7"
    private val settingsManager = SettingsManager(application)

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.imgur.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: ImgurApiService = retrofit.create(ImgurApiService::class.java)

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

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun clearError() {
        errorMessage = null
    }

    var posts = mutableStateListOf<ImgurPost>()
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    private var currentPage = 0
    val albumImagesCache = mutableStateMapOf<String, List<ImgurImage>>()

    init {
        viewModelScope.launch {
            combine(blacklistedUsers, blacklistedTags) { users, tags ->
                Pair(users, tags)
            }.collect {
                loadViralPosts(isRefresh = true)
            }
        }
    }

    fun loadViralPosts(isRefresh: Boolean = false) {
        if (isLoadingMore && !isRefresh) return
        if (isRefresh && isRefreshing) return

        if (isRefresh) {
            isRefreshing = true
        } else {
            isLoadingMore = true
        }

        viewModelScope.launch {
            try {
                val pageToLoad = if (isRefresh) 0 else currentPage
                val response = api.getMostViral(authHeader = clientId, page = pageToLoad)

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
                        currentPage = 0
                    }

                    val existingIds = posts.map { it.id }.toSet()
                    val newUniquePosts = filtered.filter { !existingIds.contains(it.id) }

                    posts.addAll(newUniquePosts)
                    currentPage++
                } else {
                    errorMessage = "Failed to load viral posts."
                }
            } catch (e: HttpException) {
                e.printStackTrace()
                errorMessage = if (e.code() == 429) {
                    "Rate limit reached. Please wait a bit before requesting more posts."
                } else {
                    "HTTP Error: ${e.code()} - ${e.message()}"
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

    fun loadFullAlbumDetails(postId: String, post: ImgurPost? = null) {
        val totalCount = post?.imagesCount ?: post?.images?.size ?: 0
        val cachedCount = albumImagesCache[postId]?.size ?: 0

        // Wenn bereits alle Bilder im Cache liegen, kein Re-Fetch nötig
        if (albumImagesCache.containsKey(postId) && cachedCount >= totalCount && totalCount > 0) {
            return
        }

        viewModelScope.launch {
            try {
                // Ruft den echten Album-Endpunkt (3/album/{id}) auf, der ALLE Bilder liefert
                val response = api.getAlbumDetails(authHeader = clientId, albumId = postId)
                if (response.success && !response.data.images.isNullOrEmpty()) {
                    albumImagesCache[postId] = response.data.images!!
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setDarkMode(enabled) }
    }

    fun toggleAutoPlayVideos(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setAutoPlayVideos(enabled) }
    }

    fun toggleAutoReplay(enabled: Boolean) {
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
}