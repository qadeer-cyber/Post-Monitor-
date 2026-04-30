package com.affiliatemonitor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.affiliatemonitor.app.data.Prefs
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val backendUrlFlow: Flow<String> = remember { Prefs.backendUrl(ctx) }
    val storedBackendUrl by backendUrlFlow.collectAsState(initial = "http://10.0.2.2:8000/")

    var backendUrl by remember { mutableStateOf("") }
    var loadedPrefs by remember { mutableStateOf(false) }
    LaunchedEffect(storedBackendUrl) {
        if (!loadedPrefs) {
            backendUrl = storedBackendUrl
            loadedPrefs = true
        }
    }

    var settings by remember { mutableStateOf<SettingsOut?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var tag by remember { mutableStateOf("") }
    var intervalMin by remember { mutableStateOf("") }
    var dailyLimit by remember { mutableStateOf("") }
    var delaySec by remember { mutableStateOf("") }
    var testMode by remember { mutableStateOf(false) }

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
            testMode = s.testMode
        } catch (t: Throwable) {
            error = t.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(storedBackendUrl) { refresh() }

    ScreenScaffold(title = "Settings", subtitle = "Backend, associate tag, scan controls") {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            GlassCard(accent = NeonBlue) {
                Column {
                    Text("Backend URL", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Where the app talks to your FastAPI backend. Use http://10.0.2.2:8000/ from the Android emulator to reach localhost.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backendUrl,
                        onValueChange = { backendUrl = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = textColors(),
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Save backend URL",
                        onClick = {
                            scope.launch {
                                Prefs.setBackendUrl(ctx, backendUrl)
                                message = "Backend URL saved"
                                refresh()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Test mode", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.weight(1f))
                                Switch(
                                    checked = testMode,
                                    onCheckedChange = { testMode = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = NeonGreen,
                                        checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                                    ),
                                )
                            }
                            Text(
                                "When on, the backend uses bundled sample data and makes no outbound requests.",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall,
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
                                                testMode = testMode,
                                            )
                                            val out = Repository(ctx).patchSettings(update)
                                            settings = out
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
