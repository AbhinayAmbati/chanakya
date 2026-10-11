package com.chanakya.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chanakya.app.BuildConfig
import com.chanakya.app.R
import com.chanakya.app.di.LocalAppContainer
import com.chanakya.app.ui.AppViewModelProvider
import com.chanakya.core.data.model.BlocklistInfo
import com.chanakya.core.data.model.RuleType
import com.chanakya.core.data.model.UserRule
import com.chanakya.core.designsystem.component.ChanakyaCard
import com.chanakya.core.designsystem.component.ChanakyaScreenTitle
import com.chanakya.core.designsystem.component.ChanakyaSecondaryButton
import com.chanakya.core.designsystem.component.SectionHeader
import com.chanakya.core.designsystem.theme.MonoTextStyle

/**
 * The Settings screen: protection options, blocklists, user rules, the advertising-ID
 * tools and an about section.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.settings(container))
    val context = LocalContext.current

    val autoStart by viewModel.autoStartOnBoot.collectAsStateWithLifecycle()
    val blockIp by viewModel.blockIpLevel.collectAsStateWithLifecycle()
    val adIdReminder by viewModel.adIdReminder.collectAsStateWithLifecycle()
    val blocklists by viewModel.blocklists.collectAsStateWithLifecycle()
    val userRules by viewModel.userRules.collectAsStateWithLifecycle()

    // Lets the user pick a hosts/domain file to import.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBlocklist) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ChanakyaScreenTitle(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
            )
        }

        item { SectionHeader(title = stringResource(R.string.settings_protection)) }
        item {
            ChanakyaCard {
                SettingToggle(
                    title = stringResource(R.string.settings_auto_start),
                    description = stringResource(R.string.settings_auto_start_desc),
                    checked = autoStart,
                    onCheckedChange = viewModel::setAutoStartOnBoot,
                )
                Spacer(Modifier.height(16.dp))
                SettingToggle(
                    title = stringResource(R.string.settings_ip_blocking),
                    description = stringResource(R.string.settings_ip_blocking_desc),
                    checked = blockIp,
                    onCheckedChange = viewModel::setBlockIpLevel,
                )
            }
        }

        item { SectionHeader(title = stringResource(R.string.settings_blocklists)) }
        items(blocklists, key = { it.id }) { list ->
            BlocklistRow(
                list = list,
                onToggle = { enabled -> viewModel.setBlocklistEnabled(list.id, enabled) },
            )
        }
        item {
            ChanakyaCard {
                Text(
                    text = stringResource(R.string.settings_blocklists_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ChanakyaSecondaryButton(
                    text = stringResource(R.string.settings_import),
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                )
            }
        }

        item { SectionHeader(title = stringResource(R.string.settings_rules)) }
        if (userRules.isEmpty()) {
            item {
                ChanakyaCard {
                    Text(
                        text = stringResource(R.string.settings_rules_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(userRules, key = { it.id }) { rule ->
                RuleRow(rule = rule, onDelete = { viewModel.deleteRule(rule.id) })
            }
        }

        item { SectionHeader(title = stringResource(R.string.settings_ad_id)) }
        item {
            ChanakyaCard {
                Text(
                    text = stringResource(R.string.settings_ad_id_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ChanakyaSecondaryButton(
                    text = stringResource(R.string.settings_ad_id_action),
                    onClick = { context.openAdSettings() },
                )
                Spacer(Modifier.height(16.dp))
                SettingToggle(
                    title = stringResource(R.string.settings_ad_id_reminder),
                    description = stringResource(R.string.settings_ad_id_reminder_desc),
                    checked = adIdReminder,
                    onCheckedChange = viewModel::setAdIdReminder,
                )
            }
        }

        item { SectionHeader(title = stringResource(R.string.settings_about)) }
        item {
            ChanakyaCard {
                Text(
                    text = stringResource(R.string.settings_about_desc, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** A labelled toggle row. */
@Composable
private fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** One blocklist with its entry count and an enable switch. */
@Composable
private fun BlocklistRow(list: BlocklistInfo, onToggle: (Boolean) -> Unit) {
    ChanakyaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${list.description} " +
                        stringResource(R.string.settings_domains_count, list.entryCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = list.enabled, onCheckedChange = onToggle)
        }
    }
}

/** One user rule with its scope and a delete action. */
@Composable
private fun RuleRow(rule: UserRule, onDelete: () -> Unit) {
    ChanakyaCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.domain,
                    style = MonoTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                val typeLabel = stringResource(
                    if (rule.type == RuleType.BLOCK) R.string.rule_type_block else R.string.rule_type_allow,
                )
                val scopeLabel = rule.packageName
                    ?: stringResource(R.string.rule_scope_everywhere)
                Text(
                    text = "$typeLabel - $scopeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.settings_rule_delete),
                )
            }
        }
    }
}

/**
 * Open Android's advertising-ID settings, falling back to the general settings screen
 * on devices where the privacy-settings activity is unavailable.
 */
private fun android.content.Context.openAdSettings() {
    val candidates = listOf(
        Intent("android.settings.PRIVACY_SETTINGS"),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            return
        } catch (exception: Exception) {
            // Try the next candidate.
        }
    }
}
