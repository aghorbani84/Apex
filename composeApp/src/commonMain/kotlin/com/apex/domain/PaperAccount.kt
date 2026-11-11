package com.apex.domain

data class RejectedTradeLog(
    val date: String,
    val attemptedSide: TradeSide,
    val reason: String,
)

data class PaperAccount(
    val startEquity: Double = 10_000.0,
    val equity: Double = 10_000.0,
    val peakEquity: Double = 10_000.0,
    val dayPnlPct: Double = 0.0,
    val weekPnlPct: Double = 0.0,
    val halted: Boolean = false,
    val haltReason: String? = null,
    val appliedTrades: List<Trade> = emptyList(),
    val rejectedTrades: List<RejectedTradeLog> = emptyList(),
    val manualResumes: List<String> = emptyList(),
) {
    fun currentDrawdownPct(): Double = if (peakEquity <= 0) 0.0 else (peakEquity - equity) / peakEquity

    fun applyTrade(trade: Trade, rules: RiskRules): PaperAccount {
        if (halted) return this

        val riskState = RiskState(
            equity = equity,
            peakEquity = peakEquity,
            dayPnlPct = dayPnlPct,
            weekPnlPct = weekPnlPct,
        )

        // Pre-trade risk barrier checks
        if (!riskState.mayOpenTrade(rules)) {
            val rejection = when {
                riskState.killSwitchTripped(rules) -> "Kill switch tripped (drawdown >= ${rules.maxDrawdownKillSwitchPct * 100}%)"
                riskState.dailyHalted(rules) -> "Daily loss limit hit (${rules.dailyLossLimitPct * 100}%)"
                else -> "Weekly loss limit hit (${rules.weeklyLossLimitPct * 100}%)"
            }
            return copy(
                rejectedTrades = rejectedTrades + RejectedTradeLog(trade.entryDate, trade.side, rejection),
            )
        }

        val newEquity = equity + trade.pnl
        val newPeak = maxOf(peakEquity, newEquity)
        val newDayPnl = dayPnlPct + (trade.pnl / equity)
        val newWeekPnl = weekPnlPct + (trade.pnl / equity)

        val updatedState = RiskState(
            equity = newEquity,
            peakEquity = newPeak,
            dayPnlPct = newDayPnl,
            weekPnlPct = newWeekPnl,
        )

        return if (updatedState.killSwitchTripped(rules)) {
            copy(
                equity = newEquity,
                peakEquity = newPeak,
                dayPnlPct = newDayPnl,
                weekPnlPct = newWeekPnl,
                halted = true,
                haltReason = "Max drawdown kill-switch: ${"%.2f".format(updatedState.maxDrawdownPct() * 100)}% >= 10.0%",
                appliedTrades = appliedTrades + trade,
            )
        } else {
            copy(
                equity = newEquity,
                peakEquity = newPeak,
                dayPnlPct = newDayPnl,
                weekPnlPct = newWeekPnl,
                appliedTrades = appliedTrades + trade,
            )
        }
    }

    fun manualResume(timestamp: String, note: String = "Manual risk review"): PaperAccount {
        return copy(
            halted = false,
            haltReason = null,
            peakEquity = equity, // Reset high water mark on manual resume
            manualResumes = manualResumes + "[$timestamp] $note (Equity: $${"%.2f".format(equity)})",
        )
    }
}

data class PaperSimulation(
    val account: PaperAccount,
    val tradesSupplied: Int,
    val tradesApplied: Int,
    val haltedAtTradeIndex: Int?,
)

fun simulatePaperAccount(
    trades: List<Trade>,
    rules: RiskRules,
    startEquity: Double = 10_000.0,
): PaperSimulation {
    var account = PaperAccount(startEquity = startEquity, equity = startEquity, peakEquity = startEquity)
    var applied = 0
    var haltedAt: Int? = null

    for ((idx, trade) in trades.withIndex()) {
        if (account.halted) break
        account = account.applyTrade(trade, rules)
        applied++
        if (account.halted) {
            haltedAt = idx
            break
        }
    }
    return PaperSimulation(
        account = account,
        tradesSupplied = trades.size,
        tradesApplied = applied,
        haltedAtTradeIndex = haltedAt,
    )
}