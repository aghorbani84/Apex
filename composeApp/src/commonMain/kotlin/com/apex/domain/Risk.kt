package com.apex.domain

/**
 * Risk rules — D-APX-010, decided by Glimmer 2026-11-10 under the user's
 * "decide yourself, think outside the box" instruction. Paper account only.
 * 1% per trade, 3% daily, 6% weekly, 10% max-drawdown kill-switch.
 * Cost model: 0.05% round-trip (as measured in backtest.py).
 */
data class RiskRules(
    val riskPerTradePct: Double = 0.01,
    val dailyLossLimitPct: Double = 0.03,
    val weeklyLossLimitPct: Double = 0.06,
    val maxDrawdownKillSwitchPct: Double = 0.10,
    val minRewardRisk: Double = 2.0,
    val roundTripCostPct: Double = 0.0005,
) {
    init {
        require(riskPerTradePct in 0.0..0.05) { "risk per trade must be 0–5%" }
        require(dailyLossLimitPct > riskPerTradePct) { "daily limit must exceed per-trade risk" }
        require(maxDrawdownKillSwitchPct > dailyLossLimitPct) { "kill-switch must exceed daily limit" }
    }
}

/** Position size in ounces: risk dollars divided by stop distance. */
fun positionSizeOz(equity: Double, stopDistance: Double, rules: RiskRules): Double {
    require(equity > 0 && stopDistance > 0)
    return (equity * rules.riskPerTradePct) / stopDistance
}

/** State of the paper account's risk limits on a given day/week. */
data class RiskState(
    val equity: Double,
    val peakEquity: Double,
    val dayPnlPct: Double,
    val weekPnlPct: Double,
) {
    fun maxDrawdownPct(): Double = if (peakEquity <= 0) 0.0 else (peakEquity - equity) / peakEquity
    fun killSwitchTripped(rules: RiskRules): Boolean = maxDrawdownPct() >= rules.maxDrawdownKillSwitchPct
    fun dailyHalted(rules: RiskRules): Boolean = dayPnlPct <= -rules.dailyLossLimitPct
    fun weeklyHalted(rules: RiskRules): Boolean = weekPnlPct <= -rules.weeklyLossLimitPct
    fun mayOpenTrade(rules: RiskRules): Boolean =
        !killSwitchTripped(rules) && !dailyHalted(rules) && !weeklyHalted(rules)
}
