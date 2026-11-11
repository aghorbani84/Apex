package com.apex.domain

import kotlin.math.max
import kotlin.math.pow

enum class SplitVerdict {
    PASS,
    FAIL_BELOW_TARGET,
    INSUFFICIENT_SAMPLE,
}

data class MonthlyReturnRecord(
    val yearMonth: String,
    val returnPct: Double,
    val isOutOfSample: Boolean,
)

data class EngineSplitEvaluation(
    val engineName: String,
    val fullResult: BacktestResult,
    val inSampleResult: BacktestResult,
    val outOfSampleResult: BacktestResult,
    val oosMonthlyReturnPct: Double,
    val verdict: SplitVerdict,
    val verdictExplanation: String,
    val monthlyReturns: List<MonthlyReturnRecord>,
    val drawdownSeries: List<Double>,
)

fun evaluateBacktestSplit(
    fullResult: BacktestResult,
    engineName: String,
    isCutoffDate: String = "2024-12-31",
    oosStartDate: String = "2025-01-01",
    targetMonthlyPct: Double = 20.0,
    maxDrawdownLimitPct: Double = 10.0,
    minInSampleTrades: Int = 30,
    minOutOfSampleTrades: Int = 20,
): EngineSplitEvaluation {
    val isTrades = fullResult.trades.filter { it.entryDate <= isCutoffDate }
    val oosTrades = fullResult.trades.filter { it.entryDate >= oosStartDate }

    val inSampleResult = computeSubResult(isTrades, fullResult.startEquity, fullResult.provenance)
    val outOfSampleResult = computeSubResult(oosTrades, fullResult.startEquity, fullResult.provenance)

    val oosMonths = 22.3
    val oosMonthlyReturn = if (outOfSampleResult.endEquity > 0 && oosMonths > 0) {
        ((outOfSampleResult.endEquity / outOfSampleResult.startEquity).pow(1.0 / oosMonths) - 1.0) * 100.0
    } else 0.0

    val isCount = isTrades.size
    val oosCount = oosTrades.size
    val oosDd = outOfSampleResult.maxDrawdownPct

    val (verdict, explanation) = when {
        isCount < minInSampleTrades || oosCount < minOutOfSampleTrades -> {
            SplitVerdict.INSUFFICIENT_SAMPLE to
                "INSUFFICIENT SAMPLE: In-sample trades ($isCount < $minInSampleTrades) or " +
                "out-of-sample trades ($oosCount < $minOutOfSampleTrades) below minimum."
        }
        oosMonthlyReturn < targetMonthlyPct || oosDd > maxDrawdownLimitPct -> {
            SplitVerdict.FAIL_BELOW_TARGET to
                "BELOW TARGET: Out-of-sample monthly return ${"%.2f".format(oosMonthlyReturn)}% is below " +
                "${"%.1f".format(targetMonthlyPct)}%/mo target, or Max DD ${"%.2f".format(oosDd)}% exceeded ${"%.1f".format(maxDrawdownLimitPct)}% limit."
        }
        else -> {
            SplitVerdict.PASS to
                "PASS: Out-of-sample monthly return clears ${"%.1f".format(targetMonthlyPct)}%/mo target with max DD ≤ ${"%.1f".format(maxDrawdownLimitPct)}%."
        }
    }

    val monthlyReturns = computeMonthlyReturns(fullResult.trades, fullResult.startEquity, oosStartDate)
    val drawdownSeries = computeDrawdownSeries(fullResult.trades, fullResult.startEquity)

    return EngineSplitEvaluation(
        engineName = engineName,
        fullResult = fullResult,
        inSampleResult = inSampleResult,
        outOfSampleResult = outOfSampleResult,
        oosMonthlyReturnPct = oosMonthlyReturn,
        verdict = verdict,
        verdictExplanation = explanation,
        monthlyReturns = monthlyReturns,
        drawdownSeries = drawdownSeries,
    )
}

private fun computeSubResult(trades: List<Trade>, startEquity: Double, provenance: DatasetProvenance): BacktestResult {
    var equity = startEquity
    var peak = startEquity
    var maxDd = 0.0

    for (t in trades) {
        equity += t.pnl
        peak = max(peak, equity)
        maxDd = max(maxDd, (peak - equity) / peak)
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

private fun computeMonthlyReturns(
    trades: List<Trade>,
    startEquity: Double,
    oosStartDate: String,
): List<MonthlyReturnRecord> {
    if (trades.isEmpty()) return emptyList()

    val tradesByMonth = trades.groupBy { it.exitDate.take(7) }.toSortedMap()
    var currentEq = startEquity
    val result = mutableListOf<MonthlyReturnRecord>()

    for ((month, mTrades) in tradesByMonth) {
        val monthStartEq = currentEq
        val monthPnl = mTrades.sumOf { it.pnl }
        currentEq += monthPnl
        val retPct = if (monthStartEq > 0) (monthPnl / monthStartEq) * 100.0 else 0.0
        val isOos = month >= oosStartDate.take(7)
        result += MonthlyReturnRecord(yearMonth = month, returnPct = retPct, isOutOfSample = isOos)
    }
    return result
}

private fun computeDrawdownSeries(trades: List<Trade>, startEquity: Double): List<Double> {
    val series = mutableListOf(0.0)
    var equity = startEquity
    var peak = startEquity

    for (t in trades) {
        equity += t.pnl
        peak = max(peak, equity)
        val dd = if (peak > 0) (peak - equity) / peak * 100.0 else 0.0
        series.add(dd)
    }
    return series
}
