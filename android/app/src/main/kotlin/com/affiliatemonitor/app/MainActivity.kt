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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController

import com.affiliatemonitor.app.ui.AppNav
import com.affiliatemonitor.app.ui.BottomBar
import com.affiliatemonitor.app.ui.theme.AppTheme
import com.affiliatemonitor.app.ui.theme.DeepBg
import com.affiliatemonitor.app.ui.theme.ElevBg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppShell() } }
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
            // subtle top accent glow
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
