package dev.chaingenhash.firefly.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chaingenhash.firefly.ui.theme.FireflyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()

            // Two screens do not justify a navigation library. This survives rotation
            // via rememberSaveable and hands the back gesture the same behaviour the
            // back arrow has.
            var showSettings by rememberSaveable { mutableStateOf(false) }

            FireflyTheme(theme = settings.theme) {
                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        settings = settings,
                        onThemeChange = viewModel::setTheme,
                        onResumeOnBootChange = viewModel::setResumeOnBoot,
                        onBack = { showSettings = false },
                    )
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}
