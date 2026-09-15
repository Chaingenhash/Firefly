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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
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
import dev.chaingenhash.firefly.domain.Direction
import dev.chaingenhash.firefly.domain.Threshold
import dev.chaingenhash.firefly.service.BatteryMonitorService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Firefly") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; sheetOpen = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add threshold")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            Card(Modifier.padding(16.dp).fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = battery?.let { "${it.level}%" } ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            text = when {
                                battery == null -> "Reading battery…"
                                battery!!.plugged -> "Charging"
                                else -> "On battery"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = monitoring,
                        onCheckedChange = { wanted ->
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
                }
            }

            if (!notificationsGranted) {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "Notifications are blocked, so alerts cannot be shown. " +
                            "Grant the notification permission in system settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            LazyColumn {
                items(thresholds, key = { it.id }) { threshold ->
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            Modifier.weight(1f).clickable {
                                editing = threshold
                                sheetOpen = true
                            },
                        ) {
                            Text("${threshold.level}%", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = threshold.label ?: when (threshold.direction) {
                                    Direction.CHARGING_UP -> "While charging"
                                    Direction.DISCHARGING_DOWN -> "While draining"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = threshold.enabled,
                            onCheckedChange = { viewModel.setEnabled(threshold.id, it) },
                        )
                        IconButton(onClick = { viewModel.delete(threshold.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        ThresholdEditorSheet(
            existing = editing,
            onDismiss = { sheetOpen = false },
            onSave = viewModel::save,
        )
    }
}
