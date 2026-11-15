package com.langhost

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.langhost.ui.LockGate
import com.langhost.ui.navigation.LanGhostNavHost
import com.langhost.ui.theme.LanGhostTheme

class MainActivity : FragmentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            LanGhostTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LockGate {
                        LanGhostNavHost(windowSizeClass = windowSizeClass)
                    }
                }
            }
        }
    }
}
