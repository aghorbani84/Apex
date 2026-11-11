package com.apex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apex.data.DataGap
import com.apex.data.GC_F_PROVENANCE
import com.apex.data.YahooFinanceService
import com.apex.data.parseCsvBars
import com.apex.domain.BacktestResult
import com.apex.domain.DonchianConfig
import com.apex.domain.EngineSplitEvaluation
import com.apex.domain.GCF_DAILY_CSV
import com.apex.domain.PaperAccount
import com.apex.domain.RiskRules
import com.apex.domain.SplitVerdict
import com.apex.domain.Trade
import com.apex.domain.evaluateBacktestSplit
import com.apex.domain.runBaselineBacktest
import com.apex.domain.runDonchianBreakoutBacktest
import com.apex.domain.simulatePaperAccount
import com.apex.range.MEASURED_RANGE_OOS_2026_11_11
import com.apex.range.MeasuredRange
import com.apex.range.displayText
import kotlinx.coroutines.launch

@Composable
fun ApexApp(
    backtest: BacktestResult? = null,
    range: MeasuredRange? = MEASURED_RANGE_OOS_2026_11_11,
) {
    val initialBars = remember { parseCsvBars(GCF_DAILY_CSV, GC_F_PROVENANCE) }
    var currentBars by remember { mutableStateOf(initialBars) }
    val rules = remember { RiskRules() }
    val yahooService = remember { YahooFinanceService() }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(0) }
    var selectedEngine by remember { mutableStateOf("Donchian Breakout") }
    var rangeFilterEnabled by remember { mutableStateOf(false) }

    var refreshStatus by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    val smaResult = remember(currentBars) { runBaselineBacktest(currentBars, GC_F_PROVENANCE) }
    val donchianResult = remember(currentBars, rangeFilterEnabled) {
        runDonchianBreakoutBacktest(
            bars = currentBars,
            provenance = GC_F_PROVENANCE,
            config = DonchianConfig(rangeFiltered = rangeFilterEnabled),
            rules = rules,
        )
    }

    val activeSplitEval: EngineSplitEvaluation = remember(selectedEngine, rangeFilterEnabled, smaResult, donchianResult) {
        if (selectedEngine == "Donchian Breakout") {
            evaluateBacktestSplit(donchianResult, "Donchian Breakout (20-day Channel)")
        } else {
            evaluateBacktestSplit(smaResult, "SMA Baseline (20/50 Long)")
        }
    }

    var paperAccountState by remember(donchianResult) {
        val sim = simulatePaperAccount(donchianResult.trades, rules)
        mutableStateOf(sim.account)
    }

    val detectedGaps = remember(currentBars) { YahooFinanceService.detectGaps(currentBars) }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121418))) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("APEX — Gold Paper Trading", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("GC=F (COMEX gold futures — proxy for XAUUSD spot) | ${currentBars.size} Daily Candles", color = Color(0xFF90A4AE), fontSize = 12.sp)
                Text("Target (Benchmark, NOT a guarantee): 20.0%/month ≈ 791.6%/year", color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            val tabs = listOf("Dashboard", "Backtest", "Trades", "Range Forecast", "Data & Provenance")
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E222A),
                contentColor = Color(0xFF64B5F6),
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 12.sp) },
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                when (selectedTab) {
                    0 -> DashboardScreen(
                        account = paperAccountState,
                        rules = rules,
                        onResetKillSwitch = {
                            paperAccountState = paperAccountState.manualResume("2026-11-11 13:45 UTC")
                        },
                    )
                    1 -> BacktestScreen(
                        selectedEngine = selectedEngine,
                        onSelectEngine = { selectedEngine = it },
                        rangeFilterEnabled = rangeFilterEnabled,
                        onToggleRangeFilter = { rangeFilterEnabled = it },
                        evaluation = activeSplitEval,
                    )
                    2 -> TradesScreen(
                        trades = activeSplitEval.fullResult.trades,
                        paperAccount = paperAccountState,
                        onReplaySelected = {
                            val sim = simulatePaperAccount(activeSplitEval.fullResult.trades, rules)
                            paperAccountState = sim.account
                        },
                    )
                    3 -> RangeScreen(range = range)
                    4 -> DataScreen(
                        provenance = GC_F_PROVENANCE,
                        barCount = currentBars.size,
                        firstDate = currentBars.firstOrNull()?.date ?: "n/a",
                        lastDate = currentBars.lastOrNull()?.date ?: "n/a",
                        gaps = detectedGaps,
                        isRefreshing = isRefreshing,
                        refreshStatus = refreshStatus,
                        onTriggerRefresh = {
                            scope.launch {
                                isRefreshing = true
                                refreshStatus = "Connecting to query1.finance.yahoo.com/v8/finance/chart/GC=F..."
                                val res = yahooService.refreshGcF()
                                res.onSuccess {
                                    currentBars = it
                                    refreshStatus = "Refreshed successfully: ${it.size} bars cached."
                                }.onFailure {
                                    refreshStatus = "Fetch failed: ${it.message ?: "Network error"}. Retained cached snapshot."
                                }
                                isRefreshing = false
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    account: PaperAccount,
    rules: RiskRules,
    onResetKillSwitch: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("PAPER ACCOUNT OVERVIEW (NO LIVE TRADING)", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Current Equity", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                            Text("$${"%.2f".format(account.equity)}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Initial Balance", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                            Text("$${"%.2f".format(account.startEquity)}", color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val totalPnl = account.equity - account.startEquity
                    val totalPnlPct = (totalPnl / account.startEquity) * 100.0
                    val pnlColor = if (totalPnl >= 0) Color(0xFF81C784) else Color(0xFFE57373)
                    Text("Total Net PnL: ${if (totalPnl >= 0) "+" else ""}${"%.2f".format(totalPnl)} (${"%.2f".format(totalPnlPct)}%)", color = pnlColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (account.halted) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1515)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("TRADING HALTED — KILL SWITCH ACTIVE", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(account.haltReason ?: "Drawdown limit reached.", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onResetKillSwitch, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                            Text("Manual Risk Review & Resume Trading")
                        }
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("RISK LIMIT GAUGES", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LimitRow("Max Drawdown Kill-Switch", "${"%.2f".format(account.currentDrawdownPct() * 100)}%", "10.0%", account.currentDrawdownPct() >= rules.maxDrawdownKillSwitchPct)
                    LimitRow("Daily Loss Limit", "${"%.2f".format(account.dayPnlPct * 100)}%", "3.0%", account.dayPnlPct <= -rules.dailyLossLimitPct)
                    LimitRow("Weekly Loss Limit", "${"%.2f".format(account.weekPnlPct * 100)}%", "6.0%", account.weekPnlPct <= -rules.weeklyLossLimitPct)
                    LimitRow("Per-Trade Risk", "1.00%", "1.00%", false)
                    LimitRow("Round-Trip Friction", "0.05%", "0.05%", false)
                }
            }
        }
    }
}

@Composable
fun LimitRow(label: String, current: String, limit: String, tripped: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Color(0xFFCFD8DC), fontSize = 12.sp)
        Text(
            "$current (Limit: $limit)",
            color = if (tripped) Color(0xFFFF5252) else Color(0xFF81C784),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun BacktestScreen(
    selectedEngine: String,
    onSelectEngine: (String) -> Unit,
    rangeFilterEnabled: Boolean,
    onToggleRangeFilter: (Boolean) -> Unit,
    evaluation: EngineSplitEvaluation,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("STRATEGY ENGINE SELECTOR", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSelectEngine("Donchian Breakout") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedEngine == "Donchian Breakout") Color(0xFF1976D2) else Color(0xFF2C3440),
                            ),
                        ) {
                            Text("Donchian Breakout", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onSelectEngine("SMA Baseline") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedEngine == "SMA Baseline") Color(0xFF1976D2) else Color(0xFF2C3440),
                            ),
                        ) {
                            Text("SMA Baseline", fontSize = 11.sp)
                        }
                    }

                    if (selectedEngine == "Donchian Breakout") {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = rangeFilterEnabled, onCheckedChange = onToggleRangeFilter)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Column {
                                Text("Filter by Range Prediction Module (R10)", color = Color.White, fontSize = 12.sp)
                                if (rangeFilterEnabled) {
                                    Text("EXPERIMENTAL — Range edge unproven until R10 falsification completed", color = Color(0xFFFFB74D), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            val verdictColor = when (evaluation.verdict) {
                SplitVerdict.PASS -> Color(0xFF1B5E20)
                SplitVerdict.FAIL_BELOW_TARGET -> Color(0xFF4A1515)
                SplitVerdict.INSUFFICIENT_SAMPLE -> Color(0xFF422C00)
            }
            Card(colors = CardDefaults.cardColors(containerColor = verdictColor), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("VERDICT VS 20%/MONTH BENCHMARK", color = Color(0xFFFFD54F), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(evaluation.verdictExplanation, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("IN-SAMPLE VS OUT-OF-SAMPLE SPLIT TABLE", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SplitTableRow("Metric", "In-Sample (2023-24)", "Out-Sample (2025-26)", "Full Period", isHeader = true)
                    Divider(color = Color(0xFF37474F), thickness = 1.dp)
                    SplitTableRow("Trades", "${evaluation.inSampleResult.trades.size}", "${evaluation.outOfSampleResult.trades.size}", "${evaluation.fullResult.trades.size}")
                    SplitTableRow("Total Return", "${"%.2f".format(evaluation.inSampleResult.totalReturnPct)}%", "${"%.2f".format(evaluation.outOfSampleResult.totalReturnPct)}%", "${"%.2f".format(evaluation.fullResult.totalReturnPct)}%")
                    SplitTableRow("Monthly Return", "n/a", "${"%.2f".format(evaluation.oosMonthlyReturnPct)}%/mo", "n/a")
                    SplitTableRow("Max Drawdown", "${"%.2f".format(evaluation.inSampleResult.maxDrawdownPct)}%", "${"%.2f".format(evaluation.outOfSampleResult.maxDrawdownPct)}%", "${"%.2f".format(evaluation.fullResult.maxDrawdownPct)}%")
                    SplitTableRow("Win Rate", evaluation.inSampleResult.winRate?.let { "${"%.0f".format(it * 100)}%" } ?: "n/a", evaluation.outOfSampleResult.winRate?.let { "${"%.0f".format(it * 100)}%" } ?: "n/a", evaluation.fullResult.winRate?.let { "${"%.0f".format(it * 100)}%" } ?: "n/a")
                    SplitTableRow("Profit Factor", evaluation.inSampleResult.profitFactor?.let { "%.2f".format(it) } ?: "n/a", evaluation.outOfSampleResult.profitFactor?.let { "%.2f".format(it) } ?: "n/a", evaluation.fullResult.profitFactor?.let { "%.2f".format(it) } ?: "n/a")
                }
            }
        }

        item {
            Text("DRAWDOWN UNDERWATER PROFILE (10% KILL-SWITCH THRESHOLD)", color = Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            DrawdownChart(drawdownSeries = evaluation.drawdownSeries)
        }

        item {
            Text("MONTHLY RETURN DISTRIBUTION (%)", color = Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            MonthlyReturnsChart(monthlyReturns = evaluation.monthlyReturns)
        }

        item {
            Text("MONTH-BY-MONTH RETURN LOG", color = Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        items(evaluation.monthlyReturns) { m ->
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF1E222A)).padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${m.yearMonth} (${if (m.isOutOfSample) "OOS" else "IS"})", color = Color.White, fontSize = 12.sp)
                val retColor = if (m.returnPct >= 0) Color(0xFF81C784) else Color(0xFFE57373)
                Text("${if (m.returnPct >= 0) "+" else ""}${"%.2f".format(m.returnPct)}%", color = retColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SplitTableRow(col1: String, col2: String, col3: String, col4: String, isHeader: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(col1, color = if (isHeader) Color(0xFFFFB74D) else Color(0xFFB0BEC5), fontSize = 11.sp, modifier = Modifier.weight(1.2f))
        Text(col2, color = if (isHeader) Color(0xFFFFB74D) else Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(col3, color = if (isHeader) Color(0xFFFFB74D) else Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(col4, color = if (isHeader) Color(0xFFFFB74D) else Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun TradesScreen(
    trades: List<Trade>,
    paperAccount: PaperAccount,
    onReplaySelected: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("PAPER ACCOUNT TRADE REPLAY", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Replay all ${trades.size} backtest trades through the paper account risk engine.", color = Color.White, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onReplaySelected, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))) {
                        Text("Replay Engine Trades on Paper Account")
                    }
                }
            }
        }

        if (paperAccount.rejectedTrades.isNotEmpty()) {
            item {
                Text("REJECTED TRADES LOG (HALTED BY RISK RULES)", color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            items(paperAccount.rejectedTrades) { rej ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1B1B)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Date: ${rej.date} | Side: ${rej.attemptedSide}", color = Color.White, fontSize = 11.sp)
                        Text("Reason: ${rej.reason}", color = Color(0xFFFF8A80), fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Text("TRADE LOG (${trades.size} TRADES)", color = Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        items(trades) { t ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${t.side} | ${t.entryDate} → ${t.exitDate}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        val pnlColor = if (t.pnl >= 0) Color(0xFF81C784) else Color(0xFFE57373)
                        Text("${if (t.pnl >= 0) "+" else ""}${"%.2f".format(t.pnl)} (${"%.2f".format(t.rMultiple)}R)", color = pnlColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Entry: $${"%.2f".format(t.entry)} | Exit: $${"%.2f".format(t.exit)} | Size: ${"%.2f".format(t.sizeOz)} oz | Reason: ${t.why}", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                    if (t.rangeFiltered) {
                        Text("Tagged: Range Filtered", color = Color(0xFFFFB74D), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RangeScreen(range: MeasuredRange?) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF263238)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("R10 5-MINUTE RANGE PREDICTION MODULE", color = Color(0xFF80D8FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Directionless containment forecast for bar t+1: [Close_t - k×ATR, Close_t + k×ATR]", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("MEASURED OUT-OF-SAMPLE BENCHMARK", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (range == null) {
                        Text("Status: Not measured", color = Color.White, fontSize = 13.sp)
                    } else {
                        Text(range.displayText(), color = Color.White, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Window: ${range.window}", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                        Text("Source: ${range.source}", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("V2 NAIVE BASELINE COMPARISON (STRICT)", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("• Forecast (k=1.25, width $9.50 OOS): 85.82% strict hits", color = Color.White, fontSize = 12.sp)
                    Text("• B1 Equal-Width Fixed Band ($9.50 width): 81.39% hits", color = Color.White, fontSize = 12.sp)
                    Text("• Skill vs Equal Width: +4.43 pp (small consistent skill)", color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("• B1 Frozen-Width Band ($11.08 width): 87.18% hits (wider band hits trivially)", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                    Text("• Return-shuffled random walk: 80.11% at $12.36 width", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("MANDATORY HONEST DISCLOSURE", color = Color(0xFFFFCC80), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "The user's claim of '80% accuracy over 5 years' remains UNVERIFIED. Yahoo intraday provides ~60 days of 5-min data only; no 5-year 5-minute dataset or proprietary tool code has been provided. An 80%+ containment rate is mathematically trivial if the band is widened past $10.00/oz.",
                        color = Color.White,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun DataScreen(
    provenance: DatasetProvenance,
    barCount: Int,
    firstDate: String,
    lastDate: String,
    gaps: List<DataGap>,
    isRefreshing: Boolean,
    refreshStatus: String?,
    onTriggerRefresh: () -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("ACTIVE DATASET & PROVENANCE", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Symbol: ${provenance.symbol}", color = Color.White, fontSize = 13.sp)
                    Text("Label: ${provenance.label}", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                    Text("Source: ${provenance.source}", color = Color(0xFFB0BEC5), fontSize = 12.sp)
                    Text("Bar Count: $barCount daily candles", color = Color.White, fontSize = 12.sp)
                    Text("Date Range: $firstDate to $lastDate", color = Color.White, fontSize = 12.sp)
                    Text("Gaps: Gaps stay gaps (no interpolation / no invented candles)", color = Color(0xFF81C784), fontSize = 12.sp)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("YAHOO FINANCE LIVE REFRESH (GC=F ONLY)", color = Color(0xFF90A4AE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Endpoint: query1.finance.yahoo.com/v8/finance/chart/GC=F", color = Color(0xFFB0BEC5), fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onTriggerRefresh,
                        enabled = !isRefreshing,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    ) {
                        Text(if (isRefreshing) "Refreshing..." else "Trigger User Refresh from Yahoo Finance")
                    }
                    if (refreshStatus != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(refreshStatus, color = Color(0xFFFFB74D), fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Text("DETECTED CALENDAR GAPS (> 3 DAYS)", color = Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        if (gaps.isEmpty()) {
            item {
                Text("No calendar gaps exceeding normal weekends detected.", color = Color(0xFF81C784), fontSize = 12.sp)
            }
        } else {
            items(gaps) { gap ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E222A)), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text("${gap.fromDate} → ${gap.toDate}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(gap.description, color = Color(0xFF90A4AE), fontSize = 11.sp)
                        }
                        Text("${gap.calendarDays} days", color = Color(0xFFFFB74D), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
