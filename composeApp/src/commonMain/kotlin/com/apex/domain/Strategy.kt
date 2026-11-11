package com.apex.domain

/**
 * Strategy — D-APX-010: playbook adaptation FILTERED by the Range Prediction
 * module (R10). NOT the SMA baseline alone (SMA 20/50 long-only returned
 * +5.26% over 3.85y, CAGR 1.34%, 8 trades — it fails the 20%/month target
 * and is kept only as a baseline in backtest.py).
 *
 * Range-as-core-edge design (from ~/memory/out-of-box-thoughts.md):
 * the range forecast is directionless, so the edge — if it survives
 * falsification — is containment, not direction:
 *  1. Fade the band edge: at the upper edge, short the containment with a
 *     stop just outside the band; at the lower edge, long it. Only when the
 *     playbook bias (H4) agrees with the fade direction.
 *  2. Breakout-only: do NOTHING inside the range; trade only a confirmed
 *     break of the band, with a close back inside = failed break = exit.
 *  3. Size inversely to predicted range width: narrow band = tight stop =
 *     larger size (still capped by RiskRules); wide band = smaller size.
 *  4. Session filter: skip playbook setups when the predicted 5-min range
 *     is abnormally wide (news) or abnormally narrow (dead market).
 * Until R10 survives its falsification test, the range filter is DISABLED
 * by default and every range-filtered trade is labelled as experimental.
 */
enum class TradeMode { FADE_EDGE, BREAKOUT_ONLY, DISABLED }

data class StrategyConfig(
    val mode: TradeMode = TradeMode.DISABLED, // range filter off until R10 is measured
    val smaFast: Int = 20, // baseline only — kept for comparison, not the edge
    val smaSlow: Int = 50,
    val atrPeriod: Int = 14,
    val stopAtrMultiple: Double = 2.0,
    val targetAtrMultiple: Double = 3.0,
    val rangeK: Double = 1.0, // band half-width = k × ATR_5m(14); frozen after in-sample choice
    val minRangeWidthUsd: Double = 0.0, // skip setups below this width (dead market)
    val maxRangeWidthUsd: Double = Double.MAX_VALUE, // skip setups above this width (news)
)

data class Trade(
    val entryDate: String,
    val exitDate: String,
    val entry: Double,
    val exit: Double,
    val sizeOz: Double,
    val pnl: Double,
    val why: String, // "target" | "stop" | "cross_exit" | "range_break_fail" | "fade_edge"
    val rangeFiltered: Boolean = false,
)
