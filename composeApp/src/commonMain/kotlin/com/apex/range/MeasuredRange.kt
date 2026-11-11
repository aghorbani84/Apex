package com.apex.range

/**
 * Measured R10 numbers — NOT a claim, NOT the user's tool, NOT 80%.
 *
 * Source: ~/workspace/apex-build/range-test/range_test.py, run 2026-11-11
 * on Yahoo Finance GC=F 5-minute bars (COMEX gold futures, proxy for
 * XAUUSD spot), 13,619 bars 2026-09-01–2026-11-10. Method: R10 proxy,
 * band = close ± k×ATR_5m(14), k=1.25 frozen after in-sample choice
 * (max skill vs fixed-width baseline). Strict full-candle-inside criterion.
 * In-sample 2026-09-01–2026-10-06, out-of-sample 2026-10-06–2026-11-10.
 *
 * OOS (6,776 pairs): full hit 85.82%, close hit 93.06%,
 * median width $9.50 (0.2317% of price).
 * - vs FROZEN-width baseline B1 ($11.08 wide OOS): 87.18%, skill −1.36pp —
 *   the pre-registered primary rule triggered FALSIFIED, BUT that
 *   comparison was width-confounded (B1 was wider than the forecast
 *   OOS, $11.08 vs $9.50).
 * - vs EQUAL-WIDTH fixed band on OOS ($9.50): 81.39%, skill +4.43pp.
 *   In-sample skill at equal width was +3.46pp. So: small, consistent
 *   skill at equal width; no skill against a wider fixed band.
 * - Random walk (shuffled returns): 80.11% at $12.36 width — does not
 *   match the real 85.82% (gap 5.7pp, real narrower).
 *
 * What this means: an "80%+" hit rate is achievable at ~$9.50–$11 width
 * on 5-min gold — a fixed-width band hits 87%. The number is
 * width-dependent and unremarkable on its own. The user's 5-year 80%
 * claim for THEIR tool remains UNTESTED: no 5-year 5-minute data and
 * no tool code/method supplied. This measurement is of the R10 proxy
 * method on a ~60-day window only.
 */
data class MeasuredRange(
    val fullHitRate: Double,
    val closeHitRate: Double,
    val baselineFrozenWidthHitRate: Double,
    val skillVsEqualWidth: Double,
    val medianWidthUsd: Double,
    val medianWidthPct: Double,
    val pairsOos: Int,
    val k: Double,
    val source: String,
    val window: String,
)

/** Last measured R10 values (2026-11-11). Update only by re-running range_test.py. */
val MEASURED_RANGE_OOS_2026_11_11 = MeasuredRange(
    fullHitRate = 0.8582,
    closeHitRate = 0.9306,
    baselineFrozenWidthHitRate = 0.8718,
    skillVsEqualWidth = 0.0443,
    medianWidthUsd = 9.50,
    medianWidthPct = 0.2317,
    pairsOos = 6776,
    k = 1.25,
    source = "Yahoo Finance GC=F 5-min, R10 proxy (close ± k×ATR14)",
    window = "OOS 2026-10-06–2026-11-10 (in-sample 2026-09-01–2026-10-06)",
)

fun MeasuredRange.displayText(): String =
    "hit ${"%.1f".format(fullHitRate * 100)}% (strict, ${pairsOos} OOS pairs), " +
        "width \$${"%.2f".format(medianWidthUsd)} (${"%.3f".format(medianWidthPct)}%), " +
        "skill vs equal-width fixed band +${"%.1f".format(skillVsEqualWidth * 100)}pp — " +
        "80% headline is width-dependent, not an edge by itself"
