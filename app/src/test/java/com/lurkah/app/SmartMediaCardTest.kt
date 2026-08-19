package com.lurkah.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SmartMediaCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testClickOnAuthorAndTag_triggersCallbacks() {
        // Variablen, in denen wir das Ergebnis des Klicks speichern
        var clickedAuthor: String? = null
        var clickedTag: String? = null

        // Ein Dummy-Post, den wir an die Card übergeben
        val dummyPost = ImgurPost(
            id = "test1",
            title = "Test Bild",
            accountUrl = "test_creator",
            images = null,
            rawTags = listOf(ImgurTag("funny")),
            size = 500L,
            link = "https://i.imgur.com/test.jpg",
            mp4 = null,
            type = "image/jpeg",
            isAlbum = false,
            imagesCount = 1,
            cover = null
        )

        composeTestRule.setContent {
            SmartMediaCard(
                post = dummyPost,
                onClick = { },
                onAccountClick = { clickedAuthor = it }, // Speichert den geklickten Autor
                onTagClick = { clickedTag = it }         // Speichert den geklickten Tag
            )
        }

        // 1. Simuliere den Klick auf den Autor-Text
        composeTestRule.onNodeWithText("@test_creator").performClick()
        // Prüfe, ob die Variable durch den Callback aktualisiert wurde
        assertEquals("test_creator", clickedAuthor)

        // 2. Simuliere den Klick auf den Tag
        composeTestRule.onNodeWithText("#funny").performClick()
        // Prüfe, ob der Tag korrekt erfasst wurde
        assertEquals("funny", clickedTag)
    }
}