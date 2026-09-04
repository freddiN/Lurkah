package com.lurkah.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaUrlTest {

    @Test
    fun extractsNumberedGiphySubdomain() {
        val urls = extractMediaUrls("gif https://media4.giphy.com/media/abc123/giphy.webp ok")
        assertEquals(listOf("https://media4.giphy.com/media/abc123/giphy.webp"), urls)
    }

    @Test
    fun stripsTrailingPunctuation() {
        val urls = extractMediaUrls("see https://i.imgur.com/x.jpg, nice)")
        assertEquals(listOf("https://i.imgur.com/x.jpg"), urls)
    }

    @Test
    fun extractsGiphyPageLink() {
        val urls = extractMediaUrls("See https://giphy.com/gifs/funny-cat-abc123")
        assertEquals(listOf("https://giphy.com/gifs/funny-cat-abc123"), urls)
    }

    @Test
    fun normalizesGiphyWebpToMp4() {
        assertEquals(
            "https://media4.giphy.com/media/abc123/giphy.mp4",
            normalizeCommentMediaUrl("https://media4.giphy.com/media/abc123/giphy.webp")
        )
    }

    @Test
    fun leavesOtherUrlsUntouched() {
        assertEquals("https://i.imgur.com/x.jpg", normalizeCommentMediaUrl("https://i.imgur.com/x.jpg"))
        assertEquals(
            "https://media.giphy.com/media/a/giphy.gif",
            normalizeCommentMediaUrl("https://media.giphy.com/media/a/giphy.gif")
        )
    }
}
