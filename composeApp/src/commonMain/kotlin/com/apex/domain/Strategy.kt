package com.apex.domain

enum class TradeMode { FADE_EDGE, BREAKOUT_ONLY, DISABLED }

enum class TradeSide { LONG, SHORT }

data class StrategyConfig(
    val mode: TradeMode = TradeMode.DISABLED,
    val smaFast: Int = 20,
    val smaSlow: Int = 50,
    val atrPeriod: Int = 14,
    val stopAtrMultiple: Double = 2.0,
    val targetAtrMultiple: Double = 3.0,
    val rangeK: Double = 1.25,
    val minRangeWidthUsd: Double = 0.0,
    val maxRangeWidthUsd: Double = Double.MAX_VALUE,
)

data class Trade(
    val entryDate: String,
    val exitDate: String,
    val entry: Double,
    val exit: Double,
    val sizeOz: Double,
    val pnl: Double,
    val why: String, // "target" | "stop" | "cross_exit" | "channel_reverse" | "close_at_end"
    val rangeFiltered: Boolean = false,
    val side: TradeSide = TradeSide.LONG,
    val rMultiple: Double = 0.0,
    val riskDollars: Double = 0.0,
)