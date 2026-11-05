@file:OptIn(ExperimentalMaterial3Api::class)

package com.aethershield.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.aethershield.core.labelRes
import com.aethershield.core.mayUseMasque
import com.aethershield.data.SettingsRepository
import com.aethershield.ui.components.AetherCard
import com.aethershield.ui.components.AetherChevron
import com.aethershield.ui.components.AetherConnectionButton
import com.aethershield.ui.components.AetherInfoRow
import com.aethershield.ui.components.AetherTopBar
import com.aethershield.ui.components.ConnectionStatusText
import com.aethershield.ui.components.GlyphGlobe
import com.aethershield.ui.components.GlyphServer
import com.aethershield.ui.components.PingIndicator
import com.aethershield.ui.components.Sparkline
import com.aethershield.ui.theme.AetherText
import com.aethershield.ui.theme.Palette
import com.aethershield.ui.theme.Radius
import com.aethershield.ui.theme.Space
import com.aethershield.vpn.ConnectionManager
import com.aethershield.vpn.VpnController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    manager: ConnectionManager,
    private val repo: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<ConnectionState> = manager.state
    val traffic: StateFlow<TrafficSnapshot> = manager.traffic
    val exitInfo: StateFlow<ExitInfo?> = manager.exitInfo
    val activeMode: StateFlow<TunnelMode?> = manager.activeMode
    val ping: StateFlow<Int?> = manager.ping
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

private const val PLACEHOLDER = "\u2014"

