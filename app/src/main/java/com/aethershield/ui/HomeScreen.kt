@file:OptIn(ExperimentalMaterial3Api::class)

package com.aethershield.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.aethershield.core.ExitInfo
import com.aethershield.core.TrafficSnapshot
import com.aethershield.core.Transport
import com.aethershield.core.TunnelMode
import com.aethershield.core.VpnSettings
import com.aethershield.core.hasSelectableCountry
import com.aethershield.core.isIdle
import com.aethershield.core.mayUseMasque
import com.aethershield.core.labelRes
import com.aethershield.data.SettingsRepository
import com.aethershield.vpn.ConnectionManager
import com.aethershield.vpn.VpnController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

class HomeViewModel(
    manager: ConnectionManager,
    private val repo: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<ConnectionState> = manager.state
    val traffic: StateFlow<TrafficSnapshot> = manager.traffic
    val exitInfo: StateFlow<ExitInfo?> = manager.exitInfo
    val activeMode: StateFlow<TunnelMode?> = manager.activeMode
    val settings: StateFlow<VpnSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, VpnSettings())

    fun setMode(m: TunnelMode) { viewModelScope.launch { repo.setMode(m) } }
    fun setTransport(t: Transport) { viewModelScope.launch { repo.setTransport(t) } }
    fun setRegion(code: String?) { viewModelScope.launch { repo.setPsiphonRegion(code) } }

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(AppContainer.connection, AppContainer.settings) }
        }
    }
}

@StringRes
private fun TunnelMode.labelRes(): Int = when (this) {
    TunnelMode.SMART -> R.string.mode_smart
    TunnelMode.MASQUE -> R.string.mode_masque
    TunnelMode.WIREGUARD -> R.string.mode_wg
    TunnelMode.GOOL -> R.string.mode_gool
    TunnelMode.MIM -> R.string.mode_mim
    TunnelMode.WIREGUARD_PSIPHON -> R.string.mode_wg_psiphon
    TunnelMode.GOOL_PSIPHON -> R.string.mode_gool_psiphon
    TunnelMode.MASQUE_PSIPHON -> R.string.mode_masque_psiphon
    TunnelMode.WIREGUARD_TOR -> R.string.mode_wg_tor
    TunnelMode.GOOL_TOR -> R.string.mode_gool_tor
    TunnelMode.MASQUE_TOR -> R.string.mode_masque_tor
    TunnelMode.GOOL_CLASSIC -> R.string.mode_gool_classic
    TunnelMode.TOR_ONLY -> R.string.mode_tor
}

@StringRes
private fun Transport.labelRes(): Int = when (this) {
    Transport.HTTP3_QUIC -> R.string.transport_h3
    Transport.HTTP2_TCP -> R.string.transport_h2
}

/** Shown instead of a number when there is nothing real to show. */
private const val PLACEHOLDER = "\u2014"

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}



