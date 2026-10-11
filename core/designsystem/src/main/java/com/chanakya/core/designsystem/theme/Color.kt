package com.chanakya.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The Chanakya colour palette.
 *
 * These raw values are the canonical brand colours. UI code should prefer the
 * semantic aliases exposed through [ChanakyaColors] / MaterialTheme, but may use
 * these directly for data-driven colouring (e.g. tracker category accents).
 */

// ---- Core -----------------------------------------------------------------
/** App background. */
val White = Color(0xFFFFFFFF)

/** Cards and raised surfaces. */
val Ice = Color(0xFFF2F7FF)

/** Primary brand colour - buttons, active states. */
val BrightMint = Color(0xFF00D9A5)

/** Secondary brand colour - links, accents. */
val SkyBlue = Color(0xFF33B8FF)

/** Soft fill used to communicate a "protected" state. */
val MintGlow = Color(0xFFDDFBF2)

// ---- Status ---------------------------------------------------------------
/** Traffic that was blocked. */
val StatusBlocked = Color(0xFF00D9A5)

/** Something suspicious that was detected but allowed. */
val StatusDetected = Color(0xFFFFC53D)

/** Unusually high activity from an app. */
val StatusHighActivity = Color(0xFFFF6B8A)

/** Traffic that was allowed through. */
val StatusAllowed = Color(0xFFDCE4EE)

// ---- Tracker categories ---------------------------------------------------
/** Ad networks. */
val CategoryAdNetworks = Color(0xFFFF8A5C)

/** Analytics providers. */
val CategoryAnalytics = Color(0xFF9B7BFF)

/** Social pixels and social SDKs. */
val CategorySocialPixels = Color(0xFF4DA3FF)

/** Fingerprinting services. */
val CategoryFingerprinting = Color(0xFFFF6FB5)

/** Anything that does not fit the categories above. */
val CategoryOther = Color(0xFFFFE14D)

// ---- Text -----------------------------------------------------------------
/** Headings and body copy. */
val InkSlate = Color(0xFF2B3A4A)

/** Captions, hints and secondary text. */
val SoftSlate = Color(0xFF5B6B7F)
