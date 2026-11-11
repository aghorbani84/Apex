package com.apex.domain

/**
 * $10,000 PAPER account only (D-APX-010). No broker connection, no
 * real-money orders, no auto-execution. The account halts per RiskRules
 * and only resumes after a manual user review (logged with a timestamp).
 */
data class PaperAccount(
    val startEquity: Double = 10_000.0,
    val equity: Double = 10_000.0,
    val peakEquity: Double = 10_000.0,
    val halted: Boolean = false,
    val haltReason: String? = null,
) {
    fun applyTrade(pnl: Double, rules: RiskRules): PaperAccount {
        val newEquity = equity + pnl
        val newPeak = maxOf(peakEquity, newEquity)
        val state = RiskState(
            equity = newEquity,
            peakEquity = newPeak,
            dayPnlPct = 0.0, // day/week P&L are tracked by the caller per calendar day
            weekPnlPct = 0.0,
        )
        return if (state.killSwitchTripped(rules)) {
            copy(equity = newEquity, peakEquity = newPeak, halted = true,
                haltReason = "max drawdown kill-switch: %.2f%%".format(state.maxDrawdownPct() * 100))
        } else {
            copy(equity = newEquity, peakEquity = newPeak)
        }
    }
}

data class PaperSimulation(
    val account: PaperAccount,
    /** Trades in the input list (all of them, even if halted part-way). */
    val tradesSupplied: Int,
    /** Trades actually applied before a halt stopped the simulation, null if never halted. */
    val tradesApplied: Int,
    /** Index of the trade that tripped the halt, null if never halted. */
    val haltedAtTradeIndex: Int?,
)

/**
 * Replays backtest [trades] through a paper account, in order. Stops
 * applying trades once the account halts (kill-switch); the remaining
 * trades are reported as not applied. This is the paper-account
 * simulation of a backtest run — it is NOT live trading.
 */
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
        account = account.applyTrade(trade.pnl, rules)
        applied++
        if (account.halted) haltedAt = idx
    }
    return PaperSimulation(
        account = account,
        tradesSupplied = trades.size,
        tradesApplied = applied,
        haltedAtTradeIndex = haltedAt,
    )
}
