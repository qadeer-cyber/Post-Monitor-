package com.affiliatemonitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.affiliatemonitor.app.data.Prefs
import com.affiliatemonitor.app.ui.AppNav
import com.affiliatemonitor.app.ui.BottomBar
import com.affiliatemonitor.app.ui.screens.OnboardingScreen
import com.affiliatemonitor.app.ui.theme.AppTheme
import com.affiliatemonitor.app.ui.theme.DeepBg
import com.affiliatemonitor.app.ui.theme.ElevBg
import com.affiliatemonitor.app.work.ScanWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate().
        val splash = installSplashScreen()
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                val ctx = LocalContext.current
                val onboardedFlow = remember { Prefs.isOnboarded(ctx) }
                val onboardedState = onboardedFlow.collectAsState(initial = null)
                if (onboardedState.value != null) keepSplash = false

                LaunchedEffect(onboardedState.value) {
                    if (onboardedState.value == true) {
                        // Re-up the periodic scan with current interval each time the app launches.
                        runCatching { ScanWorker.schedule(ctx) }
                    }
                }

                when (onboardedState.value) {
                    null -> Unit // still reading DataStore — splash covers us
                    false -> {
                        var done by remember { mutableStateOf(false) }
                        if (!done) OnboardingScreen(onDone = { done = true }) else AppShell()
                    }
                    true -> AppShell()
                }
            }
        }
    }
}

@Composable
private fun AppShell() {
    val nav = rememberNavController()
    Scaffold(
        containerColor = DeepBg,
        bottomBar = { BottomBar(nav) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to DeepBg,
                        1f to ElevBg,
                    )
                )
                .padding(padding)
        ) {
            AppNav(nav)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            0f to Color(0x2200E5FF),
                            1f to Color.Transparent,
                            radius = 900f,
                        )
                    )
            )
        }
    }
}
