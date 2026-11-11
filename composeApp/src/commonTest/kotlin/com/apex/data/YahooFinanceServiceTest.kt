package com.apex.data

import com.apex.domain.Bar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YahooFinanceServiceTest {

    @Test
    fun parsesValidYahooChartResponse() {
        val sampleJson = """
            {
              "chart": {
                "result": [
                  {
                    "timestamp": [1672756200, 1672842600],
                    "indicators": {
                      "quote": [
                        {
                          "open": [1831.8, 1845.2],
                          "high": [1856.6, 1871.3],
                          "low": [1831.3, 1842.0],
                          "close": [1846.1, 1859.0]
                        }
                      ]
                    }
                  }
                ]
            """.trimIndent()

        val bars = YahooFinanceService.parseYahooChartJson(sampleJson)
        assertEquals(2, bars.size)
        assertEquals(1831.8, bars[0].open)
        assertEquals(1859.0, bars[1].close)
    }

    @Test
    fun detectsMarketCalendarGapsGreaterThanThreeDays() {
        val bars = listOf(
            Bar("2026-01-02", 2000.0, 2010.0, 1990.0, 2005.0), // Friday
            Bar("2026-01-05", 2005.0, 2020.0, 2000.0, 2015.0), // Monday (3 days: normal weekend)
            Bar("2026-01-12", 2015.0, 2030.0, 2010.0, 2025.0), // 7 days later: holiday/missing data
        )

        val gaps = YahooFinanceService.detectGaps(bars)
        assertEquals(1, gaps.size)
        assertEquals("2026-01-05", gaps[0].fromDate)
        assertEquals("2026-01-12", gaps[0].toDate)
        assertEquals(7, gaps[0].calendarDays)
    }
}
