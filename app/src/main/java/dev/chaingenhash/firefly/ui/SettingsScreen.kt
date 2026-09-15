package dev.chaingenhash.firefly.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chaingenhash.firefly.BuildConfig
import dev.chaingenhash.firefly.domain.AppSettings
import dev.chaingenhash.firefly.domain.ThemeChoice
import dev.chaingenhash.firefly.notify.Notifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeChange: (ThemeChoice) -> Unit,
    onResumeOnBootChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader("Appearance")
            SettingsGroup {
                ThemeChoice.entries.forEach { choice ->
                    ThemeRow(
                        choice = choice,
                        selected = settings.theme == choice,
                        onSelect = { onThemeChange(choice) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader("Monitoring")
            SettingsGroup {
                ToggleRow(
                    title = "Resume after restart",
                    subtitle = "Start monitoring again when the phone reboots, " +
                        "if it was on when the phone shut down.",
                    checked = settings.resumeOnBoot,
                    onCheckedChange = onResumeOnBootChange,
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader("Alerts")
            SettingsGroup {
                // Sound, vibration and importance belong to the system's channel
                // settings — duplicating them in-app would let the two disagree.
                LinkRow(
                    title = "Alert sound and vibration",
                    subtitle = "Managed by Android, in the channel settings for Firefly.",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(
                                    Settings.EXTRA_CHANNEL_ID,
                                    Notifications.CHANNEL_ALERTS,
                                ),
                        )
                    },
                )
                LinkRow(
                    title = "Status notification",
                    subtitle = "The ongoing notification showing the current level.",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                .putExtra(
                                    Settings.EXTRA_CHANNEL_ID,
                                    Notifications.CHANNEL_STATUS,
                                ),
                        )
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            SectionHeader("About")
            SettingsGroup {
                InfoRow(
                    title = "Version",
                    value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
                InfoRow(
                    title = "How alerts work",
                    value = "A threshold fires once when the battery crosses it, then " +
                        "stays quiet until the level moves three points back the other " +
                        "way — so resting on a threshold never repeats the alert.",
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun ThemeRow(choice: ThemeChoice, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            text = when (choice) {
                ThemeChoice.SYSTEM -> "Follow system"
                ThemeChoice.LIGHT -> "Light"
                ThemeChoice.DARK -> "Dark"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
