package com.chanakya.app.ui.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chanakya.app.R
import com.chanakya.app.di.LocalAppContainer
import com.chanakya.app.ui.AppViewModelProvider
import com.chanakya.app.ui.common.AppRow
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.component.ChanakyaPrimaryButton
import com.chanakya.core.designsystem.component.ChanakyaScreenTitle
import com.chanakya.core.designsystem.component.ChanakyaSecondaryButton
import com.chanakya.core.designsystem.component.SectionHeader
import com.chanakya.core.designsystem.component.StatRow
import com.chanakya.core.designsystem.component.StatTile
import com.chanakya.core.designsystem.theme.MintGlow
import com.chanakya.core.designsystem.theme.StatusBlocked

/**
 * The Home dashboard: the protection status, today's totals and the busiest apps.
 *
 * @param onSeeAllApps navigates to the full Apps list.
 */
@Composable
fun HomeScreen(
    onSeeAllApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.home(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Shows the system VPN consent dialog the first time protection is enabled.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.startProtection()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ChanakyaScreenTitle(
                title = stringResource(R.string.home_title),
                subtitle = stringResource(R.string.home_subtitle),
            )
        }

        item {
            ProtectionCard(
                isProtected = state.isProtected,
                blockedToday = state.summary.blockedToday,
                onTurnOn = {
                    val intent = viewModel.consentIntent()
                    if (intent != null) consentLauncher.launch(intent) else viewModel.startProtection()
                },
                onTurnOff = viewModel::stopProtection,
            )
        }

        item {
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
                    StatTile(
                        value = state.summary.blockedTotal.toString(),
                        label = stringResource(R.string.home_blocked_total),
                    )
                }
            }
        }

        val topNetwork = state.summary.topNetwork
        if (topNetwork != null) {
            item {
                ChanakyaCard {
                    Text(
                        text = stringResource(R.string.home_top_network),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = topNetwork.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.home_top_offenders),
                trailing = {
                    Text(
                        text = stringResource(R.string.home_see_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                },
            )
        }

        items(state.topOffenders, key = { it.app.packageName }) { summary ->
            AppRow(summary = summary, onClick = onSeeAllApps)
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * The large protection-status card. Mint when protected, ice-blue when off.
 */
@Composable
private fun ProtectionCard(
    isProtected: Boolean,
    blockedToday: Int,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (isProtected) MintGlow else MaterialTheme.colorScheme.surface
    ChanakyaCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .padding(20.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isProtected) stringResource(R.string.home_protected)
                else stringResource(R.string.home_not_protected),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (isProtected) stringResource(R.string.home_protection_on_body)
                else stringResource(R.string.home_protection_off_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isProtected) {
                    ChanakyaSecondaryButton(
                        text = stringResource(R.string.home_turn_off),
                        onClick = onTurnOff,
                    )
                } else {
                    ChanakyaPrimaryButton(
                        text = stringResource(R.string.home_turn_on),
                        onClick = onTurnOn,
                    )
                }
            }
        }
    }
}
