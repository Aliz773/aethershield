package com.aethershield.sharing

import java.net.Inet4Address
import java.net.NetworkInterface

/** A local IPv4 address the phone can be reached on from the LAN. */
data class LocalAddress(val interfaceName: String, val ip: String)

object NetworkAddresses {

    /**
     * Every non-loopback IPv4 the phone has, so both a home Wi-Fi and a hotspot (which is a
     * second interface) show up. The tunnel itself is skipped: sharing it would route the
     * guest through the guest's own tunnel.
     */
    fun ipv4(): List<LocalAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
            .flatMap { nif ->
                nif.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    .map { LocalAddress(nif.name, it.hostAddress.orEmpty()) }
            }
            .filter { it.ip.isNotBlank() }
            .distinctBy { it.ip }
    }.getOrElse { emptyList() }
}
