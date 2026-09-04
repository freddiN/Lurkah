package com.lurkah.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ImgurPostTest {

    @Test
    fun testTypeLabel_forVideo() {
        // Bereite einen Post vor, der als MP4 markiert ist
        val videoPost = ImgurPost(
            id = "1", title = "Video Test", accountUrl = null, images = null,
            size = 1024, link = "https://i.imgur.com/test.mp4", mp4 = "https://i.imgur.com/test.mp4",
            type = "video/mp4", isAlbum = false, imagesCount = 1, cover = null
        )

        // Das Label muss "🎥 MP4" sein
        assertEquals("🎥 MP4", videoPost.typeLabel)
    }

    @Test
    fun testTypeLabel_forGif() {
        val gifPost = ImgurPost(
            id = "2", title = "Gif Test", accountUrl = null, images = null,
            size = 2048, link = "https://i.imgur.com/test.gif", mp4 = null,
            type = "image/gif", isAlbum = false, imagesCount = 1, cover = null
        )

        assertEquals("🎞️ GIF", gifPost.typeLabel)
    }

    @Test
    fun testTypeLabel_forImage() {
        val imagePost = ImgurPost(
            id = "3", title = "Image Test", accountUrl = null, images = null,
            size = 2048, link = "https://i.imgur.com/test.jpg", mp4 = null,
            type = "image/jpeg", isAlbum = false, imagesCount = 1, cover = null
        )

        assertEquals("🖼️ IMAGE", imagePost.typeLabel)
    }

    @Test
    fun testIsVideo_viaGifvSuffix() {
        val gifvPost = ImgurPost(
            id = "4", title = "Gifv Test", accountUrl = null, images = null,
            size = null, link = "https://i.imgur.com/test.gifv", mp4 = null,
            type = null, isAlbum = false, imagesCount = 1, cover = null
        )

        assertEquals(true, gifvPost.isVideo)
        assertEquals("🎥 MP4", gifvPost.typeLabel)
    }

    @Test
    fun testFormattedSize_variants() {
        fun postWithSize(size: Long?) = ImgurPost(
            id = "s", title = "t", accountUrl = null, images = null,
            size = size, link = "https://i.imgur.com/t.jpg", mp4 = null,
            type = "image/jpeg", isAlbum = false, imagesCount = 1, cover = null
        )

        assertEquals("unknown", postWithSize(null).formattedSize)
        assertEquals("unknown", postWithSize(0L).formattedSize)
        // 500 KB
        assertEquals("500 KB", postWithSize(500 * 1024L).formattedSize)
        // 2.5 MB
        assertEquals("2.5 MB", postWithSize((2.5 * 1024 * 1024).toLong()).formattedSize)
    }

    @Test
    fun testThumbnailUrl_prefersCover() {
        val post = ImgurPost(
            id = "fallback", title = "t", accountUrl = null,
            images = listOf(ImgurImage(id = "img1", link = "https://i.imgur.com/img1.jpg", mp4 = null, type = "image/jpeg", size = 10L)),
            size = null, link = null, mp4 = null, type = null,
            isAlbum = true, imagesCount = 1, cover = "coverHash"
        )

        assertEquals("https://i.imgur.com/coverHashm.jpg", post.thumbnailUrl)
    }

    @Test
    fun testMediaUrl_prefersFirstImageMp4() {
        val post = ImgurPost(
            id = "m", title = "t", accountUrl = null,
            images = listOf(ImgurImage(id = "v", link = "https://i.imgur.com/v.jpg", mp4 = "https://i.imgur.com/v.mp4", type = "video/mp4", size = 10L)),
            size = null, link = "https://i.imgur.com/fallback.jpg", mp4 = null, type = null,
            isAlbum = false, imagesCount = 1, cover = null
        )

        assertEquals("https://i.imgur.com/v.mp4", post.mediaUrl)
    }

    @Test
    fun testTags_nullSafe() {
        val post = ImgurPost(
            id = "t", title = "t", accountUrl = null, images = null, rawTags = null,
            size = null, link = null, mp4 = null, type = null,
            isAlbum = false, imagesCount = 1, cover = null
        )

        assertEquals(emptyList<String>(), post.tags)
    }

    @Test
    fun testSizeInBytes_forAlbum() {
        // Ein Album addiert die Größe aller enthaltenen Bilder
        val image1 = ImgurImage(id = "1", link = "", mp4 = null, type = "image/jpeg", size = 1000L)
        val image2 = ImgurImage(id = "2", link = "", mp4 = null, type = "image/jpeg", size = 2000L)

        val albumPost = ImgurPost(
            id = "album1", title = "Album Test", accountUrl = null,
            images = listOf(image1, image2), size = null, link = null, mp4 = null,
            type = null, isAlbum = true, imagesCount = 2, cover = null
        )

        // Erwartete Größe ist 3000L
        assertEquals(3000L, albumPost.sizeInBytes)
    }
}