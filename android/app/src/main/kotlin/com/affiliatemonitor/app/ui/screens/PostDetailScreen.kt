package com.affiliatemonitor.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.affiliatemonitor.app.data.PostOut
import com.affiliatemonitor.app.data.Repository
import com.affiliatemonitor.app.ui.DangerButton
import com.affiliatemonitor.app.ui.ErrorBanner
import com.affiliatemonitor.app.ui.LoadingIndicator
import com.affiliatemonitor.app.ui.PrimaryButton
import com.affiliatemonitor.app.ui.SecondaryButton
import com.affiliatemonitor.app.ui.theme.GlassCard
import com.affiliatemonitor.app.ui.theme.NeonBlue
import com.affiliatemonitor.app.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun PostDetailScreen(postId: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { Repository(ctx) }
    val scope = rememberCoroutineScope()

    var post by remember { mutableStateOf<PostOut?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        try { post = repo.getPost(postId) }
        catch (t: Throwable) { error = t.message }
        finally { loading = false }
    }

    LaunchedEffect(postId) { refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Text("Post", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(8.dp))

        Column(Modifier.verticalScroll(rememberScrollState())) {
            when {
                loading && post == null -> LoadingIndicator()
                error != null && post == null -> ErrorBanner(error!!) { scope.launch { refresh() } }
                post != null -> Body(
                    post = post!!,
                    onCopy = { copyToClipboard(ctx, post!!.finalCaption) },
                    onSaveImage = {
                        val url = post!!.imageUrl
                        if (url == null) Toast.makeText(ctx, "No image", Toast.LENGTH_SHORT).show()
                        else scope.launch {
                            val ok = withContext(Dispatchers.IO) { saveImageToGallery(ctx, url, post!!.asin) }
                            Toast.makeText(ctx, if (ok) "Image saved" else "Failed to save image", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShare = { shareCaption(ctx, post!!.finalCaption) },
                    onOpenSource = { openUrl(ctx, post!!.sourcePostUrl) },
                    onOpenAmazon = { openUrl(ctx, post!!.affiliateUrl) },
                    onMarkPosted = {
                        scope.launch {
                            try { post = repo.markPosted(post!!.id) }
                            catch (t: Throwable) { error = t.message }
                        }
                    },
                    onReject = {
                        scope.launch {
                            try { post = repo.rejectPost(post!!.id) }
                            catch (t: Throwable) { error = t.message }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Body(
    post: PostOut,
    onCopy: () -> Unit,
    onSaveImage: () -> Unit,
    onShare: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenAmazon: () -> Unit,
    onMarkPosted: () -> Unit,
    onReject: () -> Unit,
) {
    GlassCard(accent = NeonBlue) {
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
                Spacer(Modifier.height(12.dp))
            }
            Text(post.sourcePageName ?: "Unknown page", color = NeonBlue, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(post.finalCaption, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(12.dp))
    GlassCard {
        Column {
            LabelRow("ASIN", post.asin)
            LabelRow("Marketplace", post.marketplace)
            LabelRow("Status", post.status)
            LabelRow("Source URL", post.sourcePostUrl)
            LabelRow("Original Amazon URL", post.amazonUrl)
            LabelRow("Affiliate URL", post.affiliateUrl)
            if (post.postTime != null) LabelRow("Post time", post.postTime)
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Copy Caption", onCopy, modifier = Modifier.weight(1f))
        SecondaryButton("Save Image", onSaveImage, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton("Share", onShare, modifier = Modifier.weight(1f))
        SecondaryButton("Open Source", onOpenSource, modifier = Modifier.weight(1f))
        SecondaryButton("Amazon", onOpenAmazon, modifier = Modifier.weight(1f))
    }
    if (post.status == "queue") {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Mark as Posted", onMarkPosted, modifier = Modifier.weight(1f))
            DangerButton("Reject", onReject, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun LabelRow(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label.uppercase(), color = TextMuted, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

fun shareCaption(ctx: Context, caption: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(Intent.createChooser(intent, "Share").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

fun saveImageToGallery(ctx: Context, url: String, hint: String): Boolean {
    return try {
        val bytes = URL(url).openStream().use { it.readBytes() }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return false
        val filename = "apm_${hint}_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AffiliatePostMonitor")
            }
        }
        val resolver = ctx.contentResolver
        val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        }
        true
    } catch (t: Throwable) {
        false
    }
}

@Suppress("UNUSED_PARAMETER")
private fun legacyPicturesDir(): java.io.File? =
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
