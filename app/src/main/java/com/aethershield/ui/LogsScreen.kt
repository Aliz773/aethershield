package com.aethershield.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aethershield.AppContainer
import com.aethershield.R
import com.aethershield.core.LogEntry
import com.aethershield.ui.components.AetherCard
import com.aethershield.ui.theme.AetherText
import com.aethershield.ui.theme.Palette
import com.aethershield.ui.theme.Space
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Severity { ALL, INFO, WARN, ERROR }

private fun severityOf(entry: LogEntry): Severity = when {
    entry.text.contains("[-]") -> Severity.ERROR
    entry.text.contains("[!]") -> Severity.WARN
    else -> Severity.INFO
}

@Suppress("DEPRECATION")
@Composable
fun LogsScreen() {
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var filter by remember { mutableStateOf(Severity.ALL) }
    var query by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val status by produceState(initialValue = AppContainer.connection.engineStatus()) {
        while (true) {
            value = AppContainer.connection.engineStatus()
            delay(1_000)
        }
    }

    val all = status.lastLog
    val visible = all.filter { entry ->
        (filter == Severity.ALL || severityOf(entry) == filter) &&
            (query.isBlank() || entry.text.contains(query, ignoreCase = true))
    }

    LaunchedEffect(visible.size, autoScroll) {
        if (autoScroll && visible.isNotEmpty()) {
            listState.animateScrollToItem(visible.lastIndex)
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = Space.screen).padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nav_logs), style = AetherText.screenTitle, color = scheme.onBackground)
                Text(
                    stringResource(R.string.logs_hint),
                    style = AetherText.label,
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(visible.joinToString("\n") { it.text }))
            }) {
                Text(stringResource(R.string.logs_copy), color = scheme.primary)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.logs_search)) },
            shape = RoundedCornerShape(16.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Severity.entries.forEach { level ->
                FilterChip(
                    selected = filter == level,
                    onClick = { filter = level },
                    label = {
                        Text(
                            when (level) {
                                Severity.ALL -> stringResource(R.string.logs_all)
                                Severity.INFO -> stringResource(R.string.logs_info)
                                Severity.WARN -> stringResource(R.string.logs_warn)
                                Severity.ERROR -> stringResource(R.string.logs_error)
                            }
                        )
                    },
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { autoScroll = !autoScroll }) {
                Text(
                    stringResource(if (autoScroll) R.string.logs_autoscroll_on else R.string.logs_autoscroll_off),
                    color = scheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { AppContainer.connection.clearLogs() }) {
                Text(stringResource(R.string.logs_clear), color = Palette.Amber)
            }
        }

        AetherCard(modifier = Modifier.weight(1f), contentPadding = 12.dp) {
            if (visible.isEmpty()) {
                Text(
                    stringResource(R.string.logs_empty),
                    style = AetherText.body,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(state = listState) {
                    items(visible) { entry -> LogRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val scheme = MaterialTheme.colorScheme
    val colour = when (severityOf(entry)) {
        Severity.ERROR -> Palette.Red
        Severity.WARN -> Palette.Amber
        else -> scheme.onSurfaceVariant
    }
    val time = remember(entry.timeMillis) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timeMillis))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .padding(vertical = 3.dp)
    ) {
        Text("$time  ", style = AetherText.mono, color = scheme.onSurfaceVariant.copy(alpha = 0.7f))
        Text(entry.text, style = AetherText.mono, color = colour)
    }
}
