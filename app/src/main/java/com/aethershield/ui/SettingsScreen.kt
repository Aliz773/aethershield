package com.aethershield.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.aethershield.core.IpVersion
import com.aethershield.core.NoizeProfile
import com.aethershield.core.ScanMode
import com.aethershield.core.VpnSettings
import com.aethershield.core.isIdle
import com.aethershield.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<VpnSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, VpnSettings())

    fun setScan(value: ScanMode) { viewModelScope.launch { repo.setScan(value) } }
    fun setNoize(value: NoizeProfile) { viewModelScope.launch { repo.setNoize(value) } }
    fun setIpVersion(value: IpVersion) { viewModelScope.launch { repo.setIpVersion(value) } }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(AppContainer.settings) }
        }
    }
}

@StringRes
private fun ScanMode.labelRes(): Int = when (this) {
    ScanMode.TURBO -> R.string.scan_turbo
    ScanMode.BALANCED -> R.string.scan_balanced
    ScanMode.THOROUGH -> R.string.scan_thorough
    ScanMode.VERIFIED -> R.string.scan_verified
    ScanMode.IRONCLAD -> R.string.scan_ironclad
}

@StringRes
private fun NoizeProfile.labelRes(): Int = when (this) {
    NoizeProfile.AUTO -> R.string.noize_auto
    NoizeProfile.OFF -> R.string.noize_off
    NoizeProfile.LIGHT -> R.string.noize_light
    NoizeProfile.FIREWALL -> R.string.noize_firewall
    NoizeProfile.BALANCED -> R.string.noize_balanced
    NoizeProfile.GFW -> R.string.noize_gfw
    NoizeProfile.AGGRESSIVE -> R.string.noize_aggressive
}

@StringRes
private fun IpVersion.labelRes(): Int = when (this) {
    IpVersion.IPV4 -> R.string.ip_v4
    IpVersion.IPV6 -> R.string.ip_v6
    IpVersion.DUAL -> R.string.ip_dual
}

/**
 * The options here map one-to-one onto real CLI flags of the shipped core, so every change
 * does something. They are locked while a tunnel is up: the core reads its argv once.
 *
 * The exit country is not here on purpose: only Psiphon exposes egress regions, so the picker
 * lives next to the mode it belongs to, in the home configuration panel.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by vm.settings.collectAsState()
    val state by AppContainer.connection.state.collectAsState()
    val locked = !state.isIdle
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.nav_settings), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        if (locked) {
            Text(stringResource(R.string.cfg_locked), fontSize = 12.sp, color = scheme.error)
        }

        ChoiceCard(
            title = stringResource(R.string.settings_scan),
            hint = stringResource(R.string.settings_scan_hint),
            options = ScanMode.entries,
            selected = settings.scan,
            enabled = !locked,
            label = { stringResource(it.labelRes()) },
            onSelect = vm::setScan,
        )

        ChoiceCard(
            title = stringResource(R.string.settings_noize),
            hint = stringResource(R.string.settings_noize_hint),
            options = NoizeProfile.entries,
            selected = settings.noize,
            enabled = !locked,
            label = { stringResource(it.labelRes()) },
            onSelect = vm::setNoize,
        )

        ChoiceCard(
            title = stringResource(R.string.settings_ip),
            hint = stringResource(R.string.settings_ip_hint),
            options = IpVersion.entries,
            selected = settings.ip,
            enabled = !locked,
            label = { stringResource(it.labelRes()) },
            onSelect = vm::setIpVersion,
        )
    }
}

@Composable
private fun <T> ChoiceCard(
    title: String,
    hint: String,
    options: List<T>,
    selected: T,
    enabled: Boolean,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text(
                title,
                modifier = Modifier.padding(horizontal = 16.dp),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                hint,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
            )
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(option) }
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        enabled = enabled,
                    )
                    Text(label(option))
                }
            }
        }
    }
}
