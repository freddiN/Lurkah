package com.viralgur.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val settingsManager: SettingsManager) : ViewModel() {

    val isDarkMode: StateFlow<Boolean> = settingsManager.isDarkModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val blacklist: StateFlow<Set<String>> = settingsManager.blacklistFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.saveDarkMode(enabled)
        }
    }

    fun addTagToBlacklist(tag: String) {
        viewModelScope.launch {
            settingsManager.addToBlacklist(tag)
        }
    }

    fun removeTagFromBlacklist(tag: String) {
        viewModelScope.launch {
            settingsManager.removeFromBlacklist(tag)
        }
    }

    class Factory(private val settingsManager: SettingsManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                return MainViewModel(settingsManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
