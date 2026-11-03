package com.aethershield.ui

import androidx.annotation.StringRes
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.aethershield.core.ExitPolicy
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
import java.util.Locale

class SettingsViewModel(private val repo: SettingsRepository) : ViewModel() {
    val settings: StateFlow<VpnSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, VpnSettings())

    fun setScan(value: ScanMode) { viewModelScope.launch { repo.setScan(value) } }
    fun setNoize(value: NoizeProfile) { viewModelScope.launch { repo.setNoize(value) } }
    fun setIpVersion(value: IpVersion) { viewModelScope.launch { repo.setIpVersion(value) } }
    fun setExitPolicy(value: ExitPolicy) { viewModelScope.launch { repo.setExitPolicy(value) } }

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
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val settings by vm.settings.collectAsState()
    val state by AppContainer.connection.state.collectAsState()
    val locked = !state.isIdle
    val scheme = MaterialTheme.colorScheme
    var pickingCountry by remember { mutableStateOf(false) }

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

        ExitPolicyCard(
            policy = settings.exit,
            enabled = !locked,
            onSelect = vm::setExitPolicy,
            onPickCountry = { pickingCountry = true },
        )
    }

    if (pickingCountry) {
        CountryPickerDialog(
            selected = (settings.exit as? ExitPolicy.Only)?.countryCode,
            onDismiss = { pickingCountry = false },
            onPick = {
                vm.setExitPolicy(ExitPolicy.Only(it))
                pickingCountry = false
            },
        )
    }
}

@Composable
private fun ExitPolicyCard(
    policy: ExitPolicy,
    enabled: Boolean,
    onSelect: (ExitPolicy) -> Unit,
    onPickCountry: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text(
                stringResource(R.string.exit_policy_title),
                modifier = Modifier.padding(horizontal = 16.dp),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.exit_policy_hint),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariant,
            )

            RadioRow(
                selected = policy is ExitPolicy.Any,
                enabled = enabled,
                label = stringResource(R.string.exit_policy_any),
                onSelect = { onSelect(ExitPolicy.Any) },
            )
            RadioRow(
                selected = policy is ExitPolicy.Block,
                enabled = enabled,
                label = stringResource(R.string.exit_policy_not_iran),
                onSelect = { onSelect(ExitPolicy.Block("IR")) },
            )
            RadioRow(
                selected = policy is ExitPolicy.Only,
                enabled = enabled,
                label = stringResource(R.string.exit_policy_only),
                onSelect = onPickCountry,
            )

            if (policy is ExitPolicy.Only) {
                TextButton(
                    onClick = onPickCountry,
                    enabled = enabled,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(countryName(policy.countryCode) ?: policy.countryCode)
                }
            }
        }
    }
}

@Composable
private fun RadioRow(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.RadioButton) { onSelect() }
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Text(label)
    }
}

@Composable
private fun CountryPickerDialog(
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exit_policy_pick)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(COUNTRIES, key = { it.code }) { country ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(country.code) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = country.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (country.code == selected) FontWeight.SemiBold else null,
                        )
                        Text(
                            text = country.code,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private class Country(val code: String, val name: String)

/** Every ISO country the platform knows, named in the current language. */
private val COUNTRIES: List<Country> by lazy {
    Locale.getAvailableLocales().asSequence()
        .map { it to it.country }
        .filter { (_, code) -> code.length == 2 && code.all { c -> c in 'A'..'Z' } }
        .map { (locale, code) ->
            Country(code, locale.getDisplayCountry(Locale.getDefault()).ifBlank { code })
        }
        .distinctBy { it.code }
        .sortedBy { it.name }
        .toList()
}

/** Localised country name for an ISO code, or null when the code is not a country. */
private fun countryName(code: String): String? = runCatching {
    Locale.Builder().setRegion(code).build()
        .getDisplayCountry(Locale.getDefault())
        .ifBlank { null }
}.getOrNull()

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
