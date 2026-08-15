package com.viralgur.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    autoPlayVideos: Boolean,
    autoReplay: Boolean,
    blacklistedUsers: Set<String>,
    blacklistedTags: Set<String>,
    onDarkModeToggle: (Boolean) -> Unit,
    onAutoPlayVideosToggle: (Boolean) -> Unit,
    onAutoReplayToggle: (Boolean) -> Unit,
    onAddBlacklistUser: (String) -> Unit,
    onRemoveBlacklistUser: (String) -> Unit,
    onAddBlacklistTag: (String) -> Unit,
    onRemoveBlacklistTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        },
        modifier = modifier
    ) { padding ->
        // Alles in einer einzigen LazyColumn, damit die gesamte Seite scrollbar ist
        // und es keine Abstürze gibt, wenn beide Listen lang werden.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // --- EINSTELLUNGEN ---
            item {
                // Dark Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dark Mode", style = MaterialTheme.typography.titleMedium)
                    Switch(checked = isDarkMode, onCheckedChange = onDarkModeToggle)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Play Videos", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "videos start playing automatically when visible",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoPlayVideos, onCheckedChange = onAutoPlayVideosToggle)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Auto Replay Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Replay Videos", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "videos automatically start from the beginning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = autoReplay, onCheckedChange = onAutoReplayToggle)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // --- BLOCKIERTE ACCOUNTS ---
                Text(
                    text = "blocked accounts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (blacklistedUsers.isEmpty()) {
                    Text(
                        text = "no accounts blocked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Die Liste der Accounts
            items(blacklistedUsers.toList().sorted()) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "@$user",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    IconButton(onClick = { onRemoveBlacklistUser(user) }) {
                        Text(
                            text = "−",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // --- BLOCKIERTE TAGS ---
            item {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "blocked tags",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (blacklistedTags.isEmpty()) {
                    Text(
                        text = "no tags blocked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Die Liste der Tags
            items(blacklistedTags.toList().sorted()) { tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    IconButton(onClick = { onRemoveBlacklistTag(tag) }) {
                        Text(
                            text = "−",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}