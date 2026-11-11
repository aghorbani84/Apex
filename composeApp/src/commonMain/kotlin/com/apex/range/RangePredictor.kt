package com.apex.range

import com.apex.domain.Bar
import com.apex.domain.averageTrueRange

data class RangeBand(val low: Double, val high: Double) {
    val widthUsd: Double get() = high - low
    fun widthPct(midPrice: Double): Double = if (midPrice == 0.0) Double.NaN else widthUsd / midPrice * 100.0
}

fun predictBand(close: Double, atr5m: Double, k: Double): RangeBand =
    RangeBand(low = close - k * atr5m, high = close + k * atr5m)

fun isFullCandleInside(band: RangeBand, next: Bar): Boolean =
    next.low >= band.low && next.high <= band.high

fun isCloseInside(band: RangeBand, next: Bar): Boolean =
    next.close in band.low..band.high

data class RangeMeasurement(
    val barsEvaluated: Int,
    val fullCandleHits: Int,
    val closeHits: Int,
    val medianWidthUsd: Double,
    val medianWidthPct: Double,
    val baselineFullCandleHits: Int,
    val laggedCentreHits: Int,
) {
    val fullCandleHitRate: Double get() = if (barsEvaluated == 0) Double.NaN else fullCandleHits.toDouble() / barsEvaluated
    val closeHitRate: Double get() = if (barsEvaluated == 0) Double.NaN else closeHits.toDouble() / barsEvaluated
    val baselineHitRate: Double get() = if (barsEvaluated == 0) Double.NaN else baselineFullCandleHits.toDouble() / barsEvaluated
    val laggedCentreHitRate: Double get() = if (barsEvaluated == 0) Double.NaN else laggedCentreHits.toDouble() / barsEvaluated
    val skillVsBaseline: Double get() = fullCandleHitRate - baselineHitRate
    val skillVsLaggedCentre: Double get() = fullCandleHitRate - laggedCentreHitRate
    val falsified: Boolean get() = barsEvaluated >= 1000 && skillVsBaseline <= 0.0
}

fun measureRangePredictor(
    bars: List<Bar>,
    k: Double,
    fixedHalfWidth: Double? = null,
    fromIndex: Int = 0,
    predict: (close: Double, atr5m: Double) -> RangeBand = { c, a -> predictBand(c, a, k) },
): RangeMeasurement {
    val atr = averageTrueRange(bars, 14)
    val forecasts = mutableListOf<Triple<Int, RangeBand, Bar>>()
    for (i in fromIndex until bars.size - 1) {
        val a = atr.getOrNull(i) ?: continue
        if (a <= 0) continue
        forecasts += Triple(i, predict(bars[i].close, a), bars[i + 1])
    }
    val halfWidths = forecasts.map { it.second.widthUsd / 2.0 }.sorted()
    val medianHalf = fixedHalfWidth ?: if (halfWidths.isEmpty()) Double.NaN else halfWidths[halfWidths.size / 2]
    var evaluated = 0
    var fullHits = 0
    var closeHits = 0
    var baseHits = 0
    var laggedHits = 0
    val widths = mutableListOf<Double>()
    val widthPcts = mutableListOf<Double>()

    for ((i, band, next) in forecasts) {
        evaluated++
        if (isFullCandleInside(band, next)) fullHits++
        if (isCloseInside(band, next)) closeHits++

        val fixed = RangeBand(bars[i].close - medianHalf, bars[i].close + medianHalf)
        if (isFullCandleInside(fixed, next)) baseHits++

        if (i >= 1) {
            val half = band.widthUsd / 2.0
            val lagged = RangeBand(bars[i - 1].close - half, bars[i - 1].close + half)
            if (isFullCandleInside(lagged, next)) laggedHits++
        }
        widths += band.widthUsd
        widthPcts += band.widthPct(bars[i].close)
    }
    widths.sort()
    widthPcts.sort()
    val median = { xs: List<Double> -> if (xs.isEmpty()) Double.NaN else xs[xs.size / 2] }
    return RangeMeasurement(
        barsEvaluated = evaluated,
        fullCandleHits = fullHits,
        closeHits = closeHits,
        medianWidthUsd = median(widths),
        medianWidthPct = median(widthPcts),
        baselineFullCandleHits = baseHits,
        laggedCentreHits = laggedHits,
    )
}
