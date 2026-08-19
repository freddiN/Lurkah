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