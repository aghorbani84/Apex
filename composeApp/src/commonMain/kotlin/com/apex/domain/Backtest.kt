package com.apex.domain

import kotlin.math.max
import kotlin.math.pow

/**
 * Backtest engine — mirrors backtest.py exactly (same rules, same cost
 * model) so Kotlin results can be cross-checked against the Python run:
 * SMA(20)/SMA(50) crossover, long-only, 1% risk, stop 2×ATR(14),
 * target 3×ATR(14), 0.05% round-trip cost, next-open execution,
 * no lookahead (signals from close of bar t, executed at open of t+1).
 *
 * Baseline result (Python, GC=F daily 2023-01-03–2026-11-09, 970 bars):
 * full period 8 trades, +5.26%, CAGR 1.34%, max DD 1.09%.
 * In-sample 2023–2024: 5 trades, +0.76%. Out-of-sample 2025–2026: 3 trades,
 * +4.47%. FAILS the R6.2 trade-count minimums (≥30/≥20) and the 20%/month
 * target. The range-filtered strategy (Strategy.kt) is a separate,
 * experimental engine to be measured against the same split.
 */
data class BacktestResult(
    val trades: List<Trade>,
    val startEquity: Double,
    val endEquity: Double,
    val totalReturnPct: Double,
    val maxDrawdownPct: Double,
    val winRate: Double?,
    val profitFactor: Double?,
    val provenance: DatasetProvenance,
) {
    fun monthlyReturnPct(months: Double): Double =
        if (months <= 0) Double.NaN else (endEquity / startEquity).let { r ->
            (r.pow(1.0 / months) - 1.0) * 100.0
        }
}

fun runBaselineBacktest(
    bars: List<Bar>,
    provenance: DatasetProvenance,
    config: StrategyConfig = StrategyConfig(),
    rules: RiskRules = RiskRules(),
    startEquity: Double = 10_000.0,
): BacktestResult {
    val closes = bars.map { it.close }
    val smaFast = simpleMovingAverage(closes, config.smaFast)
    val smaSlow = simpleMovingAverage(closes, config.smaSlow)
    val atr = averageTrueRange(bars, config.atrPeriod)

    var equity = startEquity
    var peak = startEquity
    var maxDd = 0.0
    val trades = mutableListOf<Trade>()
    var open: OpenPosition? = null

    for (i in 1 until bars.size) {
        val f = smaFast.getOrNull(i - 1)
        val s = smaSlow.getOrNull(i - 1)
        val a = atr.getOrNull(i - 1)
        if (f == null || s == null || a == null || a <= 0) continue

        // Exit first, using bar i's high/low.
        if (open != null) {
            val (entry, stop, target, size, entryIdx) = open
            val exitPx: Double?
            val why: String?
            when {
                bars[i].low <= stop -> { exitPx = stop; why = "stop" }
                bars[i].high >= target -> { exitPx = target; why = "target" }
                f < s -> { exitPx = bars[i].open; why = "cross_exit" }
                else -> { exitPx = null; why = null }
            }
            if (exitPx != null) {
                val pnl = (exitPx - entry) * size
                val cost = rules.roundTripCostPct * (entry + exitPx) * size
                equity += pnl - cost
                trades += Trade(
                    entryDate = bars[entryIdx].date,
                    exitDate = bars[i].date,
                    entry = entry, exit = exitPx, sizeOz = size,
                    pnl = pnl - cost, why = why!!,
                )
                open = null
                peak = max(peak, equity)
                maxDd = max(maxDd, (peak - equity) / peak)
            }
        }
        // Entry on cross, at open of bar i, signal from close of i-1.
        if (open == null && f > s) {
            val prevF = smaFast.getOrNull(i - 2)
            val prevS = smaSlow.getOrNull(i - 2)
            if (prevF != null && prevS != null && prevF <= prevS) {
                val size = positionSizeOz(equity, config.stopAtrMultiple * a, rules)
                val entry = bars[i].open
                open = OpenPosition(
                    entry = entry,
                    stop = entry - config.stopAtrMultiple * a,
                    target = entry + config.targetAtrMultiple * a,
                    size = size,
                    entryIdx = i,
                )
            }
        }
        peak = max(peak, equity)
        maxDd = max(maxDd, (peak - equity) / peak)
    }
    if (open != null) {
        val (entry, _, _, size, entryIdx) = open
        val pnl = (bars.last().close - entry) * size
        equity += pnl
        trades += Trade(
            entryDate = bars[entryIdx].date,
            exitDate = bars.last().date,
            entry = entry, exit = bars.last().close, sizeOz = size,
            pnl = pnl, why = "close_at_end",
        )
    }
    val wins = trades.filter { it.pnl > 0 }
    val grossWin = wins.sumOf { it.pnl }
    val grossLoss = -trades.filter { it.pnl < 0 }.sumOf { it.pnl }
    return BacktestResult(
        trades = trades,
        startEquity = startEquity,
        endEquity = equity,
        totalReturnPct = (equity / startEquity - 1.0) * 100.0,
        maxDrawdownPct = maxDd * 100.0,
        winRate = if (trades.isEmpty()) null else wins.size.toDouble() / trades.size,
        profitFactor = if (grossLoss == 0.0) null else grossWin / grossLoss,
        provenance = provenance,
    )
}

private data class OpenPosition(
    val entry: Double,
    val stop: Double,
    val target: Double,
    val size: Double,
    val entryIdx: Int,
)
