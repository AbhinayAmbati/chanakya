package com.chanakya.app.ui.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.chanakya.core.data.model.BlockedEvent
import com.chanakya.core.data.model.RuleType
import com.chanakya.core.designsystem.component.CategoryChip
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.component.SectionHeader
import com.chanakya.core.designsystem.component.StatRow
import com.chanakya.core.designsystem.component.StatTile
import com.chanakya.core.designsystem.theme.MonoTextStyle
import com.chanakya.core.designsystem.theme.StatusBlocked
import com.chanakya.core.designsystem.theme.StatusDetected

/**
 * The per-app detail screen: how much this app tracked, which networks it contacted,
 * and the individual domains it resolved. Domains already governed by a user rule are
 * marked, and each can be acted on to create a new rule.
 *
 * @param packageName the app to display.
 * @param onBack returns to the previous screen.
 */
@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    onOpenNetwork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: AppDetailViewModel =
        viewModel(factory = AppViewModelProvider.appDetail(container, packageName))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = state.detail

    // Tapping a domain opens the shared sheet rather than another screen: a domain is a
    // little information and a couple of actions, and a third navigation level would
    // cost more than it gives.
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
                        contentDescription = stringResource(R.string.app_detail_back),
                    )
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(
                    packageName = packageName,
                    label = detail?.app?.label ?: packageName,
                    size = 56.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = detail?.app?.label ?: packageName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = packageName,
                        style = MonoTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            ChanakyaCard {
                StatRow {
                    StatTile(
                        value = (detail?.blockedCount ?: 0).toString(),
                        label = stringResource(R.string.app_detail_blocked),
                        valueColor = StatusBlocked,
                    )
                    StatTile(
                        value = (detail?.allowedCount ?: 0).toString(),
                        label = stringResource(R.string.app_detail_allowed),
                        valueColor = StatusDetected,
                    )
                    StatTile(
                        value = (detail?.networks?.size ?: 0).toString(),
                        label = stringResource(R.string.app_detail_networks),
                    )
                }
            }
        }

        val networks = detail?.networks.orEmpty()
        if (networks.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.app_detail_networks)) }
            items(networks, key = { it.network.id }) { networkCount ->
                ChanakyaCard(onClick = { onOpenNetwork(networkCount.network.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = networkCount.network.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            CategoryChip(
                                label = networkCount.network.category.displayName,
                                color = networkCount.network.category.accentColor(),
                            )
                        }
                        Text(
                            text = "${networkCount.count}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        item { SectionHeader(title = stringResource(R.string.app_detail_domains)) }

        val events = detail?.events.orEmpty()
        if (events.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.app_detail_no_domains),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(events, key = { it.id }) { event ->
                DomainRow(
                    event = event,
                    ruleType = state.ruleByDomain[event.domain],
                    onOpen = { selectedDomain = event.domain },
                    onBlockEverywhere = { viewModel.blockEverywhere(event.domain) },
                    onAllowEverywhere = { viewModel.allowEverywhere(event.domain) },
                    onBlockForApp = { viewModel.blockForApp(event.domain) },
                    onAllowForApp = { viewModel.allowForApp(event.domain) },
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    val domain = selectedDomain
    if (domain != null) {
        DomainDetailSheet(domain = domain, onDismiss = { selectedDomain = null })
    }
}

/**
 * A single resolved domain. When a user rule governs it, the rule is shown so the user
 * can see why it is allowed or blocked.
 */
@Composable
private fun DomainRow(
    event: BlockedEvent,
    ruleType: RuleType?,
    onOpen: () -> Unit,
    onBlockEverywhere: () -> Unit,
    onAllowEverywhere: () -> Unit,
    onBlockForApp: () -> Unit,
    onAllowForApp: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ChanakyaCard(onClick = onOpen, contentPadding = PaddingValues(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.domain,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TimeFormat.relative(event.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (ruleType != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (ruleType == RuleType.ALLOW) {
                                    R.string.rule_applied_allow
                                } else {
                                    R.string.rule_applied_block
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (ruleType == RuleType.ALLOW) {
                                MaterialTheme.colorScheme.secondary
                            } else {
                                StatusBlocked
                            },
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.app_detail_rule_menu),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rule_block_everywhere)) },
                        onClick = { menuOpen = false; onBlockEverywhere() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rule_allow_everywhere)) },
                        onClick = { menuOpen = false; onAllowEverywhere() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rule_block_for_app)) },
                        onClick = { menuOpen = false; onBlockForApp() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rule_allow_for_app)) },
                        onClick = { menuOpen = false; onAllowForApp() },
                    )
                }
            }
        }
    }
}
