package com.affiliatemonitor.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "apm_prefs")

/** Caption rendering styles users can pick in Settings. */
enum class CaptionStyle(val key: String, val label: String) {
    ORIGINAL("original", "Original caption"),
    CLEAN_DEAL("clean_deal", "Clean deal caption"),
    SHORT_VIRAL("short_viral", "Short viral caption");

    companion object {
        fun fromKey(s: String?): CaptionStyle = entries.firstOrNull { it.key == s } ?: CLEAN_DEAL
    }
}

/**
 * App-local settings. Everything runs on-device — there is no backend URL.
 */
object Prefs {
    private val ONBOARDED = booleanPreferencesKey("onboarded")
    private val AMAZON_TAG = stringPreferencesKey("amazon_tag")
    private val SCAN_INTERVAL_MIN = intPreferencesKey("scan_interval_min")
    private val DAILY_LIMIT = intPreferencesKey("daily_limit")
    private val DELAY_BETWEEN_SCANS = intPreferencesKey("delay_between_scans_sec")
    private val USER_AGENT = stringPreferencesKey("user_agent")
    private val CAPTION_STYLE = stringPreferencesKey("caption_style")
    private val REDDIT_FEED_ENABLED = booleanPreferencesKey("reddit_feed_enabled")
    private val REDDIT_SUBS = stringPreferencesKey("reddit_subs")

    const val DEFAULT_AMAZON_TAG = "laique248-20"
    const val DEFAULT_SCAN_INTERVAL_MIN = 60
    const val DEFAULT_DAILY_LIMIT = 100
    const val DEFAULT_DELAY_SEC = 5
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
    val DEFAULT_CAPTION_STYLE = CaptionStyle.CLEAN_DEAL
    const val DEFAULT_REDDIT_SUBS = "deals,amazondeals,FrugalMaleFashion,buildapcsales"

    fun isOnboarded(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[ONBOARDED] ?: false }

    suspend fun setOnboarded(context: Context, value: Boolean) {
        context.dataStore.edit { it[ONBOARDED] = value }
    }

    fun amazonTag(context: Context): Flow<String> =
        context.dataStore.data.map { it[AMAZON_TAG] ?: DEFAULT_AMAZON_TAG }

    suspend fun amazonTagValue(context: Context): String =
        context.dataStore.data.map { it[AMAZON_TAG] ?: DEFAULT_AMAZON_TAG }.first()

    suspend fun setAmazonTag(context: Context, value: String) {
        context.dataStore.edit { it[AMAZON_TAG] = value }
    }

    fun scanIntervalMin(context: Context): Flow<Int> =
        context.dataStore.data.map { it[SCAN_INTERVAL_MIN] ?: DEFAULT_SCAN_INTERVAL_MIN }

    suspend fun scanIntervalMinValue(context: Context): Int =
        context.dataStore.data.map { it[SCAN_INTERVAL_MIN] ?: DEFAULT_SCAN_INTERVAL_MIN }.first()

    suspend fun setScanIntervalMin(context: Context, value: Int) {
        context.dataStore.edit { it[SCAN_INTERVAL_MIN] = value.coerceAtLeast(15) }
    }

    fun dailyLimit(context: Context): Flow<Int> =
        context.dataStore.data.map { it[DAILY_LIMIT] ?: DEFAULT_DAILY_LIMIT }

    suspend fun dailyLimitValue(context: Context): Int =
        context.dataStore.data.map { it[DAILY_LIMIT] ?: DEFAULT_DAILY_LIMIT }.first()

    suspend fun setDailyLimit(context: Context, value: Int) {
        context.dataStore.edit { it[DAILY_LIMIT] = value.coerceAtLeast(1) }
    }

    fun delayBetweenScansSec(context: Context): Flow<Int> =
        context.dataStore.data.map { it[DELAY_BETWEEN_SCANS] ?: DEFAULT_DELAY_SEC }

    suspend fun delayBetweenScansSecValue(context: Context): Int =
        context.dataStore.data.map { it[DELAY_BETWEEN_SCANS] ?: DEFAULT_DELAY_SEC }.first()

    suspend fun setDelayBetweenScansSec(context: Context, value: Int) {
        context.dataStore.edit { it[DELAY_BETWEEN_SCANS] = value.coerceAtLeast(0) }
    }

    suspend fun userAgentValue(context: Context): String =
        context.dataStore.data.map { it[USER_AGENT] ?: DEFAULT_USER_AGENT }.first()

    fun captionStyle(context: Context): Flow<CaptionStyle> =
        context.dataStore.data.map { CaptionStyle.fromKey(it[CAPTION_STYLE]) }

    suspend fun captionStyleValue(context: Context): CaptionStyle =
        context.dataStore.data.map { CaptionStyle.fromKey(it[CAPTION_STYLE]) }.first()

    suspend fun setCaptionStyle(context: Context, value: CaptionStyle) {
        context.dataStore.edit { it[CAPTION_STYLE] = value.key }
    }

    fun redditFeedEnabled(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[REDDIT_FEED_ENABLED] ?: false }

    suspend fun redditFeedEnabledValue(context: Context): Boolean =
        context.dataStore.data.map { it[REDDIT_FEED_ENABLED] ?: false }.first()

    suspend fun setRedditFeedEnabled(context: Context, value: Boolean) {
        context.dataStore.edit { it[REDDIT_FEED_ENABLED] = value }
    }

    fun redditSubs(context: Context): Flow<String> =
        context.dataStore.data.map { it[REDDIT_SUBS] ?: DEFAULT_REDDIT_SUBS }

    suspend fun redditSubsValue(context: Context): String =
        context.dataStore.data.map { it[REDDIT_SUBS] ?: DEFAULT_REDDIT_SUBS }.first()

    suspend fun setRedditSubs(context: Context, value: String) {
        context.dataStore.edit { it[REDDIT_SUBS] = value }
    }
}
