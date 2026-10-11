package com.chanakya.app.ui.common

import androidx.compose.ui.graphics.Color
import com.chanakya.core.data.model.TrackerCategory
import com.chanakya.core.designsystem.theme.CategoryAdNetworks
import com.chanakya.core.designsystem.theme.CategoryAnalytics
import com.chanakya.core.designsystem.theme.CategoryFingerprinting
import com.chanakya.core.designsystem.theme.CategoryOther
import com.chanakya.core.designsystem.theme.CategorySocialPixels

/**
 * Maps a tracker category to its accent colour from the palette.
 *
 * This lives in the app module because it bridges the data layer (which defines the
 * category) and the design system (which defines the colours), neither of which should
 * depend on the other.
 */
fun TrackerCategory.accentColor(): Color = when (this) {
    TrackerCategory.AD_NETWORKS -> CategoryAdNetworks
    TrackerCategory.ANALYTICS -> CategoryAnalytics
    TrackerCategory.SOCIAL_PIXELS -> CategorySocialPixels
    TrackerCategory.FINGERPRINTING -> CategoryFingerprinting
    TrackerCategory.OTHER -> CategoryOther
}
