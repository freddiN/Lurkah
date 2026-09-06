package com.lurkah.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class CommentThreadItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleThread() = ImgurComment(
        id = 1, imageId = "abc", comment = "Top comment",
        author = "user1", ups = 10, downs = 1, points = 9.0,
        datetime = 1700000000, parentId = 0, deleted = false,
        children = listOf(
            ImgurComment(
                id = 2, imageId = "abc", comment = "Nested reply",
                author = "user2", ups = 5, downs = 0, points = 5.0,
                datetime = 1700000100, parentId = 1, deleted = false,
                children = emptyList()
            )
        )
    )

    @Test
    fun commentThread_rendersParentAndChild() {
        composeTestRule.setContent {
            CommentThreadItem(comment = sampleThread(), depth = 0, onMediaClick = {}, onExternalLinkClick = {})
        }

        composeTestRule.onNodeWithText("Top comment").assertIsDisplayed()
        composeTestRule.onNodeWithText("@user1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nested reply").assertIsDisplayed()
        composeTestRule.onNodeWithText("@user2").assertIsDisplayed()
    }

    @Test
    fun commentThread_collapseHidesReplies() {
        composeTestRule.setContent {
            CommentThreadItem(comment = sampleThread(), depth = 0, onMediaClick = {}, onExternalLinkClick = {})
        }

        // Replies sind initial sichtbar (expanded = true)
        composeTestRule.onNodeWithText("Nested reply").assertIsDisplayed()

        // Einklappen
        composeTestRule.onNodeWithText("Hide 1 replies").performClick()

        composeTestRule.onNodeWithText("Show 1 replies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nested reply").assertDoesNotExist()
    }

    @Test
    fun commentThread_withoutChildren_showsNoToggle() {
        val leaf = ImgurComment(
            id = 3, imageId = "abc", comment = "Lonely comment",
            author = "solo", ups = 1, downs = 0, points = 1.0,
            datetime = 1, parentId = 0, deleted = false,
            children = emptyList()
        )

        composeTestRule.setContent {
            CommentThreadItem(comment = leaf, depth = 0, onMediaClick = {}, onExternalLinkClick = {})
        }

        composeTestRule.onNodeWithText("Lonely comment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show 1 replies").assertDoesNotExist()
        composeTestRule.onNodeWithText("Hide 1 replies").assertDoesNotExist()
    }

    @Test
    fun commentThread_showsPoints() {
        composeTestRule.setContent {
            CommentThreadItem(comment = sampleThread(), depth = 0, onMediaClick = {}, onExternalLinkClick = {})
        }

        // points 9.0 -> "▲ 9"
        composeTestRule.onNodeWithText("▲ 9").assertIsDisplayed()
    }

    @Test
    fun commentThread_rendersImgurLink() {
        val withLink = ImgurComment(
            id = 4, imageId = "abc", comment = "Look https://i.imgur.com/test.jpg nice",
            author = "linker", ups = 1, downs = 0, points = 1.0,
            datetime = 1, parentId = 0, deleted = false,
            children = emptyList()
        )

        composeTestRule.setContent {
            CommentThreadItem(comment = withLink, depth = 0, onMediaClick = {}, onExternalLinkClick = {})
        }

        composeTestRule.onNodeWithText("Look https://i.imgur.com/test.jpg nice", substring = true).assertIsDisplayed()
    }

    @Test
    fun commentThread_clickMediaLink_triggersCallback() {
        var clickedUrl: String? = null
        val withLink = ImgurComment(
            id = 5, imageId = "abc", comment = "Video https://i.imgur.com/test.mp4 here",
            author = "linker", ups = 1, downs = 0, points = 1.0,
            datetime = 1, parentId = 0, deleted = false,
            children = emptyList()
        )

        composeTestRule.setContent {
            CommentThreadItem(
                comment = withLink, depth = 0,
                onMediaClick = { clickedUrl = it },
                onExternalLinkClick = {}
            )
        }

        composeTestRule.onNodeWithText("Video https://i.imgur.com/test.mp4 here", substring = true).performClick()

        assert(clickedUrl == "https://i.imgur.com/test.mp4") { "Expected media callback, got $clickedUrl" }
    }

    @Test
    fun commentThread_clickGiphyLink_triggersMediaCallback() {
        var clickedUrl: String? = null
        val withLink = ImgurComment(
            id = 6, imageId = "abc", comment = "Giphy https://media.giphy.com/media/abc123/giphy.gif lol",
            author = "linker", ups = 1, downs = 0, points = 1.0,
            datetime = 1, parentId = 0, deleted = false,
            children = emptyList()
        )

        composeTestRule.setContent {
            CommentThreadItem(
                comment = withLink, depth = 0,
                onMediaClick = { clickedUrl = it },
                onExternalLinkClick = {}
            )
        }

        composeTestRule.onNodeWithText("Giphy https://media.giphy.com/media/abc123/giphy.gif lol", substring = true).performClick()

        assert(clickedUrl == "https://media.giphy.com/media/abc123/giphy.gif") { "Expected media callback, got $clickedUrl" }
    }

    @Test
    fun commentThread_clickGiphyPageLink_triggersExternalCallback() {
        var externalUrl: String? = null
        val withLink = ImgurComment(
            id = 7, imageId = "abc", comment = "See https://giphy.com/gifs/funny-cat-abc123",
            author = "linker", ups = 1, downs = 0, points = 1.0,
            datetime = 1, parentId = 0, deleted = false,
            children = emptyList()
        )

        composeTestRule.setContent {
            CommentThreadItem(
                comment = withLink, depth = 0,
                onMediaClick = {},
                onExternalLinkClick = { externalUrl = it }
            )
        }

        composeTestRule.onNodeWithText("See https://giphy.com/gifs/funny-cat-abc123", substring = true).performClick()

        assert(externalUrl == "https://giphy.com/gifs/funny-cat-abc123") { "Expected external callback, got $externalUrl" }
    }

    @Test
    fun commentThread_clickForeignImageLink_triggersExternalCallback() {
        var mediaUrl: String? = null
        var externalUrl: String? = null
        val withLink = ImgurComment(
            id = 8, imageId = "abc", comment = "Pic https://example.com/photo.jpg here",
            author = "linker", ups = 1, downs = 0, points = 1.0,
            datetime = 1, parentId = 0, deleted = false,
            children = emptyList()
        )

        composeTestRule.setContent {
            CommentThreadItem(
                comment = withLink, depth = 0,
                onMediaClick = { mediaUrl = it },
                onExternalLinkClick = { externalUrl = it }
            )
        }

        composeTestRule.onNodeWithText("Pic https://example.com/photo.jpg here", substring = true).performClick()

        assert(mediaUrl == null) { "Expected no media callback, got $mediaUrl" }
        assert(externalUrl == "https://example.com/photo.jpg") { "Expected external callback, got $externalUrl" }
    }
}
