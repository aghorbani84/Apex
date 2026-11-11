package com.apex.data

import com.apex.domain.Bar
import com.apex.domain.DatasetProvenance

fun parseCsvBars(csv: String, provenance: DatasetProvenance): List<Bar> {
    val lines = csv.lines().filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyList()
    val header = lines.first().split(",").map { it.trim() }
    val dateCol = if (header.contains("Date")) "Date" else "Datetime"
    val idx = mapOf(
        "date" to header.indexOf(dateCol),
        "open" to header.indexOf("Open"),
        "high" to header.indexOf("High"),
        "low" to header.indexOf("Low"),
        "close" to header.indexOf("Close"),
    )
    require(idx.values.all { it >= 0 }) { "CSV must have Date,Open,High,Low,Close columns" }
    return lines.drop(1).mapNotNull { line ->
        val c = line.split(",")
        if (c.size < header.size) return@mapNotNull null
        Bar(
            date = c[idx.getValue("date")].trim(),
            open = c[idx.getValue("open")].trim().toDoubleOrNull() ?: return@mapNotNull null,
            high = c[idx.getValue("high")].trim().toDoubleOrNull() ?: return@mapNotNull null,
            low = c[idx.getValue("low")].trim().toDoubleOrNull() ?: return@mapNotNull null,
            close = c[idx.getValue("close")].trim().toDoubleOrNull() ?: return@mapNotNull null,
        )
    }.also {
        check(it.size == provenance.barCount) {
            "CSV bar count ${it.size} != provenance bar count ${provenance.barCount}"
        }
    }
}

val GC_F_PROVENANCE = DatasetProvenance(
    source = "Yahoo Finance, yfinance 1.8.0",
    symbol = "GC=F",
    label = "GC=F (COMEX gold futures — proxy for XAUUSD spot)",
    fetchedAt = "2026-11-10",
    barCount = 970,
    firstDate = "2023-01-03",
    lastDate = "2026-11-09",
)

val GC_F_5M_PROVENANCE = DatasetProvenance(
    source = "Yahoo Finance, yfinance 1.8.0",
    symbol = "GC=F",
    label = "GC=F 5-min (COMEX gold futures — proxy for XAUUSD spot)",
    fetchedAt = "2026-11-11",
    barCount = 13619,
    firstDate = "2026-09-01",
    lastDate = "2026-11-10",
)
