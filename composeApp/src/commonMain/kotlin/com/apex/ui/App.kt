package com.apex.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apex.domain.BacktestResult
import com.apex.range.MEASURED_RANGE_OOS_2026_11_11
import com.apex.range.MeasuredRange
import com.apex.range.displayText

/**
 * Minimal Phase-1 UI. It shows only measured numbers. The 20%/month
 * target is always shown with its annualised figure (791.6%/year) and
 * labelled as a benchmark, never a guarantee.
 *
 * The range section shows the LAST MEASURED R10 values (see
 * MeasuredRange.kt for provenance) — hit rate AND width, side by side.
 * The user's 80% claim for their tool is NEVER shown as a result: it is
 * untested (no 5-year 5-minute data, no tool code supplied). If [range]
 * is null, the UI shows "not measured".
 */
@Composable
fun ApexApp(
    backtest: BacktestResult?,
    range: MeasuredRange? = MEASURED_RANGE_OOS_2026_11_11,
) {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("APEX — XAUUSD paper trading", style = MaterialTheme.typography.headlineSmall)
            Text("Data: GC=F (COMEX gold futures — proxy for XAUUSD spot)")
            Text("Paper account: \$10,000 — NO live trading")
            Text("Target (benchmark, not a guarantee): 20%/month ≈ 791.6%/year")
            if (backtest == null) {
                Text("Backtest: not run")
            } else {
                Text("Backtest: ${backtest.trades.size} trades, " +
                    "return ${"%.2f".format(backtest.totalReturnPct)}%, " +
                    "max drawdown ${"%.2f".format(backtest.maxDrawdownPct)}%, " +
                    "win rate ${backtest.winRate?.let { "%.0f%%".format(it * 100) } ?: "n/a"}")
                Text("Verdict vs 20%/month target: " +
                    if (backtest.totalReturnPct >= 20.0) "above target (this run only)" else "BELOW target — see report")
            }
            if (range == null) {
                Text("Range forecast (5-min, strict): not measured")
            } else {
                Text("Range forecast (5-min, strict): ${range.displayText()}")
                Text("Range source: ${range.source}; ${range.window}")
                Text("Range note: 5-year 80% claim for the user's tool is UNTESTED — no 5y 5-min data, no tool code.")
            }
        }
    }
}
