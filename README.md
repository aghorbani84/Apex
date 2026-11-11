# APEX

Kotlin Multiplatform paper-trading app for XAUUSD (gold), built on real data.
**Paper account only — $10,000, no live trading, no broker connection, no auto-execution.**

## Target — and the honesty rule (non-negotiable)

Target: grow a $10,000 account at least 20% per month.
20% per month compounded = **1.2^12 − 1 ≈ 791.6% per year**. That is a
benchmark to test against, never a guarantee. A backtest cannot prove
future returns. If the backtest fails the target, APEX reports the failure
with the numbers.

## Measured Strategy Results (GC=F Daily 2023-01-03–2026-11-09, 970 bars)

Source: Yahoo Finance `GC=F` (COMEX gold futures — **proxy for XAUUSD spot, not spot**). Costs: 0.05% round-trip.

| Strategy Engine | Full Return | Trades | Max DD | Profit Factor | In-Sample (2023-24) | Out-of-Sample (2025-26) | R6.2 Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SMA Baseline (20/50)** | +5.26% | 8 | 1.09% | 1.98 | +0.76% (5 trades) | +4.47% (3 trades) | **FAIL (Sample &amp; Target)** |
| **Donchian Breakout (20d)** | **+17.67%** | **48** | **5.03%** | **1.74** | **+7.91%** (19 trades) | **+9.76%** (29 trades, PF 1.64) | **FAIL (Below Target)** |
| **Range Filtered Strategy**| *Not run* | *Not run* | *Not run* | *Not run* | *Not run* | *Not run* | **UNMEASURED (Filter Off)** |

*Verdict explanation:*
- Donchian Breakout is the best measured engine so far (CAGR ~4.3%/year). It passes the max DD rule (5.03% ≤ 10%) and out-of-sample trade count (29 ≥ 20), but its OOS return of ~0.42%/month is below the 20%/month target, and in-sample trade count (19) is below the 30-trade minimum.
- Range-filtered strategy is labelled **Not run** because 5 years of verified 5-minute data does not exist in the repo.

## Range Prediction Module (R10) Measured Benchmark (2026-11-11)

- Dataset: Yahoo GC=F 5-minute bars (13,619 bars, 2026-09-01 to 2026-11-10, ~60 days).
- Method: `close ± 1.25 × ATR_5m(14)` evaluated on bar t+1.
- Out-of-sample (6,776 pairs): **85.82% strict full-candle hits**, **93.06% close hits**, median width **$9.50/oz (0.2317%)**.
- Baseline Comparison (v2 non-degenerate):
  - B1 Equal-Width Fixed Band ($9.50 width): **81.39% hits** -&gt; **Skill vs Equal Width: +4.43 pp**.
  - B1 Frozen-Width Band ($11.08 width): 87.18% hits (wider band hits trivially).
  - Shuffled random walk: 80.11% at $12.36 width.
- **Honesty Disclosure:** The user's claim of "80% accuracy over 5 years" remains **UNVERIFIED**. Yahoo intraday covers ~60 days only. An 80%+ containment rate is mathematically trivial if the band is widened past $10.00/oz. The range forecast is directionless and is **not an edge by itself**.

## Risk Rules (D-APX-010)

- 1% per trade ($100 on $10,000 start).
- 3% daily loss limit ($300).
- 6% weekly loss limit ($600).
- 10% max-drawdown kill-switch ($1,000 from peak).
- Minimum 2:1 reward:risk.
- Cost model: 0.05% round-trip.
- Paper account only. Zero real-money order routing.

## Build &amp; Test Instructions

Requires JDK 21 + Android SDK (compileSdk 36).

```sh
# Run tests on Desktop JVM (no Android SDK needed)
./gradlew :composeApp:desktopTest

# Run Desktop app
./gradlew :composeApp:run

# Build Android APK (requires Android SDK)
./gradlew :composeApp:assembleDebug
```
