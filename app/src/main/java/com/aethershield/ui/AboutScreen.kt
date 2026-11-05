package com.aethershield.ui

import androidx.compose.foundation.background
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
import com.aethershield.ui.components.AetherTopBar
import com.aethershield.ui.components.GlyphPower
import com.aethershield.ui.theme.AetherText
import com.aethershield.ui.theme.Radius
import com.aethershield.ui.theme.Space

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
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
            back = {
                AetherBackButton(onBack)
            },
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
            Text(stringResource(R.string.app_name), style = AetherText.screenTitle.copy(fontSize = 24.sp), color = scheme.onBackground)
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
        }
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
            .padding(0.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Text("\u2039", style = AetherText.status.copy(fontSize = 26.sp), color = scheme.onSurface)
        }
    }
}
