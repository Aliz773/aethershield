package com.aethershield.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aethershield.AppContainer
import com.aethershield.R
import com.aethershield.core.AetherProcessEngine
import com.aethershield.core.ConnectionState
import com.aethershield.core.labelRes
import kotlinx.coroutines.delay

/** Honest placeholder for screens that are not built yet (no fake controls). */
@Composable
fun PlaceholderScreen(@StringRes title: Int, @StringRes message: Int) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(title), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DiagnosticsScreen() {
    val state by AppContainer.connection.state.collectAsState()
    val status by produceState(initialValue = AppContainer.connection.engineStatus()) {
        while (true) {
            value = AppContainer.connection.engineStatus()
            delay(1_000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.nav_diagnostics), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        Row2(stringResource(R.string.diag_state), stringResource(state.labelRes()))

        val port = status.endpoint?.port
        val pid = status.pid
        val exit = status.exitCode
        Row2(
            label = stringResource(R.string.diag_core),
            value = when {
                status.running && port != null ->
                    stringResource(R.string.diag_core_running, pid ?: 0L, port)
                status.running -> stringResource(R.string.diag_core_starting)
                exit != null -> stringResource(R.string.diag_core_exited, exit)
                else -> stringResource(R.string.diag_core_stopped)
            },
        )

        (state as? ConnectionState.Error)?.let { Row2(stringResource(R.string.status_error), it.message) }

        Text(
            stringResource(R.string.diag_log),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Text(
                text = status.lastLog.takeLast(LOG_LINES).joinToString("\n")
                    .ifBlank { stringResource(R.string.diag_log_empty) },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
fun AboutScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Row2(stringResource(R.string.about_core_version), AetherProcessEngine.CORE_VERSION)
        Text(stringResource(R.string.about_license_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Row2(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

private const val LOG_LINES = 30
