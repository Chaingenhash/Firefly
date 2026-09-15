package dev.chaingenhash.firefly.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chaingenhash.firefly.domain.Direction
import dev.chaingenhash.firefly.domain.Threshold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThresholdEditorSheet(
    existing: Threshold?,
    onDismiss: () -> Unit,
    onSave: (id: String?, level: Int, direction: Direction, label: String?) -> Unit,
) {
    var level by remember { mutableIntStateOf(existing?.level ?: 80) }
    var direction by remember { mutableStateOf(existing?.direction ?: Direction.CHARGING_UP) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text("Alert at $level%")
            Slider(
                value = level.toFloat(),
                onValueChange = { level = it.toInt() },
                valueRange = 1f..100f,
                steps = 98,
            )

            Spacer(Modifier.height(8.dp))

            Row {
                FilterChip(
                    selected = direction == Direction.CHARGING_UP,
                    onClick = { direction = Direction.CHARGING_UP },
                    label = { Text("While charging") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = direction == Direction.DISCHARGING_DOWN,
                    onClick = { direction = Direction.DISCHARGING_DOWN },
                    label = { Text("While draining") },
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    onSave(existing?.id, level, direction, label)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (existing == null) "Add threshold" else "Save")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
