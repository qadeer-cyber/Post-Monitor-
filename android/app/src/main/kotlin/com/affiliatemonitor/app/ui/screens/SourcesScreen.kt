package com.affiliatemonitor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.affiliatemonitor.app.data.Repository
import com.affiliatemonitor.app.data.SourceOut
import com.affiliatemonitor.app.data.SourcePreview
import com.affiliatemonitor.app.data.SourceUpdate
import com.affiliatemonitor.app.ui.DangerButton
import com.affiliatemonitor.app.ui.ErrorBanner
import com.affiliatemonitor.app.ui.LoadingIndicator
import com.affiliatemonitor.app.ui.PrimaryButton
import com.affiliatemonitor.app.ui.ScreenScaffold
import com.affiliatemonitor.app.ui.SecondaryButton
import com.affiliatemonitor.app.ui.theme.Danger
import com.affiliatemonitor.app.ui.theme.GlassCard
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.NeonGreen
import com.affiliatemonitor.app.ui.theme.TextMuted
import com.affiliatemonitor.app.ui.theme.WarnAmber
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
    var validating by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<SourcePreview?>(null) }
    var saving by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        error = null
        try { sources = repo.listSources() }
        catch (t: Throwable) { error = t.message ?: "Unknown error" }
        finally { loading = false }
    }

    LaunchedEffect(Unit) { refresh() }

    ScreenScaffold(title = "Sources", subtitle = "Public Facebook Pages you monitor") {
        Column(Modifier.fillMaxWidth()) {
            GlassCard(accent = NeonBlue) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Link, contentDescription = null, tint = NeonBlue)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Add a public page URL",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "We'll preview the page before saving so you can confirm it's public and has recent posts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it; preview = null },
                        placeholder = { Text("https://www.facebook.com/YourPageName") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = urlTextColors(),
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = if (validating) "Checking…" else "Preview page",
                        enabled = !validating && !saving && newUrl.isNotBlank(),
                        onClick = {
                            scope.launch {
                                validating = true
                                error = null
                                preview = null
                                try {
                                    preview = repo.validateSource(newUrl.trim())
                                } catch (t: Throwable) {
                                    error = t.message ?: "Failed to validate URL"
                                } finally {
                                    validating = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Preview card — only shown after a successful preview call
            preview?.let { p ->
                Spacer(Modifier.height(12.dp))
                PreviewCard(
                    preview = p,
                    saving = saving,
                    onCancel = { preview = null },
                    onConfirm = {
                        scope.launch {
                            saving = true
                            error = null
                            try {
                                repo.addSource(
                                    url = p.url,
                                    name = p.pageName,
                                )
                                newUrl = ""
                                preview = null
                                refresh()
                            } catch (t: Throwable) {
                                error = t.message ?: "Failed to add source"
                            } finally {
                                saving = false
                            }
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            when {
                loading && sources.isEmpty() -> LoadingIndicator()
                error != null && sources.isEmpty() -> ErrorBanner(error!!) { scope.launch { refresh() } }
                sources.isEmpty() -> Text("No sources yet. Paste a public Facebook page URL above to get started.", color = TextMuted)
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
            if (error != null && sources.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(error!!)
            }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: SourcePreview,
    saving: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val accent = when {
        preview.isPublic -> NeonGreen
        preview.error != null -> Danger
        else -> WarnAmber
    }
    GlassCard(accent = accent) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (preview.isPublic) Icons.Outlined.Verified else Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = accent,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    preview.pageName ?: preview.url,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(preview.url, color = TextMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append(if (preview.isPublic) "Public page detected." else "Page does not appear public.")
                    append("  •  ")
                    append("${preview.recentPostsCount} recent post(s) visible")
                },
                color = if (preview.isPublic) NeonGreen else Danger,
                style = MaterialTheme.typography.bodyMedium,
            )
            preview.error?.let {
                Spacer(Modifier.height(6.dp))
                Text("Note: $it", color = WarnAmber, style = MaterialTheme.typography.bodySmall)
            }
            if (preview.samplePosts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Sample posts from this page:",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFFE6EDF3),
                )
                Spacer(Modifier.height(6.dp))
                preview.samplePosts.take(3).forEach { p ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            p.description?.takeIf { it.isNotBlank() } ?: p.url,
                            color = Color(0xFFE6EDF3),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (p.hasAmazonLink) {
                            Text(
                                "• contains Amazon link",
                                color = NeonGreen,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    "Cancel",
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    text = if (saving) "Saving…" else "Save source",
                    enabled = !saving && preview.isPublic,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
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
                    Text(
                        "Status: ${source.status}",
                        color = if (source.enabled) NeonGreen else TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
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
                "Last checked: ${source.lastCheckedAt?.take(19)?.replace("T", " ") ?: "never"}",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Posts detected: ${source.postsFound}   •   Valid Amazon posts: ${source.validAmazonPosts}",
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

@Composable
private fun urlTextColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = NeonBlue,
    unfocusedIndicatorColor = TextMuted,
    cursorColor = NeonBlue,
    focusedTextColor = Color(0xFFE6EDF3),
    unfocusedTextColor = Color(0xFFE6EDF3),
)
