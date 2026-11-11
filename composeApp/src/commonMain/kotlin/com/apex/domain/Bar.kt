package com.apex.domain

/**
 * One OHLC bar. [date] is ISO-8601 (yyyy-MM-dd for daily bars,
 * yyyy-MM-dd'T'HH:mm for 5-minute bars). Prices in USD per troy ounce.
 * Source must be recorded in [DatasetProvenance]; never fabricate bars.
 */
data class Bar(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

/** Provenance carried with every dataset (R4.5). */
data class DatasetProvenance(
    val source: String,
    val symbol: String,
    val label: String,
    val fetchedAt: String,
    val barCount: Int,
    val firstDate: String,
    val lastDate: String,
)

fun averageTrueRange(bars: List<Bar>, period: Int = 14): List<Double?> {
    val tr = bars.mapIndexed { i, b ->
        if (i == 0) b.high - b.low
        else maxOf(
            b.high - b.low,
            kotlin.math.abs(b.high - bars[i - 1].close),
            kotlin.math.abs(b.low - bars[i - 1].close),
        )
    }
    return tr.mapIndexed { i, _ ->
        if (i + 1 < period) null
        else tr.subList(i + 1 - period, i + 1).average()
    }
}

fun simpleMovingAverage(values: List<Double>, period: Int): List<Double?> =
    values.mapIndexed { i, _ ->
        if (i + 1 < period) null else values.subList(i + 1 - period, i + 1).average()
    }
