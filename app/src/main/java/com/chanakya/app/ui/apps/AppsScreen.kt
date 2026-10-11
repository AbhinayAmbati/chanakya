package com.chanakya.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.chanakya.app.ui.common.AppRow
import com.chanakya.core.designsystem.component.ChanakyaScreenTitle
import com.chanakya.core.designsystem.component.EmptyState

/**
 * The Apps list: every application that contacted a tracker, ranked by activity.
 *
 * @param onAppClick navigates to the detail screen for the tapped app.
 */
@Composable
fun AppsScreen(
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: AppsViewModel = viewModel(factory = AppViewModelProvider.apps(container))
    val apps by viewModel.apps.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ChanakyaScreenTitle(
                title = stringResource(R.string.apps_title),
                subtitle = stringResource(R.string.apps_subtitle),
            )
        }

        if (apps.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.apps_empty_title),
                    message = stringResource(R.string.apps_empty_body),
                )
            }
        } else {
            items(apps, key = { it.app.packageName }) { summary ->
                AppRow(summary = summary, onClick = { onAppClick(summary.app.packageName) })
            }
        }

        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp)) }
    }
}
