package com.apex

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.apex.data.GC_F_PROVENANCE
import com.apex.data.parseCsvBars
import com.apex.domain.runBaselineBacktest
import com.apex.ui.ApexApp
import java.io.File

/** Desktop (JVM) entry — also the local build check: it runs the backtest on the bundled CSV. */
fun main() {
    val csv = File("data/gcf-daily.csv").readText()
    val bars = parseCsvBars(csv, GC_F_PROVENANCE)
    val result = runBaselineBacktest(bars, GC_F_PROVENANCE)
    println("APEX backtest: ${result.trades.size} trades, " +
        "return ${"%.2f".format(result.totalReturnPct)}%, " +
        "maxDD ${"%.2f".format(result.maxDrawdownPct)}%")
    application {
        Window(onCloseRequest = ::exitApplication, title = "APEX") {
            ApexApp(backtest = result)
        }
    }
}
