package com.chanakya.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chanakya.app.R
import com.chanakya.app.di.LocalAppContainer
import com.chanakya.app.ui.AppViewModelProvider
import com.chanakya.app.ui.home.HomeViewModel
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.component.ChanakyaScreenTitle
import com.chanakya.core.designsystem.component.StatRow
import com.chanakya.core.designsystem.component.StatTile
import com.chanakya.core.designsystem.theme.StatusBlocked

/**
 * The Reports screen. It currently reuses the protection summary; richer daily and
 * weekly aggregates arrive with the reporting worker.
 */
@Composable
fun ReportsScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.home(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.reports_today),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                ChanakyaCard {
                    StatRow {
                        StatTile(
                            value = state.summary.blockedToday.toString(),
                            label = stringResource(R.string.home_blocked_today),
                            valueColor = StatusBlocked,
                        )
                        StatTile(
                            value = state.summary.appsTrackedToday.toString(),
                            label = stringResource(R.string.home_apps_tracked),
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Text(
                    text = stringResource(R.string.reports_all_time),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                ChanakyaCard {
                    StatRow {
                        StatTile(
                            value = state.summary.blockedTotal.toString(),
                            label = stringResource(R.string.home_blocked_total),
                            valueColor = StatusBlocked,
                        )
                        StatTile(
                            value = state.topOffenders.firstOrNull()?.app?.label ?: "-",
                            label = stringResource(R.string.reports_top_apps),
                        )
                    }
                }
            }
        }

        item {
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}
