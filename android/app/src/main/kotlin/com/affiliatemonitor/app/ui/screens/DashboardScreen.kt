package com.affiliatemonitor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.affiliatemonitor.app.data.DashboardOut
import com.affiliatemonitor.app.data.Repository
import com.affiliatemonitor.app.ui.ErrorBanner
import com.affiliatemonitor.app.ui.LoadingIndicator
import com.affiliatemonitor.app.ui.PrimaryButton
import com.affiliatemonitor.app.ui.ScreenScaffold
import com.affiliatemonitor.app.ui.StatTile
import com.affiliatemonitor.app.ui.theme.Danger
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.NeonGreen
import com.affiliatemonitor.app.ui.theme.TextMuted
import com.affiliatemonitor.app.ui.theme.WarnAmber
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen() {
    val ctx = LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()

    var data by remember { mutableStateOf<DashboardOut?>(null) }
    var loading by remember { mutableStateOf(true) }
    var scanning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        try {
            data = repo.dashboard()
        } catch (t: Throwable) {
            error = t.message ?: "Unknown error"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    ScreenScaffold(title = "Dashboard", subtitle = "At-a-glance stats") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when {
                loading && data == null -> LoadingIndicator()
                error != null && data == null -> ErrorBanner(error!!) { scope.launch { refresh() } }
                data != null -> {
                    val d = data!!
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            "Total sources",
                            d.totalSources.toString(),
                            accent = NeonBlue,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Active sources",
                            d.activeSources.toString(),
                            accent = NeonGreen,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            "Posts today",
                            d.newPostsToday.toString(),
                            accent = NeonGreen,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Valid Amazon posts",
                            d.validAmazonPosts.toString(),
                            accent = NeonBlue,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            "Queue size",
                            d.queueSize.toString(),
                            accent = NeonBlue,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Duplicates",
                            d.duplicatesSkipped.toString(),
                            accent = WarnAmber,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(
                            "Failed imports",
                            d.failedImports.toString(),
                            accent = Danger,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            "Last scan",
                            d.lastScanAt?.take(19)?.replace("T", " ") ?: "—",
                            accent = NeonBlue,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(
                        text = if (scanning) "Scanning…" else "Scan Now",
                        onClick = {
                            scope.launch {
                                scanning = true
                                error = null
                                try {
                                    val res = repo.scanAll()
                                    refresh()
                                    if (!res.ok) {
                                        error = "Scan finished with ${res.failed} failure(s). Check Logs."
                                    }
                                } catch (t: Throwable) {
                                    error = t.message ?: "Scan failed"
                                } finally {
                                    scanning = false
                                }
                            }
                        },
                        enabled = !scanning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, style = MaterialTheme.typography.bodySmall, color = Danger)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tip: add sources in the Sources tab, then tap Scan Now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
