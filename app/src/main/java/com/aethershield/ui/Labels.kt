package com.aethershield.ui

import androidx.annotation.StringRes
import com.aethershield.R
import com.aethershield.core.IpVersion
import com.aethershield.core.NoizeProfile
import com.aethershield.core.ScanMode
import com.aethershield.core.ThemeMode
import com.aethershield.core.Transport
import com.aethershield.core.TunnelMode

/** One place for "enum value → label", so no two screens can drift apart. */

@StringRes
internal fun TunnelMode.labelRes(): Int = when (this) {
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
internal fun Transport.labelRes(): Int = when (this) {
    Transport.HTTP3_QUIC -> R.string.transport_h3
    Transport.HTTP2_TCP -> R.string.transport_h2
}

@StringRes
internal fun ScanMode.labelRes(): Int = when (this) {
    ScanMode.TURBO -> R.string.scan_turbo
    ScanMode.BALANCED -> R.string.scan_balanced
    ScanMode.THOROUGH -> R.string.scan_thorough
    ScanMode.VERIFIED -> R.string.scan_verified
    ScanMode.IRONCLAD -> R.string.scan_ironclad
}

@StringRes
internal fun NoizeProfile.labelRes(): Int = when (this) {
    NoizeProfile.AUTO -> R.string.noize_auto
    NoizeProfile.OFF -> R.string.noize_off
    NoizeProfile.LIGHT -> R.string.noize_light
    NoizeProfile.FIREWALL -> R.string.noize_firewall
    NoizeProfile.BALANCED -> R.string.noize_balanced
    NoizeProfile.GFW -> R.string.noize_gfw
    NoizeProfile.AGGRESSIVE -> R.string.noize_aggressive
}

@StringRes
internal fun IpVersion.labelRes(): Int = when (this) {
    IpVersion.IPV4 -> R.string.ip_v4
    IpVersion.IPV6 -> R.string.ip_v6
    IpVersion.DUAL -> R.string.ip_dual
}

@StringRes
internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.AMOLED -> R.string.theme_amoled
}
