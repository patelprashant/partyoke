package com.partyoke.tv.net

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /**
     * Returns the device's local IPv4 address on the active (Wi-Fi/Ethernet)
     * network, or null if it cannot be determined. Phones use this to reach the
     * embedded server, so it must be the LAN address, not loopback.
     */
    fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
