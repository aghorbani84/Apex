package com.apex.range

import com.apex.domain.Bar
import com.apex.domain.averageTrueRange

/**
 * Range Prediction module (R10) — MEASUREMENT ONLY.
 *
 * USER CLAIM — UNVERIFIED: a tool predicts the next 5-minute range with
 * 80% accuracy over 5 years, directionless. That number is NEVER hardcoded
 * here, NEVER displayed as a result, and NEVER feeds trading until it is
 * measured and survives falsification. Until measured, the UI must show
 * the hit rate as "not measured".
 *
 * Fixed before any measurement:
 * - Forecast: at the close of 5-min bar t, band [L, U] for bar t+1.
 * - Band (proposal): L = close_t − k × ATR_5m(14), U = close_t + k × ATR_5m(14),
 *   k frozen after in-sample choice.
 * - PRIMARY hit criterion (strict): the FULL next candle's high AND low
 *   both lie inside [L, U]. Secondary (reported separately): close inside.
 * - Width reporting (mandatory): median band width in $/oz AND % of price,
 *   always shown next to the hit rate.
 * - Falsification (pre-registered): falsified if the out-of-sample hit rate
 *   is ≤ the fixed-width baseline (B1) at equal width, or a return-shuffled
 *   random walk matches it within 2pp. Minimum 1,000 out-of-sample bars.
 *
 * BASELINE CORRECTION (2026-11-11, found in the overnight Python run):
 * the v1 baseline — previous bar's range re-centred on close_t and
 * re-scaled to the forecast width — reproduces the forecast band EXACTLY
 * (half-width = prevW × (bandW/prevW) / 2 = bandW/2, same centre), so v1
 * skill was 0.000 by construction for every k. v2 baselines (non-degenerate):
 * - B1 FIXED-WIDTH: close_t ± W_med, W_med = median forecast half-width
 *   (frozen from in-sample when [fixedHalfWidth] is supplied, else the
 *   median over the evaluated bars). Same median width, NOT adaptive to
 *   current volatility. Skill vs B1 tests whether ATR-adaptivity adds
 *   anything.
 * - B1b LAGGED-CENTRE: close_{t-1} ± k × ATR — same width as forecast,
 *   centred one bar late. Tests whether the current close as centre matters.
 */
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
    /** B1 fixed-width baseline hits (same median width, not volatility-adaptive). */
    val baselineFullCandleHits: Int,
    /** B1b lagged-centre baseline hits (same width, centred on previous close). */
    val laggedCentreHits: Int,
) {
    val fullCandleHitRate: Double get() = if (barsEvaluated == 0) Double.NaN else fullCandleHits.toDouble() / barsEvaluated
    val closeHitRate: Double get() = if (barsEvaluated == 0) Double.NaN else closeHits.toDouble() / barsEvaluated
    /** B1 hit rate — kept name for existing callers. */
    val baselineHitRate: Double get() = if (barsEvaluated == 0) Double.NaN else baselineFullCandleHits.toDouble() / barsEvaluated
    val laggedCentreHitRate: Double get() = if (barsEvaluated == 0) Double.NaN else laggedCentreHits.toDouble() / barsEvaluated
    /** Skill = forecast minus B1 fixed-width baseline at EQUAL width. ≤ 0 means no skill. */
    val skillVsBaseline: Double get() = fullCandleHitRate - baselineHitRate
    val skillVsLaggedCentre: Double get() = fullCandleHitRate - laggedCentreHitRate
    val falsified: Boolean get() = barsEvaluated >= 1000 && skillVsBaseline <= 0.0
}

/**
 * Measures a band predictor over 5-minute bars. [predict] returns the band
 * forecast at the close of bar t; it is evaluated on bar t+1.
 * [fixedHalfWidth]: frozen median forecast half-width from in-sample for
 * the B1 fixed-width baseline; when null, the median over the evaluated
 * bars is used (equal-width within this evaluation set).
 * [fromIndex]: only pairs with forecast-bar index >= [fromIndex] are
 * evaluated (ATR is still computed on the full list, so a slice keeps
 * its lookback — use it to measure an out-of-sample window).
 */
fun measureRangePredictor(
    bars: List<Bar>,
    k: Double,
    fixedHalfWidth: Double? = null,
    fromIndex: Int = 0,
    predict: (close: Double, atr5m: Double) -> RangeBand = { c, a -> predictBand(c, a, k) },
): RangeMeasurement {
    val atr = averageTrueRange(bars, 14)
    // First pass: forecast bands and their median half-width (for B1).
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
        // B1 fixed-width: same median width, centred on this close, not adaptive.
        val fixed = RangeBand(bars[i].close - medianHalf, bars[i].close + medianHalf)
        if (isFullCandleInside(fixed, next)) baseHits++
        // B1b lagged-centre: same width as forecast, centred on previous close.
        if (i >= 1) {
            val half = band.widthUsd / 2.0 // == k * ATR(14) at bar i
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
