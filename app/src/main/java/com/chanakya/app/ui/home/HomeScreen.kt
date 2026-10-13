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
import com.chanakya.core.data.model.BlockedEvent
import com.chanakya.core.vpn.model.VpnStatus
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.component.ChanakyaPrimaryButton
import com.chanakya.core.designsystem.component.ChanakyaScreenTitle
import com.chanakya.core.designsystem.component.ChanakyaSecondaryButton
import com.chanakya.core.designsystem.component.SectionHeader
import com.chanakya.core.designsystem.component.StatRow
import com.chanakya.core.designsystem.component.StatTile
import com.chanakya.core.designsystem.component.StatusBadge
import com.chanakya.core.designsystem.theme.InkSlate
import com.chanakya.core.designsystem.theme.MintGlow
import com.chanakya.core.designsystem.theme.MonoTextStyle
import com.chanakya.core.designsystem.theme.StatusBlocked
import com.chanakya.core.designsystem.theme.StatusDetected
import com.chanakya.core.designsystem.theme.SoftSlate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow

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

    // Read straight from the engine, the same way Settings does, so the warning below can
    // reflect what the tunnel actually observed rather than a second guess at it.
    val diagnostics by VpnStatus.diagnostics.collectAsStateWithLifecycle()

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

        // The Private DNS trap, said out loud. Without this the app simply under-performs
        // - or, on some phones, the connection drops - with nothing on screen to explain
        // why, which reads as a broken app rather than a setting to change.
        if (state.isProtected && diagnostics.privateDnsActive) {
            item { PrivateDnsWarning() }
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

        if (state.recentEvents.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(R.string.home_recent_activity))
            }
            items(state.recentEvents, key = { it.id }) { event ->
                RecentActivityRow(event)
            }
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
    // The protected card is a fixed light mint in *both* themes, so its text has to be
    // a fixed dark colour too. Taking it from the theme gave white-on-white in dark
    // mode. When protection is off the card follows the theme, so theme colours are
    // right there.
    val titleColor = if (isProtected) InkSlate else MaterialTheme.colorScheme.onSurface
    val bodyColor = if (isProtected) SoftSlate else MaterialTheme.colorScheme.onSurfaceVariant
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
                color = titleColor,
            )
            Text(
                text = if (isProtected) stringResource(R.string.home_protection_on_body)
                else stringResource(R.string.home_protection_off_body),
                style = MaterialTheme.typography.bodyMedium,
                color = bodyColor,
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

/**
 * Shown while Android's Private DNS is on, because DNS filtering cannot work then.
 */
@Composable
private fun PrivateDnsWarning() {
    ChanakyaCard {
        Text(
            text = stringResource(R.string.home_private_dns_title),
            style = MaterialTheme.typography.titleSmall,
            color = StatusDetected,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_private_dns_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One line of live activity: the queried domain and whether it was blocked.
 */
@Composable
private fun RecentActivityRow(event: BlockedEvent) {
    ChanakyaCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = event.domain,
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            StatusBadge(
                text = stringResource(
                    if (event.blocked) R.string.status_blocked else R.string.status_allowed,
                ),
                color = if (event.blocked) StatusBlocked else StatusDetected,
            )
        }
    }
}
