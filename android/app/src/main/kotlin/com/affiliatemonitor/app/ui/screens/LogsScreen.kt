package com.affiliatemonitor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.affiliatemonitor.app.data.LogOut
import com.affiliatemonitor.app.data.Repository
import com.affiliatemonitor.app.ui.ErrorBanner
import com.affiliatemonitor.app.ui.LoadingIndicator
import com.affiliatemonitor.app.ui.ScreenScaffold
import com.affiliatemonitor.app.ui.theme.Danger
import com.affiliatemonitor.app.ui.theme.GlassCard
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.NeonGreen
import com.affiliatemonitor.app.ui.theme.TextMuted
import com.affiliatemonitor.app.ui.theme.WarnAmber
import kotlinx.coroutines.launch

private val categories = listOf("all", "scan", "import", "link", "error")

@Composable
fun LogsScreen() {
    val ctx = LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf("all") }
    var logs by remember { mutableStateOf<List<LogOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh(cat: String) {
        loading = true
        error = null
        try {
            logs = repo.logs(category = if (cat == "all") null else cat)
        } catch (t: Throwable) {
            error = t.message
        } finally {
            loading = false
        }
    }

    LaunchedEffect(selected) { refresh(selected) }

    ScreenScaffold(title = "Logs", subtitle = "Scan, import, link conversion, errors") {
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                categories.forEach { c ->
                    FilterChip(
                        selected = selected == c,
                        onClick = { selected = c },
                        label = { Text(c) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonBlue.copy(alpha = 0.25f),
                            selectedLabelColor = NeonBlue,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            when {
                loading && logs.isEmpty() -> LoadingIndicator()
                error != null && logs.isEmpty() -> ErrorBanner(error!!) { scope.launch { refresh(selected) } }
                logs.isEmpty() -> Text("No log entries.", color = TextMuted)
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(logs, key = { it.id }) { LogRow(it) }
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: LogOut) {
    val color = when (log.level) {
        "error" -> Danger
        "warn" -> WarnAmber
        else -> NeonGreen
    }
    GlassCard(accent = color) {
        Column {
            Row {
                Text(
                    "${log.level.uppercase()}  •  ${log.category}",
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    log.createdAt.take(19).replace("T", " "),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(log.message, style = MaterialTheme.typography.bodyMedium)
            if (!log.detail.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(log.detail, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
        }
    }
}


