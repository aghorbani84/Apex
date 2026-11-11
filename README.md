# APEX

Kotlin Multiplatform paper-trading app for XAUUSD (gold), built on real data.
**Paper account only — $10,000, no live trading, no broker connection, no auto-execution.**

## Target — and the honesty rule (non-negotiable)

Target: grow a $10,000 account at least 20% per month.
20% per month compounded = **1.2^12 − 1 ≈ 791.6% per year**. That is a
benchmark to test against, never a guarantee. A backtest cannot prove
future returns. If the backtest fails the target, APEX reports the failure
with the numbers.

## Measured baseline (Python, verified 2026-11-10)

Data: Yahoo Finance `GC=F` (COMEX gold futures — **proxy for XAUUSD spot,
not spot**), 970 daily bars, 2023-01-03–2026-11-09.
Strategy: SMA(20)/SMA(50) crossover, long-only, 1% risk, 2×ATR stop,
3×ATR target, 0.05% round-trip cost. Results: **+5.26% over 3.85 years,
CAGR 1.34%, 8 trades, max drawdown 1.09%**. Fails the 20%/month target
by roughly two orders of magnitude. See `../results/backtest.json`
(the Python backtest lives next to this scaffold in `apex-build/`).

## Strategy (D-APX-010)

Playbook adaptation **filtered by the Range Prediction module (R10)** —
trade containment at band edges + breakout-only, size inversely to
predicted range width. NOT the SMA baseline alone (kept only as a baseline).
The range filter is **DISABLED by default** until R10 survives its
falsification test (equal-width naive baselines, min 1,000 OOS bars,
full-candle-inside criterion). The user's 80%-accuracy claim for the
range tool is **UNVERIFIED** — it is never displayed as a result.

## Risk (decided 2026-11-10)

1% per trade, 3% daily, 6% weekly, 10% max-drawdown kill-switch.
Cost model: 0.05% round-trip (as measured in backtest.py).

## Build

Requires JDK 21 + Android SDK (compileSdk 36). Versions: Kotlin 2.4.21,
Compose Multiplatform 1.12.2, AGP 9.3.1, Gradle 9.7.0.

```sh
gradle :composeApp:jvmTest          # common tests on the JVM (no Android SDK needed)
gradle :composeApp:assembleDebug    # needs Android SDK
```

CI runs on every push/PR (`.github/workflows/ci.yml`).

## Layout

- `composeApp/src/commonMain/kotlin/com/apex/domain/` — Bar, Risk, Strategy, Backtest, PaperAccount
- `composeApp/src/commonMain/kotlin/com/apex/range/` — RangePredictor (R10, measurement only)
- `composeApp/src/commonMain/kotlin/com/apex/data/` — bundled GC=F CSV parser
- `composeApp/src/commonMain/kotlin/com/apex/ui/` — Compose UI (measured numbers only)
- `data/gcf-daily.csv` — versioned GC=F snapshot (970 bars)
