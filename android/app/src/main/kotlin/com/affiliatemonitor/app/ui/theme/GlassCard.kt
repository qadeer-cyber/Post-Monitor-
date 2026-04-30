package com.affiliatemonitor.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box

/**
 * Glassmorphism container: translucent fill, neon cyan border, subtle top
 * highlight gradient. Used everywhere a "card" is needed.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    accent: Color = NeonBlue,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val gradient = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.08f),
        1f to Color.White.copy(alpha = 0.02f),
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(gradient)
            .background(GlassFillDim)
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.25f)), shape)
            .padding(padding)
    ) {
        CompositionLocalProvider(LocalContentColor provides Color(0xFFE6EDF3)) {
            content()
        }
    }
}
