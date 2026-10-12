package com.chanakya.app.ui.networkdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chanakya.app.R
import com.chanakya.app.di.LocalAppContainer
import com.chanakya.app.ui.AppViewModelProvider
import com.chanakya.app.ui.common.AppIcon
import com.chanakya.app.ui.common.TimeFormat
import com.chanakya.app.ui.common.accentColor
import com.chanakya.app.ui.domain.DomainDetailSheet
import com.chanakya.core.data.model.DomainSummary
import com.chanakya.core.designsystem.component.CategoryChip
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.component.SectionHeader
import com.chanakya.core.designsystem.component.StatRow
import com.chanakya.core.designsystem.component.StatTile
import com.chanakya.core.designsystem.theme.MonoTextStyle
import com.chanakya.core.designsystem.theme.StatusBlocked

/**
 * The cross-app view of one tracker network: how much of the phone talks to this company,
 * which domains are involved, and which apps are responsible.
 *
 * The per-app question is answered by the app detail screen; this one answers "who is
 * this, and how far does it reach".
 *
 * @param networkId the catalogue id of the network.
 * @param onBack returns to the previous screen.
 */
@Composable
fun NetworkDetailScreen(
    networkId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: NetworkDetailViewModel =
        viewModel(factory = AppViewModelProvider.networkDetail(container, networkId))
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    // Tapping a domain opens the shared domain sheet, so the same information and the
    // same actions are reachable from here and from an app's own screen.
    var selectedDomain by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
            }
        }

        val current = detail
        if (current == null) {
            item {
                Text(
                    text = stringResource(R.string.network_detail_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
            return@LazyColumn
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = current.network.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                CategoryChip(
                    label = current.network.category.displayName,
                    color = current.network.category.accentColor(),
                )
            }
        }

        item {
            ChanakyaCard {
                StatRow {
                    StatTile(
                        value = current.blockedCount.toString(),
                        label = stringResource(R.string.app_detail_blocked),
                        valueColor = StatusBlocked,
                    )
                    StatTile(
                        value = current.appCount.toString(),
                        label = stringResource(R.string.network_detail_apps),
                    )
                    StatTile(
                        value = current.domains.size.toString(),
                        label = stringResource(R.string.network_detail_domains),
                    )
                }
            }
        }

        if (current.domains.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.app_detail_domains)) }
            items(current.domains, key = { it.domain }) { summary ->
                DomainSummaryRow(
                    summary = summary,
                    onClick = { selectedDomain = summary.domain },
                )
            }
        }

        if (current.apps.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.network_detail_apps_heading)) }
            items(current.apps, key = { it.app.packageName }) { appCount ->
                ChanakyaCard(contentPadding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            packageName = appCount.app.packageName,
                            label = appCount.app.label,
                            size = 40.dp,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = appCount.app.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = appCount.count.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    val domain = selectedDomain
    if (domain != null) {
        DomainDetailSheet(
            domain = domain,
            onDismiss = { selectedDomain = null },
        )
    }
}

/** A domain inside a network, with how often it was seen. */
@Composable
private fun DomainSummaryRow(
    summary: DomainSummary,
    onClick: () -> Unit,
) {
    ChanakyaCard(onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.domain,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.domain_last_seen,
                        TimeFormat.relative(summary.lastSeen),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = summary.count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
