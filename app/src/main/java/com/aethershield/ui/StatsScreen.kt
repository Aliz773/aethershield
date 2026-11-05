package com.aethershield.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aethershield.AppContainer
import com.aethershield.R
import com.aethershield.core.ConnectionState
import com.aethershield.ui.components.AetherCard
import com.aethershield.ui.components.Sparkline
import com.aethershield.ui.theme.AetherText
import com.aethershield.ui.theme.Palette
import com.aethershield.ui.theme.Space

@Composable
fun StatsScreen() {
    val state by AppContainer.connection.state.collectAsState()
    val traffic by AppContainer.connection.traffic.collectAsState()
    val ping by AppContainer.connection.ping.collectAsState()
    val connected = state is ConnectionState.Connected
    val scheme = MaterialTheme.colorScheme

    val downHistory = remember { mutableStateListOf<Float>() }
    val upHistory = remember { mutableStateListOf<Float>() }
    androidx.compose.runtime.LaunchedEffect(traffic) {
        downHistory.add(traffic.downBytesPerSec.toFloat())
        upHistory.add(traffic.upBytesPerSec.toFloat())
        if (downHistory.size > 60) downHistory.removeAt(0)
        if (upHistory.size > 60) upHistory.removeAt(0)
    }

    val since = (state as? ConnectionState.Connected)?.sinceMillis
    val duration = since?.let { System.currentTimeMillis() - it } ?: 0L

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(top = 12.dp, bottom = Space.section),
        verticalArrangement = Arrangement.spacedBy(Space.standard),
    ) {
        Text(
            stringResource(R.string.nav_stats),
            style = AetherText.screenTitle,
            color = scheme.onBackground,
        )

        if (!connected) {
            AetherCard {
                Text(
                    stringResource(R.string.stats_empty),
                    style = AetherText.body,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        AetherCard {
            Text(stringResource(R.string.stats_totals), style = AetherText.cardTitle, color = scheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBlock(
                    label = stringResource(R.string.traffic_down),
                    value = if (connected) formatBytes(traffic.downBytes) else "\u2014",
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    label = stringResource(R.string.traffic_up),
                    value = if (connected) formatBytes(traffic.upBytes) else "\u2014",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        AetherCard {
            Text(stringResource(R.string.stats_speed), style = AetherText.cardTitle, color = scheme.onSurface)
            Spacer(Modifier.height(12.dp))
            Sparkline(
                values = downHistory.toList(),
                color = Palette.Cyan,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
            Spacer(Modifier.height(8.dp))
            Sparkline(
                values = upHistory.toList(),
                color = Palette.Blue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "↓ " + (if (connected) formatRate(traffic.downBytesPerSec) else "\u2014"),
                    style = AetherText.label,
                    color = Palette.Cyan,
                )
                Text(
                    "↑ " + (if (connected) formatRate(traffic.upBytesPerSec) else "\u2014"),
                    style = AetherText.label,
                    color = Palette.Blue,
                )
            }
        }

        AetherCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatBlock(
                    label = stringResource(R.string.stats_duration),
                    value = if (connected) formatDuration(duration) else "\u2014",
                    modifier = Modifier.weight(1f),
                )
                StatBlock(
                    label = stringResource(R.string.server_ping),
                    value = ping?.let { "$it ms" } ?: "-- ms",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(label, style = AetherText.label, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = AetherText.value, color = scheme.onSurface, maxLines = 1)
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
