package com.aethershield.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aethershield.R
import com.aethershield.core.AetherProcessEngine
import com.aethershield.ui.components.AetherCard
import com.aethershield.ui.components.AetherChevron
import com.aethershield.ui.components.AetherPrimaryButton
import com.aethershield.ui.components.AetherSectionHeader
import com.aethershield.ui.components.AetherTopBar
import com.aethershield.ui.components.GlyphPower
import com.aethershield.ui.theme.AetherText
import com.aethershield.ui.theme.Palette
import com.aethershield.ui.theme.Radius
import com.aethershield.ui.theme.Space
import com.aethershield.update.UpdateScheduler
import com.aethershield.update.UpdateStore
import com.aethershield.update.VersionMath
import kotlinx.coroutines.delay

private const val TELEGRAM_URL = "https://t.me/TS_PETROS313"
private const val DEVELOPER_GITHUB = "https://github.com/Aliz773"

/** The upstream project this app is a front-end for; taken from its own release metadata. */
private const val CORE_GITHUB = "https://github.com/CluvexStudio/Aether"

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    val known by produceState(initialValue = UpdateStore.load(context)) {
        while (true) {
            value = UpdateStore.load(context)
            delay(2_000)
        }
    }
    val update = known?.takeIf { VersionMath.isNewer(it.version, version) }

    fun open(url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
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
            title = stringResource(R.string.nav_about),
            subtitle = stringResource(R.string.app_subtitle),
            back = { AetherBackButton(onBack) },
        )

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(Radius.medium))
                    .background(scheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                GlyphPower(tint = scheme.primary, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.app_name),
                style = AetherText.screenTitle.copy(fontSize = 24.sp),
                color = scheme.onBackground,
            )
            Text(
                stringResource(R.string.about_tagline),
                style = AetherText.label,
                color = scheme.onSurfaceVariant,
            )
        }

        AetherCard {
            InfoLine(stringResource(R.string.about_app_version), version)
            InfoLine(stringResource(R.string.about_core_version), AetherProcessEngine.CORE_VERSION)
            InfoLine(stringResource(R.string.about_package), context.packageName)
        }

        AetherSectionHeader(stringResource(R.string.about_developer))
        AetherCard(contentPadding = 0.dp) {
            LinkRow(stringResource(R.string.about_telegram), TELEGRAM_URL) { open(TELEGRAM_URL) }
            LinkRow(stringResource(R.string.about_github), DEVELOPER_GITHUB) { open(DEVELOPER_GITHUB) }
        }

        AetherSectionHeader(stringResource(R.string.about_core_developer))
        AetherCard(contentPadding = 0.dp) {
            LinkRow(stringResource(R.string.about_core_github), CORE_GITHUB) { open(CORE_GITHUB) }
        }
        Text(
            stringResource(R.string.about_core_note),
            style = AetherText.label,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        AetherSectionHeader(stringResource(R.string.about_updates))
        AetherCard {
            when {
                update != null -> {
                    Text(
                        stringResource(R.string.about_update_ready, version, update.version),
                        style = AetherText.body,
                        color = Palette.Green,
                    )
                    Spacer(Modifier.height(10.dp))
                    AetherPrimaryButton(
                        text = stringResource(R.string.about_open_release),
                        onClick = { open(update.releaseUrl.ifBlank { "$DEVELOPER_GITHUB/$" + "AetherShield/releases" }) },
                    )
                }
                known != null -> Text(
                    stringResource(R.string.about_up_to_date, known?.version.orEmpty()),
                    style = AetherText.body,
                    color = scheme.onSurfaceVariant,
                )
                else -> Text(
                    stringResource(R.string.about_never_checked),
                    style = AetherText.body,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.about_check_hint),
                style = AetherText.label,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AetherPrimaryButton(
                text = stringResource(R.string.about_check_now),
                onClick = { UpdateScheduler.checkNow(context) },
            )
        }

        AetherCard {
            Text(
                stringResource(R.string.about_license_note),
                style = AetherText.body,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_privacy),
                style = AetherText.body,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.about_copyright),
                style = AetherText.label,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(label: String, url: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.card, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = AetherText.cardTitle.copy(fontSize = 15.sp), color = scheme.onSurface)
            Text(
                url,
                style = AetherText.label,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        AetherChevron()
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AetherText.label, color = scheme.onSurfaceVariant)
        Text(value, style = AetherText.body, color = scheme.onSurface)
    }
}

@Composable
fun AetherBackButton(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(Radius.icon))
            .background(scheme.surfaceContainer)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Text("\u2039", style = AetherText.status.copy(fontSize = 26.sp), color = scheme.onSurface)
    }
}
