package com.affiliatemonitor.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.affiliatemonitor.app.data.PostOut
import com.affiliatemonitor.app.data.Repository
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
fun QueueScreen(onOpen: (Int) -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()

    var posts by remember { mutableStateOf<List<PostOut>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        try { posts = repo.queue() }
        catch (t: Throwable) { error = t.message }
        finally { loading = false }
    }

    LaunchedEffect(Unit) { refresh() }

    ScreenScaffold(title = "Queue", subtitle = "Ready-made posts") {
        when {
            loading && posts.isEmpty() -> LoadingIndicator()
            error != null && posts.isEmpty() -> ErrorBanner(error!!) { scope.launch { refresh() } }
            posts.isEmpty() -> Text("Nothing ready yet. Run a scan from the Dashboard.", color = TextMuted)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(posts, key = { it.id }) { post ->
                    QueueCard(
                        post = post,
                        onOpen = { onOpen(post.id) },
                        onCopy = { copyToClipboard(ctx, post.finalCaption) },
                        onMarkPosted = {
                            scope.launch {
                                try { repo.markPosted(post.id); refresh() }
                                catch (t: Throwable) { error = t.message }
                            }
                        },
                        onReject = {
                            scope.launch {
                                try { repo.rejectPost(post.id); refresh() }
                                catch (t: Throwable) { error = t.message }
                            }
                        },
                        onOpenSource = { openUrl(ctx, post.sourcePostUrl) },
                        onOpenAmazon = { openUrl(ctx, post.affiliateUrl) },
                    )
                }
            }
        }
    }
}

@Composable
fun QueueCard(
    post: PostOut,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onMarkPosted: () -> Unit,
    onReject: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenAmazon: () -> Unit,
) {
    GlassCard(accent = NeonBlue, modifier = Modifier.clickable { onOpen() }) {
        Column {
            if (post.imageUrl != null) {
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.6f)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                post.sourcePageName ?: "Unknown page",
                color = NeonGreen,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                post.originalDescription.take(200) + if (post.originalDescription.length > 200) "…" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "ASIN: ${post.asin}  •  ${post.marketplace}",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Copy", onCopy)
                SecondaryButton("Mark posted", onMarkPosted)
                DangerButton("Reject", onReject)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Open source", onOpenSource)
                SecondaryButton("Amazon", onOpenAmazon)
            }
        }
    }
}

fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("caption", text))
    Toast.makeText(ctx, "Caption copied", Toast.LENGTH_SHORT).show()
}

fun openUrl(ctx: Context, url: String) {
    try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    } catch (t: Throwable) {
        Toast.makeText(ctx, "Cannot open: $url", Toast.LENGTH_SHORT).show()
    }
}
