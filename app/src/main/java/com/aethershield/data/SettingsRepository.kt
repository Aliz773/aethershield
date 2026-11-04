package com.aethershield.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aethershield.core.IpVersion
import com.aethershield.core.NoizeProfile
import com.aethershield.core.ScanMode
import com.aethershield.core.Transport
import com.aethershield.core.TunnelMode
import com.aethershield.core.VpnSettings
import com.aethershield.core.mayUseMasque
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val modeKey = stringPreferencesKey("mode")
    private val transportKey = stringPreferencesKey("transport")
    private val scanKey = stringPreferencesKey("scan")
    private val noizeKey = stringPreferencesKey("noize")
    private val ipKey = stringPreferencesKey("ip")
    private val regionKey = stringPreferencesKey("psiphon_region")

    val settings: Flow<VpnSettings> = context.dataStore.data.map { p ->
        VpnSettings(
            mode = p[modeKey].toEnumOr(TunnelMode.MASQUE),
            transport = p[transportKey].toEnumOr(Transport.HTTP3_QUIC),
            scan = p[scanKey].toEnumOr(ScanMode.TURBO),
            noize = p[noizeKey].toEnumOr(NoizeProfile.AUTO),
            ip = p[ipKey].toEnumOr(IpVersion.IPV4),
            psiphonRegion = p[regionKey]?.takeIf { it.length == 2 }?.uppercase(),
        )
    }

    suspend fun setMode(mode: TunnelMode) {
        context.dataStore.edit {
            it[modeKey] = mode.name
            // Transport only matters for MASQUE-based modes; keep the stored value valid.
            if (!mode.mayUseMasque) it[transportKey] = Transport.HTTP3_QUIC.name
        }
    }

    suspend fun setTransport(transport: Transport) {
        context.dataStore.edit { it[transportKey] = transport.name }
    }

    suspend fun setScan(scan: ScanMode) {
        context.dataStore.edit { it[scanKey] = scan.name }
    }

    suspend fun setNoize(noize: NoizeProfile) {
        context.dataStore.edit { it[noizeKey] = noize.name }
    }

    suspend fun setIpVersion(ip: IpVersion) {
        context.dataStore.edit { it[ipKey] = ip.name }
    }

    /** Null means "let Psiphon pick", which is the default and the safest on a bad network. */
    suspend fun setPsiphonRegion(region: String?) {
        context.dataStore.edit {
            if (region == null) it.remove(regionKey) else it[regionKey] = region.uppercase()
        }
    }

    /** A stored value can be stale (an enum renamed between versions); fall back quietly. */
    private inline fun <reified T : Enum<T>> String?.toEnumOr(fallback: T): T =
        this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
