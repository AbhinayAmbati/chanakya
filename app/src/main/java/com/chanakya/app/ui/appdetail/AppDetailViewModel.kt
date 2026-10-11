package com.chanakya.app.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.core.data.model.RuleScope
import com.chanakya.core.data.model.RuleType
import com.chanakya.core.data.model.UserRule
import com.chanakya.core.data.repository.AppDetail
import com.chanakya.core.data.repository.TrackerRepository
import com.chanakya.core.data.repository.UserRuleRepository
import com.chanakya.core.vpn.rules.RuleDecision
import com.chanakya.core.vpn.rules.UserRuleSet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The state rendered by the app detail screen.
 *
 * @param detail everything known about the app.
 * @param ruleByDomain for each resolved domain, the user rule (if any) that governs it.
 *   This is what lets the UI show that a domain is already allowed or blocked by the
 *   user rather than only by the blocklist.
 */
data class AppDetailUiState(
    val detail: AppDetail?,
    val ruleByDomain: Map<String, RuleType>,
) {
    companion object {
        val EMPTY = AppDetailUiState(detail = null, ruleByDomain = emptyMap())
    }
}

/**
 * Loads everything known about a single app and lets the user act on what they see by
 * creating allow/block rules.
 *
 * @param trackerRepository the read model.
 * @param userRuleRepository where new rules are written, and the source of rule state.
 * @param packageName the app to show.
 */
class AppDetailViewModel(
    trackerRepository: TrackerRepository,
    private val userRuleRepository: UserRuleRepository,
    private val packageName: String,
) : ViewModel() {

    /** The detail payload plus which domains are governed by a user rule. */
    val uiState: StateFlow<AppDetailUiState> = combine(
        trackerRepository.observeAppDetail(packageName),
        userRuleRepository.observeRules(),
    ) { detail, rules ->
        AppDetailUiState(
            detail = detail,
            ruleByDomain = detail.resolveRules(rules, packageName),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppDetailUiState.EMPTY,
    )

    /** Block this domain in every app. */
    fun blockEverywhere(domain: String) = addRule(RuleType.BLOCK, RuleScope.GLOBAL, domain, null)

    /** Allow this domain in every app. */
    fun allowEverywhere(domain: String) = addRule(RuleType.ALLOW, RuleScope.GLOBAL, domain, null)

    /** Block this domain only for the app being viewed. */
    fun blockForApp(domain: String) = addRule(RuleType.BLOCK, RuleScope.APP, domain, packageName)

    /** Allow this domain only for the app being viewed. */
    fun allowForApp(domain: String) = addRule(RuleType.ALLOW, RuleScope.APP, domain, packageName)

    /** Persist a new rule. */
    private fun addRule(type: RuleType, scope: RuleScope, domain: String, owner: String?) {
        viewModelScope.launch {
            userRuleRepository.add(
                UserRule(type = type, scope = scope, domain = domain, packageName = owner),
            )
        }
    }
}

/**
 * Evaluate the user rules against every domain this app resolved, so the UI can mark
 * the ones the user has already ruled on.
 */
private fun AppDetail.resolveRules(
    rules: List<UserRule>,
    packageName: String,
): Map<String, RuleType> {
    val ruleSet = UserRuleSet(rules)
    if (ruleSet.isEmpty) return emptyMap()

    return buildMap {
        for (domain in events.map { it.domain }.distinct()) {
            when (ruleSet.evaluate(domain, packageName)) {
                RuleDecision.ALLOW -> put(domain, RuleType.ALLOW)
                RuleDecision.BLOCK -> put(domain, RuleType.BLOCK)
                null -> Unit // no rule applies
            }
        }
    }
}
