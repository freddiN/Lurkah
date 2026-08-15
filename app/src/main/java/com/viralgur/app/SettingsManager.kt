package com.viralgur.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_settings")

class SettingsManager(private val context: Context) {

    companion object {
        private val DARK_MODE_KEY = booleanPreferencesKey("is_dark_mode")
        private val BLACKLIST_KEY = stringSetPreferencesKey("blacklisted_tags")
    }

    val isDarkModeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: false
    }

    val blacklistFlow: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[BLACKLIST_KEY] ?: emptySet()
    }

    suspend fun saveDarkMode(isDark: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = isDark
        }
    }

    suspend fun addToBlacklist(tag: String) {
        val formattedTag = tag.lowercase().trim()
        if (formattedTag.isNotEmpty()) {
            context.dataStore.edit { preferences ->
                val current = preferences[BLACKLIST_KEY] ?: emptySet()
                preferences[BLACKLIST_KEY] = current + formattedTag
            }
        }
    }

    suspend fun removeFromBlacklist(tag: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BLACKLIST_KEY] ?: emptySet()
            preferences[BLACKLIST_KEY] = current - tag
        }
    }
}
