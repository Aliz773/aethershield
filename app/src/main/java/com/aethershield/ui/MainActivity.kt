package com.aethershield.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.aethershield.AppContainer
import com.aethershield.R
import com.aethershield.core.ThemeMode
import com.aethershield.ui.components.GlyphBars
import com.aethershield.ui.components.GlyphLines
import com.aethershield.ui.theme.AetherShieldTheme
import com.aethershield.ui.theme.AetherText
import com.aethershield.ui.theme.Radius
import com.aethershield.ui.theme.Space

/** Every destination, including the two that are reached from elsewhere rather than a tab. */
enum class Route { HOME, STATS, LOGS, SETTINGS, SHARING, ABOUT }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by AppContainer.settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            AetherShieldTheme(theme) { AetherShieldApp() }
        }
    }
}

@Composable
fun AetherShieldApp() {
    var route by rememberSaveable { mutableStateOf(Route.HOME) }
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = scheme.background,
        bottomBar = {
            AetherBottomNavigation(current = route, onSelect = { route = it })
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            when (route) {
                Route.HOME -> HomeScreen(
                    onOpenSharing = { route = Route.SHARING },
                    onOpenSettings = { route = Route.SETTINGS },
                    onOpenLogs = { route = Route.LOGS },
                    onOpenAbout = { route = Route.ABOUT },
                )

                Route.STATS -> StatsScreen()
                Route.LOGS -> LogsScreen()
                Route.SETTINGS -> SettingsScreen(
                    onOpenSharing = { route = Route.SHARING },
                    onOpenLogs = { route = Route.LOGS },
                    onOpenAbout = { route = Route.ABOUT },
                )

                Route.SHARING -> SharingScreen(onBack = { route = Route.HOME })
                Route.ABOUT -> AboutScreen(onBack = { route = Route.HOME })
            }
        }
    }
}

private data class NavItem(
    val route: Route,
    @param:StringRes val label: Int,
    val icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
)

@Composable
private fun AetherBottomNavigation(current: Route, onSelect: (Route) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val items = listOf(
        NavItem(Route.HOME, R.string.nav_home) { tint ->
            Icon(Icons.Default.Home, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        },
        NavItem(Route.STATS, R.string.nav_stats) { tint ->
            GlyphBars(tint = tint, modifier = Modifier.size(21.dp))
        },
        NavItem(Route.LOGS, R.string.nav_logs) { tint ->
            GlyphLines(tint = tint, modifier = Modifier.size(21.dp))
        },
        NavItem(Route.SETTINGS, R.string.nav_settings) { tint ->
            Icon(Icons.Default.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        },
    )

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Space.standard, vertical = 8.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.nav))
                .background(scheme.surfaceContainer)
                .border(1.dp, scheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Radius.nav))
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val selected = current == item.route
                val tint = if (selected) scheme.primary else scheme.onSurfaceVariant
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.small))
                        .clickable { onSelect(item.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(Radius.small))
                            .background(
                                if (selected) scheme.primary.copy(alpha = 0.14f)
                                else androidx.compose.ui.graphics.Color.Transparent
                            )
                            .padding(horizontal = 14.dp, vertical = 3.dp),
                    ) {
                        item.icon(tint)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(stringResource(item.label), style = AetherText.label, color = tint)
                }
            }
        }
    }
}
