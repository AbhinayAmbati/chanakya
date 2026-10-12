package com.chanakya.app.ui.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chanakya.core.data.model.DomainDetail
import com.chanakya.core.data.model.RuleScope
import com.chanakya.core.data.model.RuleType
import com.chanakya.core.data.model.UserRule
import com.chanakya.core.data.repository.TrackerRepository
import com.chanakya.core.data.repository.UserRuleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Loads the cross-app view of a single domain and lets the user rule on it.
 *
 * @param trackerRepository the read model.
 * @param userRuleRepository where new rules are written.
 * @param domain the domain to show.
 */
class DomainDetailViewModel(
    trackerRepository: TrackerRepository,
    private val userRuleRepository: UserRuleRepository,
    private val domain: String,
) : ViewModel() {

    /** Everything known about the domain, or null while loading or if never seen. */
    val detail: StateFlow<DomainDetail?> = trackerRepository.observeDomainDetail(domain)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Block this domain in every app. */
    fun blockEverywhere() = addRule(RuleType.BLOCK)

    /** Allow this domain in every app. */
    fun allowEverywhere() = addRule(RuleType.ALLOW)

    /**
     * Persist a rule.
     *
     * Only global rules are offered here. A per-app rule needs one app to attach to, and
     * this view spans every app, so those stay on the app detail screen where the app is
     * unambiguous.
     */
    private fun addRule(type: RuleType) {
        viewModelScope.launch {
            userRuleRepository.add(
                UserRule(
                    type = type,
                    scope = RuleScope.GLOBAL,
                    domain = domain,
                    packageName = null,
                ),
            )
        }
    }
}
