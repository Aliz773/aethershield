package com.aethershield.core

/**
 * The egress regions Psiphon actually serves. Asking for anything else makes the core fail to
 * bring the tunnel up and the app retry forever, so the picker is limited to this list and the
 * value is validated again before it reaches the command line.
 *
 * Kept as a list (not derived from the platform) because it is a property of the Psiphon
 * network, not of the phone.
 */
object PsiphonCountries {

    val codes: List<String> = listOf(
        "AT", "AU", "BE", "CA", "CH", "CZ", "DE", "DK", "ES", "FI", "FR", "GB", "ID",
        "IE", "IN", "IT", "JP", "LT", "NL", "NO", "PL", "RO", "RS", "SE", "SG", "US",
    )

    fun supports(code: String?): Boolean =
        code != null && code.length == 2 && code.uppercase() in codes
}
