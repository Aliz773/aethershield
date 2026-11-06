package com.aethershield.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aethershield.AppContainer
import com.aethershield.R
import com.aethershield.core.IpVersion
import com.aethershield.core.PsiphonCountries
import com.aethershield.core.NoizeProfile
import com.aethershield.core.ScanMode
import com.aethershield.core.ThemeMode
import com.aethershield.core.Transport
import com.aethershield.core.TunnelMode
import com.aethershield.core.VpnSettings
import com.aethershield.core.hasSelectableCountry
import com.aethershield.core.isIdle
import com.aethershield.core.mayUseMasque
import com.aethershield.data.SettingsRepository
import com.aethershield.ui.components.AetherCard
import com.aethershield.ui.components.AetherSectionHeader
import com.aethershield.ui.components.AetherSettingRow
import com.aethershield.ui.components.AetherTopBar
import com.aethershield.ui.theme.AetherText
import com.aethershield.ui.theme.Space
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<VpnSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, VpnSettings())
    val themeMode: StateFlow<ThemeMode> =
        repo.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    fun setTheme(v: ThemeMode) { viewModelScope.launch { repo.setTheme(v) } }
    fun setMode(v: TunnelMode) { viewModelScope.launch { repo.setMode(v) } }
    fun setTransport(v: Transport) { viewModelScope.launch { repo.setTransport(v) } }
    fun setScan(v: ScanMode) { viewModelScope.launch { repo.setScan(v) } }
    fun setNoize(v: NoizeProfile) { viewModelScope.launch { repo.setNoize(v) } }
    fun setIp(v: IpVersion) { viewModelScope.launch { repo.setIpVersion(v) } }
    fun setRegion(v: String?) { viewModelScope.launch { repo.setPsiphonRegion(v) } }

    companion object {
        val Factory = viewModelFactory { initializer { SettingsViewModel(AppContainer.settings) } }
    }
}

@Composable
fun SettingsScreen(
    onOpenSharing: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAbout: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by vm.settings.collectAsState()
    val theme by vm.themeMode.collectAsState()
    val state by AppContainer.connection.state.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val locked = !state.isIdle

    var dialog by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.screen)
            .padding(top = 12.dp, bottom = Space.section),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AetherTopBar(
            title = stringResource(R.string.nav_settings),
            subtitle = stringResource(R.string.settings_subtitle),
        )

        if (locked) {
            Text(
                stringResource(R.string.cfg_locked),
                style = AetherText.label,
                color = scheme.error,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        AetherSectionHeader(stringResource(R.string.settings_appearance))
        AetherCard(contentPadding = 0.dp) {
            AetherSettingRow(
                title = stringResource(R.string.settings_theme),
                description = stringResource(theme.labelRes()),
                onClick = { dialog = "theme" },
            )
        }

        AetherSectionHeader(stringResource(R.string.settings_connection))
        AetherCard(contentPadding = 0.dp) {
            AetherSettingRow(
                title = stringResource(R.string.cfg_mode),
                description = stringResource(settings.mode.labelRes()),
                onClick = { if (!locked) dialog = "mode" },
            )
            AetherSettingRow(
                title = stringResource(R.string.cfg_transport),
                description = if (settings.mode.mayUseMasque) {
                    stringResource(settings.transport.labelRes())
                } else {
                    stringResource(R.string.cfg_transport_na)
                },
                onClick = { if (!locked && settings.mode.mayUseMasque) dialog = "transport" },
            )
            AetherSettingRow(
                title = stringResource(R.string.settings_ip),
                description = stringResource(settings.ip.labelRes()),
                onClick = { if (!locked) dialog = "ip" },
            )
            if (settings.mode.hasSelectableCountry) {
                AetherSettingRow(
                    title = stringResource(R.string.exit_country_title),
                    description = settings.psiphonRegion?.let { countryLabel(it) }
                        ?: stringResource(R.string.country_auto),
                    onClick = { if (!locked) country = true },
                )
            }
        }

        AetherSectionHeader(stringResource(R.string.settings_advanced))
        AetherCard(contentPadding = 0.dp) {
            AetherSettingRow(
                title = stringResource(R.string.settings_scan),
                description = stringResource(settings.scan.labelRes()),
                onClick = { if (!locked) dialog = "scan" },
            )
            AetherSettingRow(
                title = stringResource(R.string.settings_noize),
                description = stringResource(settings.noize.labelRes()),
                onClick = { if (!locked) dialog = "noize" },
            )
        }

        AetherSectionHeader(stringResource(R.string.settings_more))
        AetherCard(contentPadding = 0.dp) {
            AetherSettingRow(
                title = stringResource(R.string.nav_sharing),
                description = stringResource(R.string.share_subtitle),
                onClick = onOpenSharing,
            )
            AetherSettingRow(
                title = stringResource(R.string.settings_diagnostics),
                description = stringResource(R.string.logs_hint),
                onClick = onOpenLogs,
            )
            AetherSettingRow(
                title = stringResource(R.string.nav_about),
                description = stringResource(R.string.about_tagline),
                onClick = onOpenAbout,
            )
        }

        Text(
            stringResource(R.string.settings_version_footer),
            style = AetherText.label,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
    }

    when (dialog) {
        "theme" -> ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = theme,
            label = { stringResource(it.labelRes()) },
            onDismiss = { dialog = "" },
            onSelect = { vm.setTheme(it); dialog = "" },
        )
        "mode" -> ChoiceDialog(
            title = stringResource(R.string.cfg_mode),
            options = TunnelMode.entries,
            selected = settings.mode,
            label = { stringResource(it.labelRes()) },
            onDismiss = { dialog = "" },
            onSelect = { vm.setMode(it); dialog = "" },
        )
        "transport" -> ChoiceDialog(
            title = stringResource(R.string.cfg_transport),
            options = Transport.entries,
            selected = settings.transport,
            label = { stringResource(it.labelRes()) },
            onDismiss = { dialog = "" },
            onSelect = { vm.setTransport(it); dialog = "" },
        )
        "ip" -> ChoiceDialog(
            title = stringResource(R.string.settings_ip),
            options = IpVersion.entries,
            selected = settings.ip,
            label = { stringResource(it.labelRes()) },
            onDismiss = { dialog = "" },
            onSelect = { vm.setIp(it); dialog = "" },
        )
        "scan" -> ChoiceDialog(
            title = stringResource(R.string.settings_scan),
            options = ScanMode.entries,
            selected = settings.scan,
            label = { stringResource(it.labelRes()) },
            onDismiss = { dialog = "" },
            onSelect = { vm.setScan(it); dialog = "" },
        )
        "noize" -> ChoiceDialog(
            title = stringResource(R.string.settings_noize),
            options = NoizeProfile.entries,
            selected = settings.noize,
            label = { stringResource(it.labelRes()) },
            onDismiss = { dialog = "" },
            onSelect = { vm.setNoize(it); dialog = "" },
        )
    }

    if (country) {
        CountryPickerDialog(
            selected = settings.psiphonRegion,
            onDismiss = { country = false },
            onPick = { vm.setRegion(it); country = false },
            onClear = { vm.setRegion(null); country = false },
            allowedCodes = PsiphonCountries.codes,
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(options) { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(label(option), fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
