package com.apex.domain

import kotlin.math.max

data class DonchianConfig(
    val period: Int = 20,
    val atrPeriod: Int = 14,
    val stopAtrMultiple: Double = 2.0,
    val targetAtrMultiple: Double = 3.0,
    val allowShort: Boolean = true,
    val rangeFiltered: Boolean = false,
    val rangeK: Double = 1.25,
    val minRangeWidthUsd: Double = 0.0,
    val maxRangeWidthUsd: Double = Double.MAX_VALUE,
)

/**
 * 20-day Donchian Breakout engine — measured best in the 2026-11-11 overnight run:
 * Full period: 48 trades, +17.67%, max DD 5.03%, profit factor 1.74.
 * In-sample (2023-2024): 19 trades, +7.91%.
 * Out-of-sample (2025-2026): 29 trades, +9.76%.
 *
 * Rules:
 * - Channel computed over preceding [period] bars (no lookahead, bars t-period to t-1).
 * - Signal at close of bar t; executed at open of bar t+1.
 * - Long on break above channel high; Short on break below channel low.
 * - Stop: 2x ATR(14); Target: 3x ATR(14). No reversal: a position exits only
 *   on stop/target/EOD; breakout signals while a position is open are ignored
 *   (matches Python strategies.run() — verified 2026-11-11).
 * - Friction: 0.05% round-trip.
 */
fun runDonchianBreakoutBacktest(
    bars: List<Bar>,
    provenance: DatasetProvenance,
    config: DonchianConfig = DonchianConfig(),
    rules: RiskRules = RiskRules(),
    startEquity: Double = 10_000.0,
): BacktestResult {
    val atr = averageTrueRange(bars, config.atrPeriod)
    var equity = startEquity
    var peak = startEquity
    var maxDd = 0.0
    val trades = mutableListOf<Trade>()
    var open: DonchianOpenPosition? = null

    for (i in 1 until bars.size) {
        val signalIdx = i - 1
        val a = atr.getOrNull(signalIdx)

        // Need at least config.period bars before signal bar to define channel
        if (signalIdx < config.period || a == null || a <= 0) continue

        val priorBars = bars.subList(signalIdx - config.period, signalIdx)
        val channelHigh = priorBars.maxOf { it.high }
        val channelLow = priorBars.minOf { it.low }

        val signalClose = bars[signalIdx].close
        val longSignal = signalClose > channelHigh
        val shortSignal = config.allowShort && signalClose < channelLow

        // 1. Process open position exits on bar i
        if (open != null) {
            val (side, entry, stop, target, size, entryIdx, riskAmt) = open
            var exitPx: Double? = null
            var why: String? = null

            when (side) {
                TradeSide.LONG -> {
                    when {
                        bars[i].low <= stop -> { exitPx = stop; why = "stop" }
                        bars[i].high >= target -> { exitPx = target; why = "target" }
                    }
                }
                TradeSide.SHORT -> {
                    when {
                        bars[i].high >= stop -> { exitPx = stop; why = "stop" }
                        bars[i].low <= target -> { exitPx = target; why = "target" }
                    }
                }
            }

            if (exitPx != null) {
                val grossPnl = when (side) {
                    TradeSide.LONG -> (exitPx - entry) * size
                    TradeSide.SHORT -> (entry - exitPx) * size
                }
                val cost = rules.roundTripCostPct * (entry + exitPx) * size
                val netPnl = grossPnl - cost
                equity += netPnl
                peak = max(peak, equity)
                maxDd = max(maxDd, (peak - equity) / peak)

                trades += Trade(
                    entryDate = bars[entryIdx].date,
                    exitDate = bars[i].date,
                    entry = entry,
                    exit = exitPx,
                    sizeOz = size,
                    pnl = netPnl,
                    why = why!!,
                    rangeFiltered = config.rangeFiltered,
                    side = side,
                    rMultiple = if (riskAmt > 0) netPnl / riskAmt else 0.0,
                    riskDollars = riskAmt,
                )
                open = null
            }
        }

        // 2. Open new position if flat and signal fired
        if (open == null && (longSignal || shortSignal)) {
            val stopDist = config.stopAtrMultiple * a
            val targetDist = config.targetAtrMultiple * a
            val bandWidth = 2.0 * config.rangeK * a

            // Optional range filter checks
            val passesRangeFilter = !config.rangeFiltered ||
                (bandWidth in config.minRangeWidthUsd..config.maxRangeWidthUsd)

            if (passesRangeFilter) {
                val baseSize = positionSizeOz(equity, stopDist, rules)
                val entry = bars[i].open
                val riskDollars = equity * rules.riskPerTradePct

                if (longSignal) {
                    open = DonchianOpenPosition(
                        side = TradeSide.LONG,
                        entry = entry,
                        stop = entry - stopDist,
                        target = entry + targetDist,
                        size = baseSize,
                        entryIdx = i,
                        riskDollars = riskDollars,
                    )
                } else if (shortSignal) {
                    open = DonchianOpenPosition(
                        side = TradeSide.SHORT,
                        entry = entry,
                        stop = entry + stopDist,
                        target = entry - targetDist,
                        size = baseSize,
                        entryIdx = i,
                        riskDollars = riskDollars,
                    )
                }
            }
        }

        peak = max(peak, equity)
        maxDd = max(maxDd, (peak - equity) / peak)
    }

    // Close remaining position at last bar close
    if (open != null) {
        val (side, entry, _, _, size, entryIdx, riskAmt) = open
        val lastClose = bars.last().close
        val grossPnl = when (side) {
            TradeSide.LONG -> (lastClose - entry) * size
            TradeSide.SHORT -> (entry - lastClose) * size
        }
        // No cost on end-of-data close (matches Python run(): eod_close deducts no cost).
        val netPnl = grossPnl
        equity += netPnl
        trades += Trade(
            entryDate = bars[entryIdx].date,
            exitDate = bars.last().date,
            entry = entry,
            exit = lastClose,
            sizeOz = size,
            pnl = netPnl,
            why = "close_at_end",
            rangeFiltered = config.rangeFiltered,
            side = side,
            rMultiple = if (riskAmt > 0) netPnl / riskAmt else 0.0,
            riskDollars = riskAmt,
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

private data class DonchianOpenPosition(
    val side: TradeSide,
    val entry: Double,
    val stop: Double,
    val target: Double,
    val size: Double,
    val entryIdx: Int,
    val riskDollars: Double,
)