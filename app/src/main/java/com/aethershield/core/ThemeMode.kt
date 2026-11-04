package com.aethershield.core

/**
 * Four looks, no more. [SYSTEM] is the default because a phone that is already set to dark
 * or light should not be argued with; the other three are explicit choices.
 */
enum class ThemeMode {
    /** Follow the phone. */
    SYSTEM,

    LIGHT,

    DARK,

    /** Dark, but with true black surfaces, for AMOLED panels. */
    AMOLED,
}
