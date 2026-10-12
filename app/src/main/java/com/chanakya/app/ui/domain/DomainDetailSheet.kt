package com.chanakya.app.ui.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.chanakya.app.ui.common.TimeFormat
import com.chanakya.app.ui.common.accentColor
import com.chanakya.core.data.model.DomainDetail
import com.chanakya.core.designsystem.component.CategoryChip
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.component.ChanakyaPrimaryButton
import com.chanakya.core.designsystem.component.ChanakyaSecondaryButton
import com.chanakya.core.designsystem.component.StatRow
import com.chanakya.core.designsystem.component.StatTile
import com.chanakya.core.designsystem.theme.MonoTextStyle
import com.chanakya.core.designsystem.theme.StatusBlocked
import com.chanakya.core.designsystem.theme.StatusDetected

/**
 * The domain detail sheet.
 *
 * A sheet rather than a screen on purpose: a domain is one piece of information plus a
 * couple of actions, and giving it a whole screen would push the navigation three and
 * four levels deep. It swipes away, and it is reachable from both the app detail screen
 * and a network's own screen.
 *
 * @param domain the domain to show.
 * @param onDismiss called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainDetailSheet(
    domain: String,
    onDismiss: () -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: DomainDetailViewModel = viewModel(
        key = domain,
        factory = AppViewModelProvider.domainDetail(container, domain),
    )
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = domain,
                style = MonoTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val current = detail
            if (current == null) {
                Text(
                    text = stringResource(R.string.domain_detail_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            current.network?.let { network ->
                CategoryChip(
                    label = "${network.name} - ${network.category.displayName}",
                    color = network.category.accentColor(),
                )
            }

            ChanakyaCard {
                StatRow {
                    StatTile(
                        value = current.blockedCount.toString(),
                        label = stringResource(R.string.app_detail_blocked),
                        valueColor = StatusBlocked,
                    )
                    StatTile(
                        value = current.allowedCount.toString(),
                        label = stringResource(R.string.app_detail_allowed),
                        valueColor = StatusDetected,
                    )
                    StatTile(
                        value = current.apps.size.toString(),
                        label = stringResource(R.string.network_detail_apps),
                    )
                }
                Spacer(Modifier.height(16.dp))
                DetailLine(
                    label = stringResource(R.string.domain_first_seen),
                    value = TimeFormat.relative(current.firstSeen),
                )
                DetailLine(
                    label = stringResource(R.string.domain_last_seen_label),
                    value = TimeFormat.relative(current.lastSeen),
                )
            }

            if (current.apps.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.domain_asked_by),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                current.apps.take(APPS_SHOWN).forEach { appCount ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appCount.app.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = appCount.count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChanakyaPrimaryButton(
                    text = stringResource(R.string.rule_block_everywhere),
                    onClick = { viewModel.blockEverywhere(); onDismiss() },
                )
                ChanakyaSecondaryButton(
                    text = stringResource(R.string.rule_allow_everywhere),
                    onClick = { viewModel.allowEverywhere(); onDismiss() },
                )
            }
        }
    }
}

/** A label on the left and a value on the right. */
@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** How many apps the sheet lists before it stops; the rest are on the network screen. */
private const val APPS_SHOWN = 5
