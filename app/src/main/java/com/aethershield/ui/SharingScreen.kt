package com.aethershield.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import com.aethershield.sharing.NetworkAddresses
import com.aethershield.sharing.SharedClient
import com.aethershield.sharing.SharingServer
import com.aethershield.sharing.SharingState
import com.aethershield.ui.components.AetherCard
import com.aethershield.ui.components.AetherSectionHeader
import com.aethershield.ui.components.AetherTopBar
import com.aethershield.ui.components.GlyphCopy
import com.aethershield.ui.theme.AetherText
import com.aethershield.ui.theme.Palette
import com.aethershield.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SharingViewModel(private val server: SharingServer) : ViewModel() {
    private val _state = MutableStateFlow(server.snapshot())
    val state: StateFlow<SharingState> = _state.asStateFlow()

    init {
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
        val Factory = viewModelFactory { initializer { SharingViewModel(AppContainer.sharing) } }
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

@Suppress("DEPRECATION")
@Composable
fun SharingScreen(
    onBack: () -> Unit,
    vm: SharingViewModel = viewModel(factory = SharingViewModel.Factory),
) {
    val sharing by vm.state.collectAsState()
    val connection by AppContainer.connection.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val connected = connection is ConnectionState.Connected

    val addresses by produceState(initialValue = NetworkAddresses.ipv4()) {
        while (true) {
            value = NetworkAddresses.ipv4()
            delay(3_000)
        }
    }
    val primaryIp = addresses.firstOrNull()?.ip ?: "\u2014"
    val http = "$primaryIp:${sharing.httpPort}"
    val socks = "$primaryIp:${sharing.socksPort}"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(top = 12.dp, bottom = Space.section),
        verticalArrangement = Arrangement.spacedBy(Space.standard),
    ) {
        AetherTopBar(
            title = stringResource(R.string.nav_sharing),
            subtitle = stringResource(R.string.share_subtitle),
            back = { AetherBackButton(onBack) },
        )

        AetherCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.share_enable), style = AetherText.cardTitle, color = scheme.onSurface)
                    Text(
                        stringResource(if (sharing.running) R.string.share_running else R.string.share_stopped),
                        style = AetherText.label,
                        color = if (sharing.running) Palette.Green else scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = sharing.running, onCheckedChange = vm::setEnabled)
            }
            if (!connected) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.share_needs_vpn), style = AetherText.label, color = scheme.error)
            }
            sharing.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AetherText.label, color = scheme.error)
            }
        }

        ProxyCard(
            title = stringResource(R.string.share_http_proxy),
            address = http,
            enabled = sharing.running,
            onCopy = { clipboard.setText(AnnotatedString(http)) },
        )
        ProxyCard(
            title = stringResource(R.string.share_socks_proxy),
            address = socks,
            enabled = sharing.running,
            onCopy = { clipboard.setText(AnnotatedString(socks)) },
        )

        Text(
            stringResource(R.string.share_setup),
            style = AetherText.label,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        if (addresses.size > 1) {
            AetherCard {
                Text(
                    stringResource(R.string.share_other_addresses),
                    style = AetherText.label,
                    color = scheme.onSurfaceVariant,
                )
                addresses.drop(1).forEach { address ->
                    Text(
                        "${address.ip}  ·  ${address.interfaceName}",
                        style = AetherText.label.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        AetherSectionHeader(stringResource(R.string.share_clients))
        AetherCard {
            if (sharing.clients.isEmpty()) {
                Text(
                    stringResource(R.string.share_no_clients),
                    style = AetherText.body,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                sharing.clients.forEach { ClientRow(it) }
            }
        }
    }
}

@Composable
private fun ProxyCard(title: String, address: String, enabled: Boolean, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AetherCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = AetherText.label, color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    address,
                    style = AetherText.value.copy(fontFamily = FontFamily.Monospace, fontSize = 17.sp),
                    color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerHigh)
                    .clickable(onClick = onCopy),
                contentAlignment = Alignment.Center,
            ) {
                GlyphCopy(tint = scheme.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ClientRow(client: SharedClient) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(client.os.emoji(), fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${client.os.label()} · ${client.address}",
                style = AetherText.body,
                color = scheme.onSurface,
            )
            Text(
                "${client.protocol} · ↓ ${formatBytes(client.downBytes)} · ↑ ${formatBytes(client.upBytes)}",
                style = AetherText.label,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
