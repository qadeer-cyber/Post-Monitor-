package com.affiliatemonitor.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.affiliatemonitor.app.data.PostOut
import com.affiliatemonitor.app.data.Repository
import com.affiliatemonitor.app.ui.ErrorBanner
import com.affiliatemonitor.app.ui.LoadingIndicator
import com.affiliatemonitor.app.ui.ScreenScaffold
import com.affiliatemonitor.app.ui.theme.GlassCard
import com.affiliatemonitor.app.ui.theme.NeonGreen
import com.affiliatemonitor.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun PostedScreen(onOpen: (Int) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()

    var posts by remember { mutableStateOf<List<PostOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        try { posts = repo.posted() }
        catch (t: Throwable) { error = t.message }
        finally { loading = false }
    }

    LaunchedEffect(Unit) { refresh() }

    ScreenScaffold(title = "Posted", subtitle = "Archive of what you've published") {
        when {
            loading && posts.isEmpty() -> LoadingIndicator()
            error != null && posts.isEmpty() -> ErrorBanner(error!!) { scope.launch { refresh() } }
            posts.isEmpty() -> Text("No posts marked as posted yet.", color = TextMuted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(posts, key = { it.id }) { p -> PostedRow(p) { onOpen(p.id) } }
            }
        }
    }
}

@Composable
private fun PostedRow(post: PostOut, onOpen: () -> Unit) {
    GlassCard(accent = NeonGreen, modifier = Modifier.clickable { onOpen() }) {
        Column {
            Text(post.sourcePageName ?: "Unknown page", style = MaterialTheme.typography.titleMedium, color = NeonGreen)
            Spacer(Modifier.height(4.dp))
            Text("ASIN ${post.asin}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
            Spacer(Modifier.height(4.dp))
            Text(
                post.finalCaption.take(160) + if (post.finalCaption.length > 160) "…" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Posted: ${post.postedAt?.take(19)?.replace("T", " ") ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}