private fun formatElapsed(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
fun HomeScreen(
    onOpenSharing: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsState()
    val traffic by vm.traffic.collectAsState()
    val exit by vm.exitInfo.collectAsState()
    val activeMode by vm.activeMode.collectAsState()
    val ping by vm.ping.collectAsState()
    val settings by vm.settings.collectAsState()

    var showSheet by remember { mutableStateOf(false) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) VpnController.start(ctx)
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
    val accent = when (s) {
        is ConnectionState.Connected -> Palette.Cyan
        is ConnectionState.Error -> Palette.Red
        else -> scheme.onSurface
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(top = 12.dp, bottom = Space.section),
        verticalArrangement = Arrangement.spacedBy(Space.standard),
    ) {
        AetherTopBar(
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.app_subtitle),
            onShare = onOpenSharing,
            onMore = { showMore = true },
        )

        Box {
            DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_settings)) },
                    onClick = { showMore = false; onOpenSettings() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_logs)) },
                    onClick = { showMore = false; onOpenLogs() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_sharing)) },
                    onClick = { showMore = false; onOpenSharing() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.nav_about)) },
                    onClick = { showMore = false; onOpenAbout() },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AetherConnectionButton(state = s, onClick = ::onMainButton)

            Spacer(Modifier.height(18.dp))

            if (s is ConnectionState.Connected) {
                val elapsed by produceState(0L, s) {
                    while (true) {
                        value = System.currentTimeMillis() - s.sinceMillis
                        delay(1_000)
                    }
                }
                ConnectionStatusText(
                    title = stringResource(s.labelRes()),
                    subtitle = formatElapsed(elapsed),
                    accent = accent,
                )
            } else {
                ConnectionStatusText(
                    title = stringResource(s.labelRes()),
                    subtitle = if (s.isIdle) stringResource(R.string.home_tap_to_connect) else null,
                    accent = accent,
                )
            }
        }

        if (s is ConnectionState.Error) {
            AetherCard {
                Text(s.message, style = AetherText.body, color = scheme.error)
            }
        }

        ServerCard(
            exit = exit,
            ping = ping,
            regionCode = settings.psiphonRegion,
            canPickCountry = settings.mode.hasSelectableCountry,
            onPickCountry = { showCountryPicker = true },
        )

        TrafficCard(traffic = traffic, live = s is ConnectionState.Connected)

        ConfigurationCard(
            modeLabel = if (settings.mode == TunnelMode.SMART && activeMode != null) {
                stringResource(R.string.mode_smart) + " · " + stringResource(activeMode!!.labelRes())
            } else {
                stringResource(settings.mode.labelRes())
            },
            transportLabel = if (settings.mode.mayUseMasque) {
                stringResource(settings.transport.labelRes())
            } else {
                PLACEHOLDER
            },
            regionLabel = settings.psiphonRegion?.let { countryLabel(it) }
                ?: stringResource(R.string.country_auto),
            onManage = { showSheet = true },
        )
    }

    if (showCountryPicker) {
        CountryPickerDialog(
            selected = settings.psiphonRegion,
            onDismiss = { showCountryPicker = false },
            onPick = { vm.setRegion(it); showCountryPicker = false },
            onClear = { vm.setRegion(null); showCountryPicker = false },
        )
    }

    if (showSheet) {
        val locked = !s.isIdle
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = scheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier
                    .padding(horizontal = Space.screen)
                    .padding(bottom = Space.section)
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.cfg_mode), style = AetherText.cardTitle, color = scheme.onSurface)
                TunnelMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.small))
                            .clickable(enabled = !locked) { vm.setMode(mode) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.mode == mode,
                            onClick = { vm.setMode(mode) },
                            enabled = !locked,
                        )
                        Text(stringResource(mode.labelRes()), style = AetherText.body)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.cfg_transport), style = AetherText.cardTitle, color = scheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Transport.entries.forEach { transport ->
                        FilterChip(
                            selected = settings.transport == transport,
                            onClick = { vm.setTransport(transport) },
                            label = { Text(stringResource(transport.labelRes())) },
                            enabled = !locked && settings.mode.mayUseMasque,
                        )
                    }
                }
                if (!settings.mode.mayUseMasque) {
                    Text(
                        stringResource(R.string.cfg_transport_na),
                        style = AetherText.label,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (locked) {
                    Text(
                        stringResource(R.string.cfg_locked),
                        style = AetherText.label,
                        color = scheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    exit: ExitInfo?,
    ping: Int?,
    regionCode: String?,
    canPickCountry: Boolean,
    onPickCountry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AetherCard(contentPadding = 16.dp) {
        val country = exit?.countryCode
        val place = when {
            country != null -> countryLabel(country)
            exit != null -> exit.ip
            else -> PLACEHOLDER
        }
        AetherInfoRow(
            label = stringResource(R.string.server_title),
            value = place,
            leading = { GlyphServer(tint = scheme.primary, modifier = Modifier.size(22.dp)) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PingIndicator(ping)
                    Spacer(Modifier.width(8.dp))
                    AetherChevron()
                }
            },
        )
        if (exit != null) {
            Text(
                exit.ip,
                modifier = Modifier.padding(start = 34.dp, bottom = 2.dp),
                style = AetherText.label.copy(fontFamily = FontFamily.Monospace),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(color = scheme.outline.copy(alpha = 0.4f))
        AetherInfoRow(
            label = stringResource(R.string.exit_country_title),
            value = regionCode?.let { countryLabel(it) } ?: stringResource(R.string.country_auto),
            leading = { GlyphGlobe(tint = scheme.primary, modifier = Modifier.size(22.dp)) },
            trailing = {
                if (canPickCountry) {
                    AetherChevron()
                } else {
                    Text(
                        stringResource(R.string.exit_country_auto_hint),
                        style = AetherText.label,
                        color = scheme.onSurfaceVariant,
                    )
                }
            },
            onClick = if (canPickCountry) onPickCountry else null,
        )
    }
}

@Composable
private fun TrafficCard(traffic: TrafficSnapshot, live: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val downHistory = remember { mutableStateListOf<Float>() }
    val upHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(traffic) {
        if (!live) return@LaunchedEffect
        downHistory.add(traffic.downBytesPerSec.toFloat())
        upHistory.add(traffic.upBytesPerSec.toFloat())
        if (downHistory.size > 40) downHistory.removeAt(0)
        if (upHistory.size > 40) upHistory.removeAt(0)
    }

    AetherCard(contentPadding = 16.dp) {
        Row(Modifier.fillMaxWidth()) {
            TrafficColumn(
                label = stringResource(R.string.traffic_down),
                rate = traffic.downBytesPerSec,
                total = traffic.downBytes,
                history = downHistory,
                color = Palette.Cyan,
                live = live,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(64.dp)
                    .background(scheme.outline.copy(alpha = 0.45f))
            )
            Spacer(Modifier.width(16.dp))
            TrafficColumn(
                label = stringResource(R.string.traffic_up),
                rate = traffic.upBytesPerSec,
                total = traffic.upBytes,
                history = upHistory,
                color = Palette.Blue,
                live = live,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrafficColumn(
    label: String,
    rate: Long,
    total: Long,
    history: List<Float>,
    color: Color,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Text(label, style = AetherText.label, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (live) formatRate(rate) else PLACEHOLDER,
            style = AetherText.value.copy(fontSize = 18.sp),
            color = scheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = if (live) formatBytes(total) else "",
            style = AetherText.label,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Sparkline(
            values = history.toList(),
            color = color,
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
    }
}

@Composable
private fun ConfigurationCard(
    modeLabel: String,
    transportLabel: String,
    regionLabel: String,
    onManage: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AetherCard(onClick = onManage, contentPadding = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.cfg_title),
                style = AetherText.cardTitle,
                color = scheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.cfg_manage), style = AetherText.label, color = scheme.primary)
            AetherChevron()
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ConfigPill(stringResource(R.string.cfg_mode), modeLabel, Modifier.weight(1f))
            ConfigPill(stringResource(R.string.cfg_transport), transportLabel, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        ConfigPill(stringResource(R.string.cfg_country_short), regionLabel, Modifier.fillMaxWidth())
    }
}

@Composable
private fun ConfigPill(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.small))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(label, style = AetherText.label, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = AetherText.body.copy(fontWeight = FontWeight.Medium),
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
