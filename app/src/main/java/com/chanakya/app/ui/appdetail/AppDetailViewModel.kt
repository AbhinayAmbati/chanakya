package com.chanakya.app.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.core.data.model.RuleScope
import com.chanakya.core.data.model.RuleType
import com.chanakya.core.data.model.UserRule
import com.chanakya.core.data.repository.AppDetail
import com.chanakya.core.data.repository.TrackerRepository
import com.chanakya.core.data.repository.UserRuleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Loads everything known about a single app and lets the user act on what they see by
 * creating allow/block rules.
 *
 * @param trackerRepository the read model.
 * @param userRuleRepository where new rules are written.
 * @param packageName the app to show.
 */
class AppDetailViewModel(
    trackerRepository: TrackerRepository,
    private val userRuleRepository: UserRuleRepository,
    private val packageName: String,
) : ViewModel() {

    /** The detail payload, or null while it is first loading. */
    val detail: StateFlow<AppDetail?> = trackerRepository.observeAppDetail(packageName)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
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
