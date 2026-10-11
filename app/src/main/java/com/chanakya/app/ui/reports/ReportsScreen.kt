package com.chanakya.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chanakya.app.R
import com.chanakya.app.di.LocalAppContainer
import com.chanakya.app.ui.AppViewModelProvider
import com.chanakya.app.ui.common.ByteFormat
import com.chanakya.core.data.model.ReportWindow
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.component.ChanakyaScreenTitle
import com.chanakya.core.designsystem.component.StatRow
import com.chanakya.core.designsystem.component.StatTile
import com.chanakya.core.designsystem.theme.MonoTextStyle
import com.chanakya.core.designsystem.theme.StatusBlocked

/**
 * The Reports screen: today, the last seven days and all time, each with the busiest
 * networks and apps. Every figure is computed locally from the event database.
 */
@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: ReportsViewModel = viewModel(factory = AppViewModelProvider.reports(container))
    val today by viewModel.today.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val allTime by viewModel.allTime.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ChanakyaScreenTitle(
                title = stringResource(R.string.reports_title),
                subtitle = stringResource(R.string.reports_subtitle),
            )
        }

        item { WindowSection(title = stringResource(R.string.reports_today), window = today) }
        item { WindowSection(title = stringResource(R.string.reports_last_7_days), window = week) }
        item { WindowSection(title = stringResource(R.string.reports_all_time), window = allTime) }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** One report window: headline stats plus the busiest networks and apps. */
@Composable
private fun WindowSection(title: String, window: ReportWindow) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ChanakyaCard {
            StatRow {
                StatTile(
                    value = window.blockedCount.toString(),
                    label = stringResource(R.string.reports_blocked),
                    valueColor = StatusBlocked,
                )
                StatTile(
                    value = window.appCount.toString(),
                    label = stringResource(R.string.reports_apps),
                )
                StatTile(
                    value = ByteFormat.human(window.estimatedBytesSaved),
                    label = stringResource(R.string.reports_data_saved),
                )
            }

            if (window.isEmpty) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.reports_no_activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@ChanakyaCard
            }

            if (window.topNetworks.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.reports_top_networks),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                window.topNetworks.forEach { networkCount ->
                    BreakdownRow(
                        name = networkCount.network.name,
                        count = networkCount.count,
                    )
                }
            }

            if (window.topApps.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.reports_top_apps),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                window.topApps.forEach { appCount ->
                    BreakdownRow(
                        name = appCount.app.label,
                        count = appCount.count,
                    )
                }
            }
        }
    }
}

/** A label/count line inside a report card. */
@Composable
private fun BreakdownRow(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = name,
            style = MonoTextStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
