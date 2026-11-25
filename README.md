# APEX

> A Kotlin Multiplatform paper-trading and backtesting app for gold (XAUUSD), built on real historical data.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)
![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin%20Multiplatform-Android%20%7C%20Desktop-7F52FF)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20Multiplatform-1.12-4285F4)
![Paper trading only](https://img.shields.io/badge/mode-paper%20trading%20only-lightgrey)

> **Paper account only.** A simulated $10,000 account with no live trading, no broker connection, and no automatic execution. Nothing here is financial advice.

## Overview

APEX tests simple trading strategies on gold price data and runs them through a paper account with strict risk rules. It uses Yahoo Finance `GC=F` (COMEX gold futures) as a proxy for XAUUSD spot, and it reports results honestly: a benchmark target of 20% per month (≈ 791.6% per year compounded) is used only to judge strategies, never presented as an expected return. When a strategy misses the target, the app says so with the numbers.

## Features

- **Backtest engines** – SMA crossover baseline (20/50) and a 20-day Donchian breakout, with a 0.05% round-trip cost model
- **In-sample / out-of-sample evaluation** – splits results by period and returns a pass/fail verdict with an explanation
- **Paper account simulation** – equity tracking, rejected-trade log, and a kill switch that halts trading until a manual risk review
- **Risk rules** – 1% risk per trade, 3% daily and 6% weekly loss limits, 10% max-drawdown kill switch, minimum 2:1 reward-to-risk
- **Range forecast** – an ATR-based next-bar price band (`close ± k × ATR`) with hit-rate measurement against baseline bands
- **Data provenance** – bundled daily `GC=F` CSV (2023-01-03 to 2026-11-09), a Yahoo Finance chart fetcher, and data-gap detection
- **UI** – Compose Multiplatform screens for Dashboard, Backtest, Trades, Range Forecast, and Data & Provenance, on Android and desktop (JVM)

## Recorded results

Results below are as recorded by the project on the bundled dataset; they are historical backtests and do not predict future returns.

### Strategy backtests (GC=F Daily 2023-01-03–2026-11-09, 970 bars)

Source: Yahoo Finance `GC=F` (COMEX gold futures — **proxy for XAUUSD spot, not spot**). Costs: 0.05% round-trip.

| Strategy Engine | Full Return | Trades | Max DD | Profit Factor | In-Sample (2023-24) | Out-of-Sample (2025-26) | R6.2 Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **SMA Baseline (20/50)** | +5.26% | 8 | 1.09% | 1.98 | +0.76% (5 trades) | +4.47% (3 trades) | **FAIL (Sample & Target)** |
| **Donchian Breakout (20d)** | **+17.67%** | **48** | **5.03%** | **1.74** | **+7.91%** (19 trades) | **+9.76%** (29 trades, PF 1.64) | **FAIL (Below Target)** |
| **Range Filtered Strategy**| *Not run* | *Not run* | *Not run* | *Not run* | *Not run* | *Not run* | **UNMEASURED (Filter Off)** |

*Verdict explanation:*
- Donchian Breakout is the best measured engine so far (CAGR ~4.3%/year). It passes the max DD rule (5.03% ≤ 10%) and out-of-sample trade count (29 ≥ 20), but its OOS return of ~0.42%/month is below the 20%/month target, and in-sample trade count (19) is below the 30-trade minimum.
- Range-filtered strategy is labelled **Not run** because 5 years of verified 5-minute data does not exist in the repo.

### Range prediction benchmark (2026-11-11)

- Dataset: Yahoo GC=F 5-minute bars (13,619 bars, 2026-09-01 to 2026-11-10, ~60 days).
- Method: `close ± 1.25 × ATR_5m(14)` evaluated on bar t+1.
- Out-of-sample (6,776 pairs): **85.82% strict full-candle hits**, **93.06% close hits**, median width **$9.50/oz (0.2317%)**.
- Baseline Comparison (v2 non-degenerate):
  - B1 Equal-Width Fixed Band ($9.50 width): **81.39% hits** → **Skill vs Equal Width: +4.43 pp**.
  - B1 Frozen-Width Band ($11.08 width): 87.18% hits (wider band hits trivially).
  - Shuffled random walk: 80.11% at $12.36 width.
- **Honesty Disclosure:** The original "80% accuracy over 5 years" claim remains **UNVERIFIED**. Yahoo intraday covers ~60 days only. An 80%+ containment rate is mathematically trivial if the band is widened past $10.00/oz. The range forecast is directionless and is **not an edge by itself**.

## Tech stack

| Area | Tools |
| --- | --- |
| Language | Kotlin 2.4 (Multiplatform: Android + JVM desktop) |
| UI | Compose Multiplatform 1.12, Material 3 |
| Concurrency | kotlinx.coroutines |
| Build | Gradle 9.7 (wrapper properties), Android Gradle Plugin 9.3 |
| Tests | `kotlin.test` in `commonTest` |

## Getting started

Requires JDK 21 and, for Android builds, the Android SDK (compile SDK 36).

```sh
git clone https://github.com/aghorbani84/Apex.git
cd Apex

./gradlew :composeApp:desktopTest     # run tests on the desktop JVM
./gradlew :composeApp:run             # run the desktop app
./gradlew :composeApp:assembleDebug   # build an Android debug APK
```

> The `gradlew` wrapper scripts are not committed yet. Generate them with a local Gradle installation (`gradle wrapper`) or run the same tasks with `gradle` directly.

## Project structure

```
composeApp/src/
├── commonMain/kotlin/com/apex/
│   ├── data/      # CSV parsing, Yahoo Finance service, HTTP fetcher (expect)
│   ├── domain/    # bars, strategies, backtests, risk, paper account, split evaluator
│   ├── range/     # ATR range predictor and measurement
│   └── ui/        # Compose app and charts
├── commonTest/    # backtest, Donchian, paper account, range, data tests
├── androidMain/   # Android entry point and HTTP fetcher
└── jvmMain/       # desktop entry point and HTTP fetcher
data/gcf-daily.csv # GC=F daily bars
```

## Author

Built by [aghorbani84](https://github.com/aghorbani84).
