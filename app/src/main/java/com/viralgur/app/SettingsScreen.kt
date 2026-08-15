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
    autoReplay: Boolean,
    blacklistedUsers: Set<String>,
    blacklistedTags: Set<String>,
    onDarkModeToggle: (Boolean) -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
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

            // NEU: Auto Replay Toggle
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

            // Überschrift für blockierte Nutzer
            Text(
                text = "Blockierte Accounts",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Liste der blockierten Nutzer
            if (blacklistedUsers.isEmpty()) {
                Text(
                    text = "Keine Accounts blockiert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
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
                }
            }
        }
    }
}
