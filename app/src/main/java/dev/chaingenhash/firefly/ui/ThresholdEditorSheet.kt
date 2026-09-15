package dev.chaingenhash.firefly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chaingenhash.firefly.domain.Direction
import dev.chaingenhash.firefly.domain.Threshold
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdEditorSheet(
    existing: Threshold?,
    onDismiss: () -> Unit,
    onSave: (id: String?, level: Int, direction: Direction, label: String?, enabled: Boolean) -> Unit,
) {
    var level by remember { mutableIntStateOf(existing?.level ?: 80) }
    var direction by remember { mutableStateOf(existing?.direction ?: Direction.CHARGING_UP) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = if (existing == null) "New threshold" else "Edit threshold",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$level",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp),
                )
            }

            Slider(
                value = level.toFloat(),
                onValueChange = { level = it.roundToInt() },
                valueRange = 1f..100f,
                steps = 98,
            )

            Spacer(Modifier.height(16.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = direction == Direction.CHARGING_UP,
                    onClick = { direction = Direction.CHARGING_UP },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {},
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, Modifier.height(18.dp))
                    Text(" While charging")
                }
                SegmentedButton(
                    selected = direction == Direction.DISCHARGING_DOWN,
                    onClick = { direction = Direction.DISCHARGING_DOWN },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {},
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = null, Modifier.height(18.dp))
                    Text(" While draining")
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = when (direction) {
                    Direction.CHARGING_UP -> "Alerts once when the battery climbs past $level% on the charger."
                    Direction.DISCHARGING_DOWN -> "Alerts once when the battery falls past $level% off the charger."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (optional)") },
                supportingText = { Text("Shown as the alert's title instead of the default wording.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enabled", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    onSave(existing?.id, level, direction, label, enabled)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (existing == null) "Add threshold" else "Save")
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
