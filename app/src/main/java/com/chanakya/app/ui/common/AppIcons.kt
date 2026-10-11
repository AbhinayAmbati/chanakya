package com.chanakya.app.ui.common

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches app launcher icons.
 *
 * Icons are read once per package and kept in memory. A failed lookup is cached as null
 * (the presence of the key is what marks it cached) so a missing icon is not retried on
 * every recomposition.
 */
object AppIcons {

    private val cache = ConcurrentHashMap<String, ImageBitmap?>()

    /** Load the launcher icon for [packageName], or null if it cannot be read. */
    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        if (cache.containsKey(packageName)) return cache[packageName]

        val icon = withContext(Dispatchers.IO) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                drawable.toBitmap(width = ICON_PIXELS, height = ICON_PIXELS).asImageBitmap()
            } catch (exception: Exception) {
                null
            }
        }
        cache[packageName] = icon
        return icon
    }

    private const val ICON_PIXELS = 128
}

/**
 * A round app icon that falls back to a coloured letter avatar while the icon loads or
 * when it is unavailable.
 */
@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        icon = AppIcons.load(context, packageName)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val current = icon
        if (current != null) {
            Image(
                bitmap = current,
                contentDescription = label,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
            )
        } else {
            Text(
                text = label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
