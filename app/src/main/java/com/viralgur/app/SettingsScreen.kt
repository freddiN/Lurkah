package com.viralgur.app

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
    blacklist: Set<String>,
    onDarkModeToggle: (Boolean) -> Unit,
    onAddBlacklistTag: (String) -> Unit,
    onRemoveBlacklistTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTagText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Einstellungen",
            style = MaterialTheme.typography.headlineMedium
        )

        // --- Dark Mode Switch ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dunkles Design (Dark Mode)",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isDarkMode,
                onCheckedChange = onDarkModeToggle
            )
        }

        HorizontalDivider()

        // --- Blacklist Verwaltung ---
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Blacklist (Blockierte Tags)",
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text("Tag hinzufügen") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (newTagText.isNotBlank()) {
                            onAddBlacklistTag(newTagText)
                            newTagText = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Hinzufügen")
                }
            }

            // Liste der geblockten Tags als Chips
            if (blacklist.isEmpty()) {
                Text(
                    text = "Keine Tags blockiert.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(blacklist.toList()) { tag ->
                        InputChip(
                            selected = false,
                            onClick = { },
                            label = { Text(tag) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onRemoveBlacklistTag(tag) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Entfernen"
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
