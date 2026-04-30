package com.affiliatemonitor.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.affiliatemonitor.app.data.Repository
import com.affiliatemonitor.app.data.SettingsOut
import com.affiliatemonitor.app.data.SettingsUpdate
import com.affiliatemonitor.app.ui.ErrorBanner
import com.affiliatemonitor.app.ui.LoadingIndicator
import com.affiliatemonitor.app.ui.PrimaryButton
import com.affiliatemonitor.app.ui.ScreenScaffold
import com.affiliatemonitor.app.ui.theme.GlassCard
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.NeonGreen
import com.affiliatemonitor.app.ui.theme.TextMuted
import com.affiliatemonitor.app.work.ScanWorker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var settings by remember { mutableStateOf<SettingsOut?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var tag by remember { mutableStateOf("") }
    var intervalMin by remember { mutableStateOf("") }
    var dailyLimit by remember { mutableStateOf("") }
    var delaySec by remember { mutableStateOf("") }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            val s = Repository(ctx).getSettings()
            settings = s
            tag = s.amazonAssociateTag
            intervalMin = s.scanIntervalMinutes.toString()
            dailyLimit = s.dailyImportLimit.toString()
            delaySec = s.delayBetweenPageScansSeconds.toString()
        } catch (t: Throwable) {
            error = t.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    ScreenScaffold(title = "Settings", subtitle = "Associate tag, scan controls — all on-device") {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            GlassCard(accent = NeonBlue) {
                Column {
                    Text("Android-only mode", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This app runs entirely on your phone. Sources, posts, scheduling, link rewriting and dedup all happen locally — there's no backend URL to configure and no server to run.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            when {
                loading && settings == null -> LoadingIndicator()
                error != null && settings == null -> ErrorBanner(error!!) { scope.launch { refresh() } }
                else -> {
                    GlassCard(accent = NeonGreen) {
                        Column {
                            Text("Amazon Associate tag", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = tag,
                                onValueChange = { tag = it },
                                singleLine = true,
                                placeholder = { Text("yourtag-20") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textColors(),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Scan interval (minutes)", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = intervalMin,
                                onValueChange = { intervalMin = it.filter { ch -> ch.isDigit() } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textColors(),
                            )
                            Text(
                                "Android limits periodic background work to a 15-minute minimum. Default is 60 minutes.",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Daily import limit", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = dailyLimit,
                                onValueChange = { dailyLimit = it.filter { ch -> ch.isDigit() } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textColors(),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Delay between page scans (seconds)", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = delaySec,
                                onValueChange = { delaySec = it.filter { ch -> ch.isDigit() } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = textColors(),
                            )
                            Spacer(Modifier.height(12.dp))
                            PrimaryButton(
                                text = if (saving) "Saving…" else "Save settings",
                                enabled = !saving,
                                onClick = {
                                    scope.launch {
                                        saving = true
                                        error = null
                                        message = null
                                        try {
                                            val update = SettingsUpdate(
                                                amazonAssociateTag = tag.ifBlank { null },
                                                scanIntervalMinutes = intervalMin.toIntOrNull(),
                                                dailyImportLimit = dailyLimit.toIntOrNull(),
                                                delayBetweenPageScansSeconds = delaySec.toIntOrNull(),
                                            )
                                            val out = Repository(ctx).patchSettings(update)
                                            settings = out
                                            // Reschedule background scans with the new interval.
                                            ScanWorker.schedule(ctx)
                                            message = "Settings saved"
                                        } catch (t: Throwable) {
                                            error = t.message ?: "Failed to save settings"
                                        } finally {
                                            saving = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (message != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(message!!, color = NeonGreen, style = MaterialTheme.typography.bodySmall)
                            }
                            if (error != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun textColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = NeonBlue,
    unfocusedIndicatorColor = TextMuted,
    cursorColor = NeonBlue,
    focusedTextColor = Color(0xFFE6EDF3),
    unfocusedTextColor = Color(0xFFE6EDF3),
)
