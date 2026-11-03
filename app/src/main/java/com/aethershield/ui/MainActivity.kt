package com.aethershield.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aethershield.R
import com.aethershield.ui.theme.AetherShieldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AetherShieldTheme { AetherShieldApp() } }
    }
}

@Composable
fun AetherShieldApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val items = listOf(
        R.string.nav_home to Icons.Default.Home,
        R.string.nav_sharing to Icons.Default.Share,
        R.string.nav_diagnostics to Icons.Default.Build,
        R.string.nav_settings to Icons.Default.Settings,
        R.string.nav_about to Icons.Default.Info,
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> HomeScreen()
                1 -> PlaceholderScreen(R.string.nav_sharing, R.string.todo_sharing)
                2 -> DiagnosticsScreen()
                3 -> PlaceholderScreen(R.string.nav_settings, R.string.todo_settings)
                else -> AboutScreen()
            }
        }
    }
}
