package com.apex.domain

import com.apex.data.GC_F_PROVENANCE
import com.apex.data.parseCsvBars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CsvBacktestTest {

    @Test
    fun bundledCsvReproducesPythonBacktest() {
        val bars = parseCsvBars(GCF_DAILY_CSV, GC_F_PROVENANCE)
        assertEquals(970, bars.size)
        val result = runBaselineBacktest(bars, GC_F_PROVENANCE)
        assertEquals(8, result.trades.size, "expected 8 trades, got ${result.trades.size}")
        assertEquals(5.26, result.totalReturnPct, 0.3)
        assertEquals(1.09, result.maxDrawdownPct, 0.3)
        assertEquals(10_526.28, result.endEquity, 5.0)
    }

    @Test
    fun paperAccountSimulationReplaysBacktestTrades() {
        val bars = parseCsvBars(GCF_DAILY_CSV, GC_F_PROVENANCE)
        val result = runBaselineBacktest(bars, GC_F_PROVENANCE)
        val sim = simulatePaperAccount(result.trades, RiskRules())
        assertEquals(result.trades.size, sim.tradesApplied)
        assertFalse(sim.account.halted, "baseline must not trip the kill-switch")
        assertEquals(result.endEquity, sim.account.equity, 0.01)
        assertEquals(10_526.28, sim.account.equity, 5.0)
    }

    @Test
    fun paperAccountHaltsOnTenPercentDrawdown() {
        val losing = listOf(
            Trade("t0", "t1", 4000.0, 3900.0, 10.0, -1000.0, "stop"),
            Trade("t1", "t2", 3900.0, 3800.0, 10.0, -1000.0, "stop"),
        )
        val sim = simulatePaperAccount(losing, RiskRules())
        assertTrue(sim.account.halted, "two -$1,000 trades on $10,000 must trip the 10% kill-switch")
        assertEquals(0, sim.haltedAtTradeIndex)
    }
}
