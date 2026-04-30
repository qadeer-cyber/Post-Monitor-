package com.affiliatemonitor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.affiliatemonitor.app.data.ImportFeedback
import com.affiliatemonitor.app.data.Prefs
import com.affiliatemonitor.app.data.Repository
import com.affiliatemonitor.app.ui.PrimaryButton
import com.affiliatemonitor.app.ui.theme.GlassCard
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.NeonGreen
import com.affiliatemonitor.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun InputHubScreen() {
    val context = LocalContext.current
    val repo = remember { Repository(context) }
    val scope = rememberCoroutineScope()

    var fbUrl by remember { mutableStateOf("") }
    var amazonUrl by remember { mutableStateOf("") }
    var redditEnabled by remember { mutableStateOf(false) }
    var redditSubs by remember { mutableStateOf("") }
    var fbBusy by remember { mutableStateOf(false) }
    var amzBusy by remember { mutableStateOf(false) }
    var redditBusy by remember { mutableStateOf(false) }
    var lastFbResult by remember { mutableStateOf<ImportFeedback?>(null) }
    var lastAmzResult by remember { mutableStateOf<ImportFeedback?>(null) }
    var lastRedditResult by remember { mutableStateOf<ImportFeedback?>(null) }

    LaunchedEffect(Unit) {
        redditEnabled = Prefs.redditFeedEnabledValue(context)
        redditSubs = Prefs.redditSubsValue(context)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "Input Hub",
            style = MaterialTheme.typography.displaySmall,
            color = Color(0xFFE6EDF3),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Paste a Facebook post URL, an Amazon link, or enable the Reddit deals feed " +
                "to keep your queue full. Manual posting only.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
        )
        Spacer(Modifier.height(20.dp))

        // ── Card 1: Import Facebook post URL ────────────────────────────
        GlassCard(accent = NeonBlue) {
            Column {
                Text(
                    "Import Facebook post",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NeonBlue,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Paste a public Facebook post URL — the app will render it in a hidden " +
                        "browser, pull out any Amazon link, and add a deal post to the queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = fbUrl,
                    onValueChange = { fbUrl = it },
                    label = { Text("https://www.facebook.com/.../posts/...") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = inputColors(),
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (fbBusy) "Importing…" else "Import post",
                    onClick = {
                        if (fbBusy) return@PrimaryButton
                        fbBusy = true
                        lastFbResult = null
                        scope.launch {
                            val res = runCatching { repo.importFacebookPostUrl(fbUrl) }
                                .getOrElse { ImportFeedback(false, 0, 0, 1, "Error: ${it.message}") }
                            lastFbResult = res
                            if (res.ok) fbUrl = ""
                            fbBusy = false
                        }
                    },
                    enabled = !fbBusy && fbUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                lastFbResult?.let { res ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        res.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (res.ok) NeonGreen else TextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Card 2: Import Amazon URL ───────────────────────────────────
        GlassCard(accent = NeonGreen) {
            Column {
                Text(
                    "Import Amazon link",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NeonGreen,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Paste an Amazon product URL (or amzn.to). The app extracts the ASIN, " +
                        "fetches the product title when possible, and builds a deal post.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amazonUrl,
                    onValueChange = { amazonUrl = it },
                    label = { Text("https://www.amazon.com/dp/XXXXXXXXXX") },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                    colors = inputColors(),
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = if (amzBusy) "Importing…" else "Import link",
                    onClick = {
                        if (amzBusy) return@PrimaryButton
                        amzBusy = true
                        lastAmzResult = null
                        scope.launch {
                            val res = runCatching { repo.importAmazonUrl(amazonUrl) }
                                .getOrElse { ImportFeedback(false, 0, 0, 1, "Error: ${it.message}") }
                            lastAmzResult = res
                            if (res.ok) amazonUrl = ""
                            amzBusy = false
                        }
                    },
                    enabled = !amzBusy && amazonUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                lastAmzResult?.let { res ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        res.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (res.ok) NeonGreen else TextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Card 3: Reddit deals feed ───────────────────────────────────
        GlassCard(accent = NeonBlue) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Reddit deals feed",
                            style = MaterialTheme.typography.titleMedium
                                .copy(fontWeight = FontWeight.SemiBold),
                            color = NeonBlue,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Auto-fetch hot posts from your subs every hour and import any " +
                                "with Amazon links.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                        )
                    }
                    Switch(
                        checked = redditEnabled,
                        onCheckedChange = { checked ->
                            redditEnabled = checked
                            scope.launch { Prefs.setRedditFeedEnabled(context, checked) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonBlue,
                            checkedTrackColor = NeonBlue.copy(alpha = 0.4f),
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = redditSubs,
                    onValueChange = { redditSubs = it },
                    label = { Text("Subreddits (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = inputColors(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Default: ${Prefs.DEFAULT_REDDIT_SUBS}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = "Save subs",
                        onClick = {
                            scope.launch {
                                Prefs.setRedditSubs(
                                    context,
                                    redditSubs.ifBlank { Prefs.DEFAULT_REDDIT_SUBS },
                                )
                            }
                        },
                        enabled = redditEnabled,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = if (redditBusy) "Fetching…" else "Fetch now",
                        onClick = {
                            if (redditBusy) return@PrimaryButton
                            redditBusy = true
                            lastRedditResult = null
                            scope.launch {
                                val res = runCatching { repo.runRedditFeedScan() }
                                    .getOrElse { ImportFeedback(false, 0, 0, 1, "Error: ${it.message}") }
                                lastRedditResult = res
                                redditBusy = false
                            }
                        },
                        enabled = redditEnabled && !redditBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
                lastRedditResult?.let { res ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        res.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (res.ok) NeonGreen else TextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(accent = TextMuted) {
            Column {
                Text(
                    "Why no Facebook page monitoring?",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextMuted,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Facebook actively blocks both OkHttp and our headless browser when " +
                        "trying to scan pages, so periodic page scanning has been disabled. " +
                        "Use the import flows above instead — they read one specific URL at a " +
                        "time, the same way a logged-out browser would.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun inputColors() = TextFieldDefaults.colors(
    focusedTextColor = Color(0xFFE6EDF3),
    unfocusedTextColor = Color(0xFFE6EDF3),
    focusedContainerColor = Color(0x33001A22),
    unfocusedContainerColor = Color(0x22001A22),
    focusedIndicatorColor = NeonBlue,
    unfocusedIndicatorColor = TextMuted,
    cursorColor = NeonBlue,
    focusedLabelColor = NeonBlue,
    unfocusedLabelColor = TextMuted,
)