@Composable
fun HomeScreen(vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val traffic by vm.traffic.collectAsState()
    val exit by vm.exitInfo.collectAsState()
    val activeMode by vm.activeMode.collectAsState()
    val settings by vm.settings.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) VpnController.start(ctx)
        // Denied => nothing starts and the state stays Disconnected.
    }

    fun onMainButton() {
        when (state) {
            ConnectionState.Disconnected, is ConnectionState.Error -> {
                val intent = VpnController.consentIntent(ctx)
                if (intent != null) consent.launch(intent) else VpnController.start(ctx)
            }
            ConnectionState.Disconnecting -> Unit
            else -> VpnController.stop(ctx)
        }
    }

    val s = state
    val scheme = MaterialTheme.colorScheme
    val ring = when (s) {
        is ConnectionState.Connected -> scheme.primary
        is ConnectionState.Error -> scheme.error
        else -> scheme.outline
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.size(170.dp).clip(CircleShape).border(3.dp, ring, CircleShape)
                .clickable(enabled = s !is ConnectionState.Disconnecting, role = Role.Button) { onMainButton() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (s.isIdle) R.string.btn_connect else R.string.btn_disconnect),
                fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ring,
            )
        }
        Text(stringResource(s.labelRes()), fontSize = 22.sp, fontWeight = FontWeight.Medium)
        if (s.isIdle) {
            Text(
                stringResource(R.string.home_tap_to_connect),
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
            )
        }

        if (s is ConnectionState.Connected) {
            val elapsed by produceState(0L, s) {
                while (true) { value = System.currentTimeMillis() - s.sinceMillis; delay(1000) }
            }
            Text(formatElapsed(elapsed), color = scheme.onSurfaceVariant)
        }
        ExitCard(info = exit, live = s is ConnectionState.Connected)
        if (s is ConnectionState.Error) {
            Card(colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
                Text(s.message, Modifier.padding(16.dp), color = scheme.error)
            }
        }

        Card(
            onClick = { showSheet = true },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.cfg_title), fontWeight = FontWeight.SemiBold)
                val active = activeMode
                val modeText = if (settings.mode == TunnelMode.SMART && active != null) {
                    stringResource(R.string.mode_smart) + " · " + stringResource(active.labelRes())
                } else {
                    stringResource(settings.mode.labelRes())
                }
                ConfigRow(stringResource(R.string.cfg_mode), modeText)
                if (settings.mode.hasSelectableCountry) {
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = s.isIdle) { showCountryPicker = true },
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.cfg_country), color = scheme.onSurfaceVariant)
                        Text(
                            settings.psiphonRegion?.let { countryLabel(it) }
                                ?: stringResource(R.string.country_auto)
                        )
                    }
                }
                Text(stringResource(R.string.cfg_tap_to_change), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }

        // Counted by the TUN forwarder itself (hev_socks5_tunnel_stats), so these are real
        // bytes that crossed the tunnel, not an estimate. "—" only when nothing is connected.
        val live = s is ConnectionState.Connected
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TrafficCard(
                label = stringResource(R.string.traffic_up),
                bytes = traffic.upBytes,
                rate = traffic.upBytesPerSec,
                live = live,
                modifier = Modifier.weight(1f),
            )
            TrafficCard(
                label = stringResource(R.string.traffic_down),
                bytes = traffic.downBytes,
                rate = traffic.downBytesPerSec,
                live = live,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selected = settings.psiphonRegion,
            onDismiss = { showCountryPicker = false },
            onPick = {
                vm.setRegion(it)
                showCountryPicker = false
            },
            onClear = {
                vm.setRegion(null)
                showCountryPicker = false
            },
        )
    }

    if (showSheet) {
        val locked = !s.isIdle
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = scheme.surfaceContainer,
        ) {
            Column(
                Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.cfg_mode), fontWeight = FontWeight.SemiBold)
                TunnelMode.entries.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !locked) { vm.setMode(m) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.mode == m, onClick = { vm.setMode(m) }, enabled = !locked)
                        Text(stringResource(m.labelRes()))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.cfg_transport), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Transport.entries.forEach { t ->
                        FilterChip(
                            selected = settings.transport == t,
                            onClick = { vm.setTransport(t) },
                            label = { Text(stringResource(t.labelRes())) },
                            enabled = !locked && settings.mode.mayUseMasque,
                        )
                    }
                }
                if (!settings.mode.mayUseMasque) {
                    Text(stringResource(R.string.cfg_transport_na), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
                if (locked) {
                    Text(stringResource(R.string.cfg_locked), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ExitCard(info: ExitInfo?, live: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.exit_title), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            val place = info?.let { entry ->
                val name = countryName(entry.countryCode) ?: entry.countryCode
                "$name (${entry.countryCode})"
            }
            Text(
                text = place ?: if (live) stringResource(R.string.exit_checking) else PLACEHOLDER,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = info?.ip ?: "",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TrafficCard(
    label: String,
    bytes: Long,
    rate: Long,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (live) formatBytes(bytes) else PLACEHOLDER, fontSize = 20.sp)
            Text(
                text = if (live) formatRate(rate) else "",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
