package dev.chaingenhash.firefly.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.chaingenhash.firefly.data.ThresholdRepository
import dev.chaingenhash.firefly.domain.BatteryState
import dev.chaingenhash.firefly.domain.Direction
import dev.chaingenhash.firefly.domain.Threshold
import dev.chaingenhash.firefly.service.BatteryMonitorService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = ThresholdRepository(app)

    val battery: StateFlow<BatteryState?> = batteryStateFlow(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val thresholds: StateFlow<List<Threshold>> = repository.thresholds
        .map { list -> list.sortedWith(compareBy({ it.direction }, { -it.level })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monitoring: StateFlow<Boolean> = repository.monitorState
        .map { it.monitoringEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setMonitoring(enabled: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            repository.setMonitoringEnabled(enabled)
            if (enabled) BatteryMonitorService.start(app) else BatteryMonitorService.stop(app)
        }
    }

    fun save(id: String?, level: Int, direction: Direction, label: String?) {
        viewModelScope.launch {
            repository.upsert(
                Threshold(
                    id = id ?: UUID.randomUUID().toString(),
                    level = level,
                    direction = direction,
                    enabled = true,
                    label = label?.takeIf { it.isNotBlank() },
                ),
                currentLevel = battery.value?.level
                    ?: currentBatteryState(getApplication())?.level
                    ?: level,
            )
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(
                id,
                enabled,
                battery.value?.level ?: currentBatteryState(getApplication())?.level ?: 0,
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
