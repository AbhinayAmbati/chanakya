package com.chanakya.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chanakya.core.data.model.AppUsageSummary
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.component.StatusBadge
import com.chanakya.core.designsystem.theme.StatusBlocked

/**
 * A single row in the Apps list: the app's icon, its name, the top tracker networks it
 * contacted, and its blocked count.
 *
 * @param summary the app's aggregated activity.
 * @param onClick invoked when the row is tapped.
 */
@Composable
fun AppRow(
    summary: AppUsageSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChanakyaCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = summary.app.packageName, label = summary.app.label)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = summary.app.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary.networks.take(2).joinToString(" - ") { it.network.name }
                        .ifEmpty { "No known network" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            StatusBadge(
                text = "${summary.blockedCount} blocked",
                color = StatusBlocked,
            )
        }
    }
}
