package com.aethershield.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aethershield.R
import com.aethershield.core.flagEmoji
import java.util.Locale

class Country(val code: String, val name: String) {
    /** "🇩🇪 Germany" -- the flag is what makes a long list scannable. */
    val label: String get() = "${flagEmoji(code)} $name"
}

/**
 * Every ISO country the platform knows, named in the current language. Built once; the list
 * is a few hundred entries and the picker is opened rarely.
 */
val COUNTRIES: List<Country> by lazy {
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
fun countryName(code: String): String? = runCatching {
    Locale.Builder().setRegion(code).build()
        .getDisplayCountry(Locale.getDefault())
        .ifBlank { null }
}.getOrNull()

/** "🇩🇪 Germany (DE)" -- the flag first, because that is what people look for. */
fun countryLabel(code: String): String {
    val name = countryName(code) ?: return code
    return "${flagEmoji(code)} $name ($code)"
}

@Composable
fun CountryPickerDialog(
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onClear: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.country_pick)) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (onClear != null) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onClear() }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.country_auto),
                                modifier = Modifier.weight(1f),
                                fontWeight = if (selected == null) FontWeight.SemiBold else null,
                            )
                        }
                    }
                }
                items(COUNTRIES, key = { it.code }) { country ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(country.code) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = country.label,
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
