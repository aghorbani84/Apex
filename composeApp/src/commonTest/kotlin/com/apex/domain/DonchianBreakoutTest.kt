package com.apex.domain

import com.apex.data.GC_F_PROVENANCE
import com.apex.data.parseCsvBars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DonchianBreakoutTest {

    @Test
    fun engineTradesBothDirectionsAndCalculatesCosts() {
        val bars = parseCsvBars(GCF_DAILY_CSV, GC_F_PROVENANCE)
        val result = runDonchianBreakoutBacktest(bars, GC_F_PROVENANCE)

        assertTrue(result.trades.isNotEmpty(), "Expected Donchian trades")
        assertTrue(result.trades.any { it.side == TradeSide.LONG }, "Expected long trades")
        assertTrue(result.trades.any { it.side == TradeSide.SHORT }, "Expected short trades")
        assertTrue(result.totalReturnPct > 0.0, "Expected positive return from breakout engine")

        // Validate cost deduction on all trades — except close_at_end:
        // Python eod_close deducts no cost (strategies.py), so that
        // trade's net PnL equals gross by design.
        for (trade in result.trades) {
            val gross = when (trade.side) {
                TradeSide.LONG -> (trade.exit - trade.entry) * trade.sizeOz
                TradeSide.SHORT -> (trade.entry - trade.exit) * trade.sizeOz
            }
            if (trade.why == "close_at_end") {
                assertEquals(gross, trade.pnl, 1e-9, "close_at_end deducts no cost (Python eod_close)")
            } else {
                assertTrue(trade.pnl < gross, "Round trip friction must be deducted from net PnL")
            }
        }
    }

    @Test
    fun evaluationEvaluatesInSampleAndOutOfSampleSplitCorrectly() {
        val bars = parseCsvBars(GCF_DAILY_CSV, GC_F_PROVENANCE)
        val fullResult = runDonchianBreakoutBacktest(bars, GC_F_PROVENANCE)
        val eval = evaluateBacktestSplit(fullResult, "Donchian Breakout")

        assertTrue(eval.inSampleResult.trades.isNotEmpty())
        assertTrue(eval.outOfSampleResult.trades.isNotEmpty())
        assertEquals(fullResult.trades.size, eval.inSampleResult.trades.size + eval.outOfSampleResult.trades.size)

        // Strict verdict verification
        // Breakout CAGR is ~4.3%/year (~0.35%/mo), far below 20.0%/month target
        assertTrue(eval.verdict == SplitVerdict.FAIL_BELOW_TARGET || eval.verdict == SplitVerdict.INSUFFICIENT_SAMPLE)
        assertTrue(eval.verdictExplanation.contains("BELOW TARGET") || eval.verdictExplanation.contains("INSUFFICIENT SAMPLE"))
    }

    @Test
    fun noLookaheadExecutionEntersAtNextBarOpen() {
        val bars = parseCsvBars(GCF_DAILY_CSV, GC_F_PROVENANCE)
        val result = runDonchianBreakoutBacktest(bars, GC_F_PROVENANCE)

        for (trade in result.trades) {
            val entryBar = bars.first { it.date == trade.entryDate }
            assertEquals(entryBar.open, trade.entry, 0.0001, "Entry must execute strictly at bar open")
        }
    }

    @Test
    fun rangeFilterTogglesExperimentalTag() {
        val bars = parseCsvBars(GCF_DAILY_CSV, GC_F_PROVENANCE)
        val filtered = runDonchianBreakoutBacktest(
            bars = bars,
            provenance = GC_F_PROVENANCE,
            config = DonchianConfig(rangeFiltered = true),
        )
        assertTrue(filtered.trades.all { it.rangeFiltered }, "All trades must carry experimental range filter tag")
    }
}