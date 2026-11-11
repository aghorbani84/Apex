package com.apex

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.apex.ui.ApexApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phase 1: UI shell only; the backtest runs in commonTest and in
        // backtest.py. Wiring the bundled CSV into the UI is T-APX-11.
        setContent { ApexApp(backtest = null) }
    }
}
