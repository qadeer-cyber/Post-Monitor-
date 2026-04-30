package com.affiliatemonitor.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.affiliatemonitor.app.ui.theme.Danger
import com.affiliatemonitor.app.ui.theme.GlassCard
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.NeonGreen
import com.affiliatemonitor.app.ui.theme.TextMuted

@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.displaySmall, color = Color(0xFFE6EDF3))
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun StatTile(label: String, value: String, accent: Color = NeonBlue, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, accent = accent, padding = PaddingValues(16.dp)) {
        Column {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextMuted,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                color = accent,
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = NeonBlue,
            contentColor = Color(0xFF001218),
            disabledContainerColor = NeonBlue.copy(alpha = 0.35f),
        ),
        modifier = modifier,
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen),
    ) { Text(text) }
}

@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
    ) { Text(text) }
}

@Composable
fun LoadingIndicator() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = NeonBlue)
    }
}

@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null) {
    GlassCard(accent = Danger) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Danger),
                )
                Spacer(Modifier.padding(4.dp))
                Text("Error", color = Danger, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(message, color = Color(0xFFE6EDF3), style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) {
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Retry", onRetry)
            }
        }
    }
}

@Composable
fun GlowSeparator() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to NeonBlue.copy(alpha = 0.35f),
                    1f to Color.Transparent,
                )
            )
    )
}
