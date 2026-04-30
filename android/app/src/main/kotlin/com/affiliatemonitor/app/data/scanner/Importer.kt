package com.affiliatemonitor.app.data.scanner

import com.affiliatemonitor.app.data.CaptionStyle
import com.affiliatemonitor.app.data.amazon.AmazonLink
import com.affiliatemonitor.app.data.amazon.buildCaption
import com.affiliatemonitor.app.data.amazon.captionHash
import com.affiliatemonitor.app.data.amazon.detectCouponCode
import com.affiliatemonitor.app.data.local.LogEntity
import com.affiliatemonitor.app.data.local.LogDao
import com.affiliatemonitor.app.data.local.PostDao
import com.affiliatemonitor.app.data.local.PostEntity
import com.affiliatemonitor.app.data.local.SourceDao
import okhttp3.OkHttpClient

/**
 * Shared "import a single Amazon-bearing post" pipeline used by:
 *   - Facebook post URL importer
 *   - Direct Amazon URL importer
 *   - Reddit deals feed
 *
 * Always emits structured log rows. Returns an [ImportResult] so the caller can
 * tally found / imported / duplicate / failed counts across batches.
 */
class Importer(
    private val postDao: PostDao,
    private val sourceDao: SourceDao,
    private val logDao: LogDao,
) {
    enum class Outcome { Imported, Duplicate, NoAsin, AlreadySeen }

    data class ImportResult(
        val outcome: Outcome,
        val postId: Int? = null,
        val message: String,
    )

    /**
     * Import a single post into the queue.
     *
     * @param sourceId the (virtual or real) source this post is attributed to.
     * @param sourcePageName e.g. "Reddit r/deals", "Manual Amazon URL", page name from FB
     * @param sourcePostUrl deduplication-relevant URL — for FB the post permalink, for
     *   direct Amazon imports the Amazon URL itself, for Reddit the post permalink.
     * @param description raw post text used as the description / for caption mining.
     * @param imageUrl optional product image URL.
     * @param amazonUrl the raw Amazon URL we will resolve.
     * @param tag affiliate tag.
     * @param style caption style picked in Settings.
     * @param productTitleOverride optional title (e.g. fetched OG:title from amazon.com)
     *   used by clean_deal/short_viral renderers.
     * @param http OkHttpClient used for short-link resolution (amzn.to).
     */
    suspend fun importPost(
        sourceId: Int,
        sourcePageName: String?,
        sourcePostUrl: String,
        description: String,
        imageUrl: String?,
        amazonUrl: String,
        tag: String,
        style: CaptionStyle,
        productTitleOverride: String? = null,
        http: OkHttpClient,
    ): ImportResult {
        val parsed = AmazonLink.parse(amazonUrl, tag, http)
        if (parsed == null) {
            logDao.insert(
                LogEntity(
                    category = "link",
                    level = "warn",
                    message = "Could not extract ASIN from $amazonUrl",
                    sourceId = sourceId,
                ),
            )
            return ImportResult(Outcome.NoAsin, message = "Could not extract ASIN")
        }
        val caption = buildCaption(
            originalDescription = description,
            affiliateLink = parsed.affiliateUrl,
            style = style,
            productTitleOverride = productTitleOverride,
        )
        val coupon = detectCouponCode(description)
        val cHash = captionHash(caption)
        val dup = postDao.findDuplicate(sourcePostUrl, parsed.asin, cHash)
        if (dup != null) {
            logDao.insert(
                LogEntity(
                    category = "import",
                    level = "info",
                    message = "Duplicate skipped (post ${dup.id})",
                    sourceId = sourceId,
                    detail = "asin=${parsed.asin}",
                ),
            )
            return ImportResult(Outcome.Duplicate, postId = dup.id, message = "Duplicate")
        }
        val id = postDao.insert(
            PostEntity(
                sourceId = sourceId,
                sourcePageName = sourcePageName,
                sourcePostUrl = sourcePostUrl,
                originalDescription = description,
                imageUrl = imageUrl,
                postTime = null,
                amazonUrl = parsed.normalizedUrl,
                affiliateUrl = parsed.affiliateUrl,
                asin = parsed.asin,
                marketplace = parsed.marketplace,
                finalCaption = caption,
                captionHash = cHash,
                couponCode = coupon,
            ),
        ).toInt()
        sourceDao.bumpPostsFound(sourceId)
        sourceDao.bumpValidAmazonPosts(sourceId)
        logDao.insert(
            LogEntity(
                category = "amazon_posts_extracted",
                level = "info",
                message = "Imported ASIN ${parsed.asin} (${parsed.marketplace})",
                sourceId = sourceId,
                detail = parsed.affiliateUrl,
            ),
        )
        return ImportResult(Outcome.Imported, postId = id, message = "Imported ASIN ${parsed.asin}")
    }
}
