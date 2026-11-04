package com.aethershield.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aethershield.AppContainer
import com.aethershield.R
import com.aethershield.core.ConnectionState
import com.aethershield.sharing.ClientOs
import com.aethershield.sharing.LocalAddress
import com.aethershield.sharing.NetworkAddresses
import com.aethershield.sharing.SharedClient
import com.aethershield.sharing.SharingServer
import com.aethershield.sharing.SharingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharingViewModel(private val server: SharingServer) : ViewModel() {
    private val _state = MutableStateFlow(server.snapshot())
    val state: StateFlow<SharingState> = _state.asStateFlow()

    init {
        // Byte counters live in the sessions; a one-second tick is all the UI needs.
        viewModelScope.launch {
            while (true) {
                _state.value = server.snapshot()
                delay(1_000)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (enabled) server.start() else server.stop()
        _state.value = server.snapshot()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SharingViewModel(AppContainer.sharing) }
        }
    }
}

private fun ClientOs.emoji(): String = when (this) {
    ClientOs.ANDROID -> "🤖"
    ClientOs.IOS -> "🍎"
    ClientOs.WINDOWS -> "🪟"
    ClientOs.MACOS -> "🍏"
    ClientOs.LINUX -> "🐧"
    ClientOs.UNKNOWN -> "❓"
}

private fun ClientOs.label(): String = when (this) {
    ClientOs.ANDROID -> "Android"
    ClientOs.IOS -> "iOS"
    ClientOs.WINDOWS -> "Windows"
    ClientOs.MACOS -> "macOS"
    ClientOs.LINUX -> "Linux"
    ClientOs.UNKNOWN -> "Unknown device"
}

@Composable
fun SharingScreen(vm: SharingViewModel = viewModel(factory = SharingViewModel.Factory)) {
    val sharing by vm.state.collectAsState()
    val connection by AppContainer.connection.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val connected = connection is ConnectionState.Connected

    val addresses by produceState(initialValue = NetworkAddresses.ipv4()) {
        while (true) {
            value = NetworkAddresses.ipv4()
            delay(3_000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.nav_sharing), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.share_enable), fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.share_hint),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = sharing.running, onCheckedChange = vm::setEnabled)
            }
            if (sharing.error != null) {
                Text(
                    sharing.error.orEmpty(),
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    fontSize = 12.sp,
                    color = scheme.error,
                )
            }
        }

        if (!connected) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
            ) {
                Text(
                    stringResource(R.string.share_needs_vpn),
                    Modifier.padding(16.dp),
                    fontSize = 13.sp,
                    color = scheme.error,
                )
            }
        }

        if (sharing.running) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.share_addresses),
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (addresses.isEmpty()) {
                        Text(
                            stringResource(R.string.share_no_address),
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    addresses.forEach { address -> AddressRow(address, sharing.port) }
                    Text(
                        stringResource(R.string.share_setup),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.share_clients), fontWeight = FontWeight.SemiBold)
                    if (sharing.clients.isEmpty()) {
                        Text(
                            stringResource(R.string.share_no_clients),
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    sharing.clients.forEach { ClientRow(it) }
                }
            }
        }
    }
}

@Composable
private fun AddressRow(address: LocalAddress, port: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text("${address.ip}:$port", fontFamily = FontFamily.Monospace, fontSize = 15.sp)
            Text(address.interfaceName, fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ClientRow(client: SharedClient) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(client.os.emoji(), fontSize = 20.sp)
        Column(Modifier.weight(1f)) {
            Text("${client.os.label()} · ${client.address}", fontSize = 14.sp)
            Text(
                "${client.protocol} · ↓ ${formatBytes(client.downBytes)} · ↑ ${formatBytes(client.upBytes)}",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
