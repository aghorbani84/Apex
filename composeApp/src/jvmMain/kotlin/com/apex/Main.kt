package com.apex

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.apex.data.GC_F_PROVENANCE
import com.apex.data.parseCsvBars
import com.apex.domain.DonchianConfig
import com.apex.domain.GCF_DAILY_CSV
import com.apex.domain.evaluateBacktestSplit
import com.apex.domain.runBaselineBacktest
import com.apex.domain.runDonchianBreakoutBacktest
import com.apex.ui.ApexApp
import java.io.File

fun main() {
    val csv = if (File("data/gcf-daily.csv").exists()) {
        File("data/gcf-daily.csv").readText()
    } else {
        GCF_DAILY_CSV
    }
    val bars = parseCsvBars(csv, GC_F_PROVENANCE)

    val baseline = runBaselineBacktest(bars, GC_F_PROVENANCE)
    val donchian = runDonchianBreakoutBacktest(bars, GC_F_PROVENANCE, DonchianConfig())

    val baselineSplit = evaluateBacktestSplit(baseline, "SMA Baseline (20/50 Long)")
    val donchianSplit = evaluateBacktestSplit(donchian, "Donchian Breakout (20-day Channel)")

    println("================================================================================")
    println("APEX PHASE 1 ENGINE VERIFICATION RUN (GC=F DAILY 2023-01-03–2026-11-09)")
    println("================================================================================")
    println("1. SMA Baseline: ${baseline.trades.size} trades | Return: ${"%.2f".format(baseline.totalReturnPct)}% | MaxDD: ${"%.2f".format(baseline.maxDrawdownPct)}%")
    println("   Verdict: ${baselineSplit.verdict} -> ${baselineSplit.verdictExplanation}")
    println("2. Donchian Breakout: ${donchian.trades.size} trades | Return: ${"%.2f".format(donchian.totalReturnPct)}% | MaxDD: ${"%.2f".format(donchian.maxDrawdownPct)}% | PF: ${donchian.profitFactor?.let { "%.2f".format(it) } ?: "n/a"}")
    println("   Verdict: ${donchianSplit.verdict} -> ${donchianSplit.verdictExplanation}")
    println("3. Range Filtered: NOT RUN (Range filter disabled until R10 5y data falsification is measured)")
    println("================================================================================")

    application {
        Window(onCloseRequest = ::exitApplication, title = "APEX — Gold Paper Trading (XAUUSD)") {
            ApexApp(backtest = donchian)
        }
    }
}
