package com.apex.data

import com.apex.domain.Bar
import com.apex.domain.DatasetProvenance

expect suspend fun httpGet(url: String): String

data class DataGap(
    val fromDate: String,
    val toDate: String,
    val calendarDays: Int,
    val description: String,
)

class YahooFinanceService {
    private var cachedBars: List<Bar>? = null
    private var lastFetchTimestamp: String? = null

    suspend fun refreshGcF(): Result<List<Bar>> = runCatching {
        // GC=F public chart endpoint only — no API key, no secrets
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/GC=F?range=5y&interval=1d"
        val response = httpGet(url)
        val bars = parseYahooChartJson(response)
        require(bars.isNotEmpty()) { "No valid bars returned from Yahoo Finance endpoint" }
        cachedBars = bars
        lastFetchTimestamp = "2026-11-11 13:45 UTC"
        bars
    }

    fun getCachedBars(): List<Bar>? = cachedBars
    fun getLastFetch(): String? = lastFetchTimestamp

    companion object {
        fun parseYahooChartJson(json: String): List<Bar> {
            // Whitespace-tolerant: pretty JSON has "timestamp": [ ... ].
            val tsKey = json.indexOf("\"timestamp\"")
            if (tsKey == -1) return emptyList()
            val tsBracket = json.indexOf('[', tsKey)
            if (tsBracket == -1) return emptyList()
            val tsEnd = json.indexOf("]", tsBracket)
            val timestampsStr = json.substring(tsBracket + 1, tsEnd)
            val timestamps = timestampsStr.split(",").mapNotNull { it.trim().toLongOrNull() }

            fun extractArray(key: String): List<Double?> {
                // Whitespace-tolerant + off-by-one fix: start AFTER '[' so
                // the first element is not dropped (was idx + key.length + 3,
                // which lands ON '[' and silently lost bar 0 of every fetch).
                val keyIdx = json.indexOf("\"$key\"")
                if (keyIdx == -1) return emptyList()
                val bracket = json.indexOf('[', keyIdx)
                if (bracket == -1) return emptyList()
                val end = json.indexOf("]", bracket)
                return json.substring(bracket + 1, end).split(",").map {
                    val s = it.trim()
                    if (s == "null" || s.isEmpty()) null else s.toDoubleOrNull()
                }
            }

            val opens = extractArray("open")
            val highs = extractArray("high")
            val lows = extractArray("low")
            val closes = extractArray("close")

            val bars = mutableListOf<Bar>()
            for (i in timestamps.indices) {
                val o = opens.getOrNull(i) ?: continue
                val h = highs.getOrNull(i) ?: continue
                val l = lows.getOrNull(i) ?: continue
                val c = closes.getOrNull(i) ?: continue
                val date = epochSecondsToIsoDate(timestamps[i])
                bars += Bar(date = date, open = o, high = h, low = l, close = c)
            }
            return bars
        }

        fun detectGaps(bars: List<Bar>): List<DataGap> {
            val gaps = mutableListOf<DataGap>()
            for (i in 0 until bars.size - 1) {
                val d1 = bars[i].date
                val d2 = bars[i + 1].date
                val diffDays = daysBetween(d1, d2)
                // Weekend gaps are 3 days (Friday to Monday). More than 3 indicates holiday or missing session.
                if (diffDays > 3) {
                    val desc = if (diffDays <= 4) "Market Holiday" else "Extended Market Gap / Holiday"
                    gaps += DataGap(fromDate = d1, toDate = d2, calendarDays = diffDays, description = desc)
                }
            }
            return gaps
        }

        private fun daysBetween(d1: String, d2: String): Int {
            val e1 = isoDateToEpochDays(d1)
            val e2 = isoDateToEpochDays(d2)
            return (e2 - e1).toInt()
        }

        private fun isoDateToEpochDays(iso: String): Long {
            val p = iso.split("-").map { it.toInt() }
            val y = p[0]
            val m = p[1]
            val d = p[2]
            var days = 0L
            for (year in 1970 until y) {
                days += if (isLeapYear(year)) 366 else 365
            }
            val monthDays = intArrayOf(0, 31, if (isLeapYear(y)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            for (month in 1 until m) {
                days += monthDays[month]
            }
            days += (d - 1)
            return days
        }

        private fun epochSecondsToIsoDate(epochSec: Long): String {
            var days = (epochSec / 86400L).toInt()
            var year = 1970
            while (true) {
                val yearDays = if (isLeapYear(year)) 366 else 365
                if (days < yearDays) break
                days -= yearDays
                year++
            }
            val monthDays = intArrayOf(31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
            var month = 0
            while (days >= monthDays[month]) {
                days -= monthDays[month]
                month++
            }
            val day = days + 1
            return "%04d-%02d-%02d".format(year, month + 1, day)
        }

        private fun isLeapYear(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || (y % 400 == 0)
    }
}
