package com.apex.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BacktestTest {

    /** Deterministic 200-bar series: flat, then a steady uptrend that must trigger a crossover entry. */
    private fun trendingBars(): List<Bar> {
        val bars = mutableListOf<Bar>()
        var price = 4000.0
        for (i in 0 until 200) {
            if (i >= 100) price += 1.0
            bars += Bar("2023-01-%02d".format((i % 28) + 1), price, price + 1.0, price - 1.0, price)
        }
        return bars
    }

    @Test
    fun engineEntersOnCrossoverAndExits() {
        val bars = trendingBars()
        val result = runBaselineBacktest(bars, TEST_PROVENANCE)
        assertTrue(result.trades.isNotEmpty(), "expected at least one trade on a trending series")
        assertTrue(result.endEquity.isFinite())
        assertTrue(result.maxDrawdownPct >= 0.0)
    }

    @Test
    fun flatSeriesMakesNoTrades() {
        val bars = (0 until 100).map { Bar("t$it", 4000.0, 4001.0, 3999.0, 4000.0) }
        val result = runBaselineBacktest(bars, TEST_PROVENANCE)
        assertEquals(0, result.trades.size)
        assertEquals(10_000.0, result.endEquity, 0.001)
    }

    @Test
    fun riskRulesRejectUnsafeConfig() {
        val bad = runCatching { RiskRules(riskPerTradePct = 0.5) }
        assertTrue(bad.isFailure)
    }

    @Test
    fun killSwitchTripsAtTenPercentDrawdown() {
        val rules = RiskRules()
        val state = RiskState(equity = 8_900.0, peakEquity = 10_000.0, dayPnlPct = 0.0, weekPnlPct = 0.0)
        assertTrue(state.killSwitchTripped(rules))
        assertTrue(!state.mayOpenTrade(rules))
    }

    @Test
    fun positionSizeIsRiskDividedByStopDistance() {
        val size = positionSizeOz(equity = 10_000.0, stopDistance = 10.0, rules = RiskRules())
        assertEquals(10.0, size, 0.001) // $100 risk / $10 stop = 10 oz
    }

    companion object {
        // Cross-check against backtest.py (same rules, same GC=F data) is a
        // documented manual check, not a unit test: full period 8 trades,
        // +5.26%, max DD 1.09% — see ~/workspace/apex-build/results/backtest.json.
        // Wiring the bundled CSV into commonTest resources is T-APX-11.
        val TEST_PROVENANCE = DatasetProvenance(
            source = "test", symbol = "TEST", label = "test",
            fetchedAt = "2026-11-10", barCount = 0,
            firstDate = "2023-01-01", lastDate = "2023-01-01",
        )
    }
}
