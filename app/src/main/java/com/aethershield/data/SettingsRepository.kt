package com.aethershield.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aethershield.core.Transport
import com.aethershield.core.TunnelMode
import com.aethershield.core.VpnSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val modeKey = stringPreferencesKey("mode")
    private val transportKey = stringPreferencesKey("transport")

    val settings: Flow<VpnSettings> = context.dataStore.data.map { p ->
        VpnSettings(
            mode = p[modeKey]?.let { runCatching { TunnelMode.valueOf(it) }.getOrNull() }
                ?: TunnelMode.MASQUE,
            transport = p[transportKey]?.let { runCatching { Transport.valueOf(it) }.getOrNull() }
                ?: Transport.HTTP3_QUIC,
        )
    }

    suspend fun setMode(mode: TunnelMode) {
        context.dataStore.edit {
            it[modeKey] = mode.name
            // Transport only matters for MASQUE-based modes; keep the stored value valid.
            if (!mode.usesMasque) it[transportKey] = Transport.HTTP3_QUIC.name
        }
    }

    suspend fun setTransport(transport: Transport) {
        context.dataStore.edit { it[transportKey] = transport.name }
    }
}
