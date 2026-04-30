package com.affiliatemonitor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.affiliatemonitor.app.data.Repository
import com.affiliatemonitor.app.data.SourceOut
import com.affiliatemonitor.app.data.SourceUpdate
import com.affiliatemonitor.app.ui.DangerButton
import com.affiliatemonitor.app.ui.ErrorBanner
import com.affiliatemonitor.app.ui.LoadingIndicator
import com.affiliatemonitor.app.ui.PrimaryButton
import com.affiliatemonitor.app.ui.ScreenScaffold
import com.affiliatemonitor.app.ui.SecondaryButton
import com.affiliatemonitor.app.ui.theme.GlassCard
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.NeonGreen
import com.affiliatemonitor.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun SourcesScreen() {
    val ctx = LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf<List<SourceOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var newUrl by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        error = null
        try { sources = repo.listSources() }
        catch (t: Throwable) { error = t.message ?: "Unknown error" }
        finally { loading = false }
    }

    LaunchedEffect(Unit) { refresh() }

    ScreenScaffold(title = "Sources", subtitle = "Public Facebook Pages to monitor") {
        Column(Modifier.fillMaxWidth()) {
            GlassCard(accent = NeonBlue) {
                Column {
                    Text("Add a public page URL", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        placeholder = { Text("https://www.facebook.com/YourPageName") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = NeonBlue,
                            unfocusedIndicatorColor = TextMuted,
                            cursorColor = NeonBlue,
                            focusedTextColor = androidx.compose.ui.graphics.Color(0xFFE6EDF3),
                            unfocusedTextColor = androidx.compose.ui.graphics.Color(0xFFE6EDF3),
                        ),
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = if (submitting) "Adding…" else "Add Source",
                        enabled = !submitting && newUrl.isNotBlank(),
                        onClick = {
                            scope.launch {
                                submitting = true
                                error = null
                                try {
                                    repo.addSource(newUrl.trim())
                                    newUrl = ""
                                    refresh()
                                } catch (t: Throwable) {
                                    error = t.message ?: "Failed to add source"
                                } finally {
                                    submitting = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                loading && sources.isEmpty() -> LoadingIndicator()
                error != null && sources.isEmpty() -> ErrorBanner(error!!) { scope.launch { refresh() } }
                sources.isEmpty() -> Text("No sources yet. Add one above.", color = TextMuted)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(sources, key = { it.id }) { src ->
                        SourceRow(
                            source = src,
                            onToggle = { enabled ->
                                scope.launch {
                                    try {
                                        repo.updateSource(src.id, SourceUpdate(enabled = enabled))
                                        refresh()
                                    } catch (t: Throwable) { error = t.message }
                                }
                            },
                            onScan = {
                                scope.launch {
                                    try { repo.scanSource(src.id); refresh() }
                                    catch (t: Throwable) { error = t.message }
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    try { repo.deleteSource(src.id); refresh() }
                                    catch (t: Throwable) { error = t.message }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: SourceOut,
    onToggle: (Boolean) -> Unit,
    onScan: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassCard(accent = if (source.enabled) NeonGreen else TextMuted) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        source.name ?: source.url,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(source.url, color = TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = source.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonGreen,
                        checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                    ),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Last checked: ${source.lastCheckedAt?.take(19)?.replace("T", " ") ?: "never"}   •   Posts found: ${source.postsFound}",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Scan", onScan)
                DangerButton("Remove", onDelete)
            }
        }
    }
}
