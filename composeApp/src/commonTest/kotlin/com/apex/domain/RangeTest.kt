package com.apex.domain

import com.apex.range.isCloseInside
import com.apex.range.isFullCandleInside
import com.apex.range.measureRangePredictor
import com.apex.range.predictBand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RangeTest {

    @Test
    fun strictCriterionRequiresFullCandleInside() {
        val band = predictBand(close = 4000.0, atr5m = 2.0, k = 1.0)
        assertEquals(3998.0, band.low)
        assertEquals(4002.0, band.high)
        val inside = Bar("t+1", 4000.0, 4001.0, 3999.0, 4000.5)
        val wickOutside = Bar("t+1", 4000.0, 4003.0, 3999.0, 4000.5)
        assertTrue(isFullCandleInside(band, inside))
        assertFalse(isFullCandleInside(band, wickOutside))
        assertTrue(isCloseInside(band, wickOutside))
    }

    @Test
    fun measurementCountsOnlyEvaluatedBars() {
        val bars = (0 until 20).map { i ->
            Bar("t$i", 4000.0 + i, 4002.0 + i, 3998.0 + i, 4000.0 + i)
        }
        val m = measureRangePredictor(bars, k = 1.0)
        assertTrue(m.barsEvaluated >= 1, "expected >=1 evaluated bar, got ${m.barsEvaluated}")
        assertTrue(m.barsEvaluated < bars.size - 1, "ATR warmup bars must not be evaluated")
        assertTrue(m.medianWidthUsd > 0)
    }
}
