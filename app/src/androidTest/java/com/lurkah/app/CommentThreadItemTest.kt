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
            CommentThreadItem(comment = sampleThread(), depth = 0)
        }

        composeTestRule.onNodeWithText("Top comment").assertIsDisplayed()
        composeTestRule.onNodeWithText("@user1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nested reply").assertIsDisplayed()
        composeTestRule.onNodeWithText("@user2").assertIsDisplayed()
    }

    @Test
    fun commentThread_collapseHidesReplies() {
        composeTestRule.setContent {
            CommentThreadItem(comment = sampleThread(), depth = 0)
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
            CommentThreadItem(comment = leaf, depth = 0)
        }

        composeTestRule.onNodeWithText("Lonely comment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show 1 replies").assertDoesNotExist()
        composeTestRule.onNodeWithText("Hide 1 replies").assertDoesNotExist()
    }

    @Test
    fun commentThread_showsPoints() {
        composeTestRule.setContent {
            CommentThreadItem(comment = sampleThread(), depth = 0)
        }

        // points 9.0 -> "▲ 9"
        composeTestRule.onNodeWithText("▲ 9").assertIsDisplayed()
    }
}
