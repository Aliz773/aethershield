package com.aethershield.core

import androidx.annotation.StringRes
import com.aethershield.R

@StringRes
fun ConnectionState.labelRes(): Int = when (this) {
    ConnectionState.Disconnected -> R.string.status_disconnected
    ConnectionState.Preparing -> R.string.status_preparing
    ConnectionState.Connecting -> R.string.status_connecting
    is ConnectionState.Connected -> R.string.status_connected
    ConnectionState.Reconnecting -> R.string.status_reconnecting
    ConnectionState.Disconnecting -> R.string.status_disconnecting
    is ConnectionState.Error -> R.string.status_error
}
