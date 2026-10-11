package com.chanakya.core.vpn.rules

import com.chanakya.core.data.model.RuleScope
import com.chanakya.core.data.model.RuleType
import com.chanakya.core.data.model.UserRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the user-rule precedence matrix.
 */
class UserRuleSetTest {

    private fun rule(
        type: RuleType,
        scope: RuleScope,
        domain: String,
        packageName: String? = null,
        enabled: Boolean = true,
    ) = UserRule(type = type, scope = scope, domain = domain, packageName = packageName, enabled = enabled)

    @Test
    fun `an app allow overrides a global block`() {
        val set = UserRuleSet(
            listOf(
                rule(RuleType.BLOCK, RuleScope.GLOBAL, "example.com"),
                rule(RuleType.ALLOW, RuleScope.APP, "example.com", "com.foo"),
            ),
        )
        assertEquals(RuleDecision.ALLOW, set.evaluate("example.com", "com.foo"))
        assertEquals(RuleDecision.BLOCK, set.evaluate("example.com", "com.bar"))
    }

    @Test
    fun `a global allow overrides a global block`() {
        val set = UserRuleSet(
            listOf(
                rule(RuleType.BLOCK, RuleScope.GLOBAL, "example.com"),
                rule(RuleType.ALLOW, RuleScope.GLOBAL, "example.com"),
            ),
        )
        assertEquals(RuleDecision.ALLOW, set.evaluate("example.com", "com.any"))
    }

    @Test
    fun `an app block applies only to that app`() {
        val set = UserRuleSet(listOf(rule(RuleType.BLOCK, RuleScope.APP, "example.com", "com.foo")))
        assertEquals(RuleDecision.BLOCK, set.evaluate("example.com", "com.foo"))
        assertNull(set.evaluate("example.com", "com.bar"))
    }

    @Test
    fun `rules match subdomains`() {
        val set = UserRuleSet(listOf(rule(RuleType.BLOCK, RuleScope.GLOBAL, "example.com")))
        assertEquals(RuleDecision.BLOCK, set.evaluate("ads.example.com", "com.any"))
    }

    @Test
    fun `disabled rules are ignored`() {
        val set = UserRuleSet(
            listOf(rule(RuleType.BLOCK, RuleScope.GLOBAL, "example.com", enabled = false)),
        )
        assertNull(set.evaluate("example.com", "com.any"))
    }

    @Test
    fun `returns null when nothing matches`() {
        val set = UserRuleSet(listOf(rule(RuleType.BLOCK, RuleScope.GLOBAL, "other.com")))
        assertNull(set.evaluate("example.com", "com.any"))
    }
}
