package com.viralgur.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    blacklistedUsers: Set<String>,
    onDarkModeToggle: (Boolean) -> Unit,
    onAddBlacklistUser: (String) -> Unit,
    onRemoveBlacklistUser: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newUserText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        // --- Dark Mode Switch ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dark Mode",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isDarkMode,
                onCheckedChange = onDarkModeToggle
            )
        }

        HorizontalDivider()

        // --- Blacklist Management (Users) ---
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Blocked Users",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newUserText,
                    onValueChange = { newUserText = it },
                    label = { Text("Enter username") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newUserText.isNotBlank()) {
                            val cleanUser = newUserText.trim().removePrefix("@")
                            onAddBlacklistUser(cleanUser)
                            newUserText = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Block user")
                }
            }

            // Blocked users chip list
            if (blacklistedUsers.isEmpty()) {
                Text(
                    text = "No users blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(blacklistedUsers.toList()) { user ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text("@$user") },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onRemoveBlacklistUser(user) }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
