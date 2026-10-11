package com.chanakya.core.data.model

/**
 * How the app decides between the light and dark colour schemes.
 */
enum class ThemeMode {
    /** Follow the system setting. */
    SYSTEM,

    /** Always use the light scheme. */
    LIGHT,

    /** Always use the dark scheme. */
    DARK,
}
