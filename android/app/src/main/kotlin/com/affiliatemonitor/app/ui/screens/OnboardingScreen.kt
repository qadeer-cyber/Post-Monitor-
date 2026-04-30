package com.affiliatemonitor.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.affiliatemonitor.app.R
import com.affiliatemonitor.app.data.Prefs
import com.affiliatemonitor.app.ui.PrimaryButton
import com.affiliatemonitor.app.ui.SecondaryButton
import com.affiliatemonitor.app.ui.theme.DeepBg
import com.affiliatemonitor.app.ui.theme.ElevBg
import com.affiliatemonitor.app.ui.theme.GlassCard
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.NeonGreen
import com.affiliatemonitor.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

private const val TOTAL_STEPS = 2

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var amazonTag by remember { mutableStateOf(Prefs.DEFAULT_AMAZON_TAG) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to DeepBg,
                    1f to ElevBg,
                )
            ),
    ) {
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.splash_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        "Affiliate Post Monitor",
                        color = Color(0xFFE6EDF3),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "Premium deals monitoring  •  on-device only",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            StepDots(step, TOTAL_STEPS)
            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 3 })
                            .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 3 })
                    } else {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 3 })
                            .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 3 })
                    }
                },
                label = "onb-step",
            ) { current ->
                when (current) {
                    0 -> StepWelcome()
                    else -> StepAmazon(value = amazonTag, onChange = { amazonTag = it })
                }
            }

            Spacer(Modifier.height(18.dp))

            if (error != null) {
                GlassCard(accent = MaterialTheme.colorScheme.error) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (step > 0) {
                    SecondaryButton(
                        "Back",
                        onClick = { if (!saving) step-- },
                        modifier = Modifier.weight(1f),
                    )
                }
                PrimaryButton(
                    text = when (step) {
                        TOTAL_STEPS - 1 -> if (saving) "Finishing…" else "Finish"
                        else -> "Continue"
                    },
                    enabled = !saving && when (step) {
                        TOTAL_STEPS - 1 -> amazonTag.isNotBlank()
                        else -> true
                    },
                    onClick = {
                        if (step < TOTAL_STEPS - 1) {
                            step++
                        } else {
                            scope.launch {
                                saving = true
                                error = null
                                try {
                                    Prefs.setAmazonTag(ctx, amazonTag.trim())
                                    Prefs.setOnboarded(ctx, true)
                                    onDone()
                                } catch (t: Throwable) {
                                    error = t.message ?: "Failed to save"
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            val active = i <= current
            Box(
                Modifier
                    .size(width = if (i == current) 22.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (active) NeonBlue else Color(0x33FFFFFF)),
            )
        }
    }
}

@Composable
private fun StepWelcome() {
    GlassCard(accent = NeonBlue) {
        Column {
            Text(
                "Welcome to Affiliate Post Monitor",
                color = Color(0xFFE6EDF3),
                style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 32.sp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Watch public Facebook Pages for new Amazon product posts. We turn each one into a ready-to-copy caption with your affiliate tag.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            )
            Spacer(Modifier.height(18.dp))
            Bullet(Icons.Outlined.Visibility, "Public page monitoring only — you add the pages.")
            Bullet(Icons.Outlined.Widgets, "Auto-generates captions. You paste and post manually.")
            Bullet(Icons.Outlined.Sell, "Amazon links rewritten to your associate tag.")
            Bullet(Icons.Outlined.PhoneAndroid, "Runs entirely on your phone — no backend, no server.")
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    GlassCard(accent = NeonGreen) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = NeonGreen)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Manual posting only",
                    color = NeonGreen,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Disclosure(Icons.Outlined.Block, "No Facebook login, no passwords.")
            Disclosure(Icons.Outlined.Block, "No auto-posting, no auto-clicking.")
            Disclosure(Icons.Outlined.Block, "No private groups. No captcha bypass.")
            Disclosure(Icons.Outlined.Verified, "You copy the caption and post whenever you like.")
        }
    }
}

@Composable
private fun StepAmazon(value: String, onChange: (String) -> Unit) {
    GlassCard(accent = NeonGreen) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Sell, contentDescription = null, tint = NeonGreen)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Confirm Amazon tag",
                    color = Color(0xFFE6EDF3),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Every generated affiliate link will include this tag. We've prefilled yours — adjust if needed.",
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = onboardingTextColors(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Default marketplace: amazon.com. Other marketplaces are detected automatically per post (amazon.co.uk, .in, .ae, and 13 more).",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            )
        }
    }
}

@Composable
private fun Bullet(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = NeonBlue,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            color = Color(0xFFE6EDF3),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Disclosure(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier
                .padding(top = 3.dp)
                .size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text,
            color = Color(0xFFE6EDF3),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun onboardingTextColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = NeonBlue,
    unfocusedIndicatorColor = TextMuted,
    cursorColor = NeonBlue,
    focusedTextColor = Color(0xFFE6EDF3),
    unfocusedTextColor = Color(0xFFE6EDF3),
)
