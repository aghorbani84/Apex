package com.apex.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaperAccountTest {

    @Test
    fun paperAccountHaltsAndLogsWhenKillSwitchTrips() {
        val rules = RiskRules()
        var account = PaperAccount(startEquity = 10_000.0, equity = 10_000.0, peakEquity = 10_000.0)

        // Apply a -$1,100 losing trade (11% drawdown from $10,000 peak)
        val losingTrade = Trade(
            entryDate = "2026-01-05",
            exitDate = "2026-01-06",
            entry = 2700.0,
            exit = 2600.0,
            sizeOz = 11.0,
            pnl = -1100.0,
            why = "stop",
        )

        account = account.applyTrade(losingTrade, rules)
        assertTrue(account.halted, "11% drawdown must trip the 10% kill-switch")
        assertTrue(account.haltReason?.contains("kill-switch") == true)

        // Attempting to trade while halted must record a rejection
        val attemptTrade = Trade(
            entryDate = "2026-01-07",
            exitDate = "2026-01-08",
            entry = 2650.0,
            exit = 2700.0,
            sizeOz = 5.0,
            pnl = 250.0,
            why = "target",
        )
        val rejectedAccount = account.applyTrade(attemptTrade, rules)
        assertEquals(account.equity, rejectedAccount.equity, "No equity change permitted while halted")
    }

    @Test
    fun manualResumeResetsHaltStateWithLoggedTimestamp() {
        val rules = RiskRules()
        var account = PaperAccount(startEquity = 10_000.0, equity = 8_900.0, peakEquity = 10_000.0, halted = true, haltReason = "Kill-switch")

        account = account.manualResume("2026-11-11T14:00:00Z", "User reviewed loss distribution")
        assertFalse(account.halted, "Manual resume must clear halt")
        assertEquals(8_900.0, account.peakEquity, "Peak equity resets to current equity upon resumption")
        assertEquals(1, account.manualResumes.size)
        assertTrue(account.manualResumes.first().contains("2026-11-11T14:00:00Z"))
    }

    @Test
    fun dailyLossLimitPreventsFurtherTradesOnSameDate() {
        val rules = RiskRules(dailyLossLimitPct = 0.03) // 3%
        val account = PaperAccount(startEquity = 10_000.0, equity = 9_700.0, peakEquity = 10_000.0, dayPnlPct = -0.035)

        val nextTrade = Trade(
            entryDate = "2026-02-01",
            exitDate = "2026-02-01",
            entry = 2800.0,
            exit = 2850.0,
            sizeOz = 5.0,
            pnl = 250.0,
            why = "target",
        )

        val updated = account.applyTrade(nextTrade, rules)
        assertEquals(1, updated.rejectedTrades.size)
        assertTrue(updated.rejectedTrades.first().reason.contains("Daily loss limit"))
    }
}