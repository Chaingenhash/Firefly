package dev.chaingenhash.firefly.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chaingenhash.firefly.domain.BatteryState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.chaingenhash.firefly.ui.theme.glowAccent
import dev.chaingenhash.firefly.domain.Direction
import dev.chaingenhash.firefly.domain.Threshold
import dev.chaingenhash.firefly.service.BatteryMonitorService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val thresholds by viewModel.thresholds.collectAsStateWithLifecycle()
    val monitoring by viewModel.monitoring.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Threshold?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    // NotificationManagerCompat.areNotificationsEnabled() reflects whether the user can
    // actually see a notification; checkSelfPermission(POST_NOTIFICATIONS) is always
    // PERMISSION_GRANTED on API 31-32 (the permission didn't exist yet), which would hide
    // this warning from anyone who disabled notifications there.
    var notificationsGranted by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
        if (granted) viewModel.setMonitoring(true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsGranted = NotificationManagerCompat.from(context)
                    .areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The service can die without stopService — an OEM battery killer, a force-stop, a
    // restore onto a new device — leaving the switch reading on with nothing monitoring.
    //
    // This keys on `monitoring` rather than sampling it inside the ON_RESUME observer
    // above: a dead service usually means a dead process, so the next launch is a cold
    // start, and at that first ON_RESUME `monitoring` is still the `stateIn` seed with
    // the DataStore read outstanding. Re-running when the real value arrives is what
    // makes the reconciliation work in the case it exists for. `onStartCommand` is
    // idempotent, so starting an already-live service is a no-op.
    LaunchedEffect(lifecycleOwner, monitoring) {
        if (monitoring) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                BatteryMonitorService.start(context)
            }
        }
    }

    var pendingDelete by remember { mutableStateOf<Threshold?>(null) }
    val accent = glowAccent

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Firefly", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = null; sheetOpen = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add threshold")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            BatteryCard(
                battery = battery,
                monitoring = monitoring,
                accent = accent,
                onMonitoringChange = { wanted ->
                    // Below API 33 there is no runtime permission to request —
                    // checkSelfPermission is always granted there — so a request
                    // is only ever worth launching on 33+ while it is un-granted.
                    val requestWorthwhile = Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED

                    if (wanted && !notificationsGranted && requestWorthwhile) {
                        requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.setMonitoring(wanted)
                    }
                },
            )

            if (!notificationsGranted) {
                NotificationsBlockedNotice(
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                )
            }

            if (thresholds.isEmpty()) {
                EmptyThresholds(onAdd = { editing = null; sheetOpen = true })
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        // Clear the FAB so the last row is never stranded underneath it.
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(thresholds, key = { it.id }) { threshold ->
                        ThresholdRow(
                            threshold = threshold,
                            accent = accent,
                            onClick = { editing = threshold; sheetOpen = true },
                            onToggle = { viewModel.setEnabled(threshold.id, it) },
                            onDelete = { pendingDelete = threshold },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        DeleteConfirmation(
            threshold = target,
            onConfirm = {
                viewModel.delete(target.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (sheetOpen) {
        ThresholdEditorSheet(
            existing = editing,
            onDismiss = { sheetOpen = false },
            onSave = viewModel::save,
        )
    }
}

@Composable
private fun BatteryCard(
    battery: BatteryState?,
    monitoring: Boolean,
    accent: Color,
    onMonitoringChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = battery?.let { "${it.level}%" } ?: "—",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            battery == null -> "Reading battery…"
                            battery.plugged -> "Charging"
                            else -> "On battery"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Switch(
                        checked = monitoring,
                        onCheckedChange = onMonitoringChange,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (monitoring) "Monitoring" else "Paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (monitoring) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            BatteryGauge(
                level = battery?.level ?: 0,
                plugged = battery?.plugged == true,
                accent = accent,
                track = MaterialTheme.colorScheme.surfaceContainerHigh,
                outline = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

@Composable
private fun ThresholdRow(
    threshold: Threshold,
    accent: Color,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val charging = threshold.direction == Direction.CHARGING_UP
    // Direction is carried by an icon AND by the caption text, so it never depends on
    // colour alone.
    val directionLabel = if (charging) "While charging" else "While draining"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (threshold.enabled) {
                            accent.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (charging) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (threshold.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = "${threshold.level}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (threshold.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
                Text(
                    text = threshold.label ?: directionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Switch(
                checked = threshold.enabled,
                onCheckedChange = onToggle,
            )

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete threshold at ${threshold.level} percent",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyThresholds(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No thresholds yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add a level to be alerted at — 80% while charging to unplug, " +
                "or 20% while draining to find a charger.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAdd) { Text("Add a threshold") }
    }
}

@Composable
private fun NotificationsBlockedNotice(onOpenSettings: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp)) {
            Text(
                text = "Notifications are blocked, so alerts cannot be shown.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
            ) {
                Text("Open notification settings")
            }
        }
    }
}

@Composable
private fun DeleteConfirmation(
    threshold: Threshold,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this threshold?") },
        text = {
            Text(
                "The ${threshold.level}% alert " +
                    if (threshold.direction == Direction.CHARGING_UP) {
                        "while charging will be removed."
                    } else {
                        "while draining will be removed."
                    },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
