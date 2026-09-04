package com.lurkah.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsScreen_displaysBlockedUsersAndTags() {
        val testUsers = setOf("troll_user")
        val testTags = setOf("boring_tag")

        composeTestRule.setContent {
            SettingsScreen(
                isDarkMode = true,
                autoPlayVideos = true,
                autoReplay = false,
                blacklistedUsers = testUsers,
                blacklistedTags = testTags,
                onDarkModeToggle = {},
                onAutoPlayVideosToggle = {},
                onAutoReplayToggle = {},
                onRemoveBlacklistUser = {},
                onRemoveBlacklistTag = {}
            )
        }

        // Überprüfen, ob die statischen Titel aus dem UI-Code gerendert werden
        composeTestRule.onNodeWithText("Dark Mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Blocked Accounts").assertIsDisplayed()

        // Überprüfen, ob die dynamischen, blockierten Werte korrekt formatiert sind
        composeTestRule.onNodeWithText("@troll_user").assertIsDisplayed()
        composeTestRule.onNodeWithText("#boring_tag").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_clickRemoveUser_triggersCallback() {
        var removedUser: String? = null

        composeTestRule.setContent {
            SettingsScreen(
                isDarkMode = true,
                autoPlayVideos = true,
                autoReplay = true,
                blacklistedUsers = setOf("troll_account"),
                blacklistedTags = emptySet(),
                onDarkModeToggle = {},
                onAutoPlayVideosToggle = {},
                onAutoReplayToggle = {},
                onRemoveBlacklistUser = { removedUser = it },
                onRemoveBlacklistTag = {}
            )
        }

        // Wir prüfen, ob der User in der Liste gerendert wird
        composeTestRule.onNodeWithText("@troll_account").assertIsDisplayed()

        // Da "−" der Text deines Löschen-Buttons in der UI ist, suchen wir ihn und klicken ihn
        composeTestRule.onNodeWithText("−").performClick()

        // Wenn der Klick funktioniert hat, muss unsere Variable den String "troll_account" enthalten
        assertEquals("troll_account", removedUser)
    }
}