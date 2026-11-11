package com.apex.range

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
