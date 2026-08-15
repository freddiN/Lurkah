package com.viralgur.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        private val BLACKLISTED_USERS_KEY = stringSetPreferencesKey("blacklisted_users")

        // NEU: Schritt 1 - Der Schlüssel für die Tags
        private val BLACKLISTED_TAGS_KEY = stringSetPreferencesKey("blacklisted_tags")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: true
    }

    val blacklistedUsers: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[BLACKLISTED_USERS_KEY] ?: emptySet()
    }

    // NEU: Schritt 2 - Die Tags auslesen
    val blacklistedTags: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[BLACKLISTED_TAGS_KEY] ?: emptySet()
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    suspend fun addBlacklistedUser(user: String) {
        val cleanUser = user.trim().removePrefix("@")
        if (cleanUser.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLISTED_USERS_KEY] ?: emptySet()
            preferences[BLACKLISTED_USERS_KEY] = current + cleanUser
        }
    }

    suspend fun removeBlacklistedUser(user: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLISTED_USERS_KEY] ?: emptySet()
            preferences[BLACKLISTED_USERS_KEY] = current - user
        }
    }

    // NEU: Schritt 3 - Einen Tag hinzufügen
    suspend fun addBlacklistedTag(tag: String) {
        val cleanTag = tag.trim().removePrefix("#") // Entfernt sicherheitshalber ein #
        if (cleanTag.isBlank()) return
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLISTED_TAGS_KEY] ?: emptySet()
            preferences[BLACKLISTED_TAGS_KEY] = current + cleanTag
        }
    }

    // NEU: Schritt 4 - Einen Tag entfernen
    suspend fun removeBlacklistedTag(tag: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLISTED_TAGS_KEY] ?: emptySet()
            preferences[BLACKLISTED_TAGS_KEY] = current - tag
        }
    }
}