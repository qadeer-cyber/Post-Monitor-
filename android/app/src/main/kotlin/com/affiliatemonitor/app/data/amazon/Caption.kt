package com.affiliatemonitor.app.data.amazon

import com.affiliatemonitor.app.data.CaptionStyle
import java.security.MessageDigest

private const val FTC_LINE = "As an Amazon Associate, I earn from qualifying purchases."

/** Lines we always strip before picking out a product title. */
private val NOISE_LINE_HINTS = listOf(
    "as an amazon associate",
    "earn from qualifying purchases",
    "price and availability are valid",
    "deal of the day",
    "limited time deal",
    "no spam",
    "follow for more",
    "follow us",
    "join our group",
    "click here",
    "tap here",
    "link in bio",
    "share if you like",
    "use code:",
    "promo code:",
)

/** Regex for things we don't want to keep verbatim in the caption. */
private val URL_RE = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

/** Detect a price string anywhere in the source text. Examples: "$19.99", "Deal price: $14.50". */
private val PRICE_RE = Regex(
    "(?:price[^:]*:?\\s*)?" +
        "(?:\\$|usd|€|£|gbp|cad|aud|inr|aed|₹)\\s?" +
        "[0-9]+(?:[.,][0-9]{2})?",
    RegexOption.IGNORE_CASE,
)

/** Heuristic: a "shouty deal" phrase like "-50% OFF", "Save 30%". */
private val DEAL_RE = Regex(
    "(?:-?\\s?[0-9]{1,2}%\\s?off|save\\s?[0-9]{1,2}%|[0-9]{1,2}%\\s?discount)",
    RegexOption.IGNORE_CASE,
)

/**
 * Detect a coupon / promo code in source text.
 *
 *  - "Code: ABCD12"
 *  - "Use code ABCD12"
 *  - "Coupon: ABCD12"
 *  - "Promo code ABCD12"
 *
 * The captured token is 4–12 alphanumeric chars, upper-cased so we can match
 * "abcd12" and "ABCD12" the same way.
 */
private val COUPON_RE = Regex(
    "(?i)\\b(?:use\\s+code|promo\\s*code|coupon(?:\\s*code)?|code)\\s*[:#-]?\\s*([A-Z0-9]{4,12})\\b",
    RegexOption.IGNORE_CASE,
)

/** Strip a "Code: XXXX" / "Use code XXXX" line entirely (used by clean_deal). */
private val COUPON_LINE_RE = Regex(
    "(?im)^.*\\b(?:use\\s+code|promo\\s*code|coupon(?:\\s*code)?|code)\\s*[:#-]?\\s*[A-Z0-9]{4,12}\\b.*$",
    RegexOption.IGNORE_CASE,
)

/** Lines whose only content is a URL/emoji/decoration we want to drop when cleaning. */
private fun isLineNoise(rawLine: String): Boolean {
    val line = rawLine.trim()
    if (line.isEmpty()) return true
    val lower = line.lowercase()
    if (NOISE_LINE_HINTS.any { it in lower }) return true
    val withoutUrls = URL_RE.replace(line, "").trim()
    if (withoutUrls.isEmpty()) return true
    // pure decoration: only emoji + punctuation, no letters/digits.
    if (withoutUrls.none { it.isLetterOrDigit() }) return true
    return false
}

/** Result of mining the source description for product metadata. */
data class CaptionMined(
    val productTitle: String?,
    val priceLine: String?,
    val couponCode: String?,
    val cleanedDescription: String,
)

/** Pull the first coupon code out of [text], upper-cased. Returns null if none. */
fun detectCouponCode(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val m = COUPON_RE.find(text) ?: return null
    return m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }?.uppercase()
}

/**
 * Pull a probable product title and price line out of [originalDescription], and
 * return a cleaned version with boilerplate / disclosure / dangling URLs stripped.
 *
 * If [productTitleOverride] is provided (e.g. from an Amazon OG title), it wins.
 */
fun mineCaption(originalDescription: String?, productTitleOverride: String? = null): CaptionMined {
    val text = (originalDescription ?: "").replace("\r\n", "\n").trim()
    if (text.isEmpty() && productTitleOverride.isNullOrBlank()) {
        return CaptionMined(null, null, null, "")
    }

    val priceLine: String? = run {
        val priceMatch = PRICE_RE.find(text)?.value?.trim()
        val dealMatch = DEAL_RE.find(text)?.value?.trim()
        when {
            !priceMatch.isNullOrBlank() && !dealMatch.isNullOrBlank() ->
                "$dealMatch • $priceMatch"
            !priceMatch.isNullOrBlank() -> priceMatch
            !dealMatch.isNullOrBlank() -> dealMatch
            else -> null
        }
    }

    val couponCode = detectCouponCode(text)

    // Strip the entire "use code XYZ" line so it doesn't appear above the formatted "🏷️ Code:" line.
    val deCouponed = if (couponCode != null) COUPON_LINE_RE.replace(text, "") else text

    val keptLines = deCouponed.split("\n")
        .map { it.trim() }
        .filter { !isLineNoise(it) }
        .map { URL_RE.replace(it, "").trim() }
        .filter { it.isNotEmpty() }

    val productTitle: String? = productTitleOverride?.takeIf { it.isNotBlank() }
        ?: pickProductTitleLine(keptLines)

    val cleaned = keptLines.joinToString("\n")
    return CaptionMined(
        productTitle = productTitle,
        priceLine = priceLine,
        couponCode = couponCode,
        cleanedDescription = cleaned,
    )
}

private fun pickProductTitleLine(lines: List<String>): String? {
    if (lines.isEmpty()) return null
    // Score each line: prefer lines with letters AND digits, length 10-100,
    // not starting with "Price"/ "Deal price" / "Use code".
    return lines
        .filter { line ->
            val lower = line.lowercase()
            !lower.startsWith("price") &&
                !lower.startsWith("deal price") &&
                !lower.startsWith("use code") &&
                !lower.startsWith("code:") &&
                line.length in 6..140
        }
        .maxByOrNull { line ->
            var score = 0
            if (line.any { it.isLetter() }) score += 2
            if (line.any { it.isDigit() }) score += 1
            if (line.length in 12..100) score += 2
            if (line.first().isUpperCase()) score += 1
            // bias against lines that are mostly emojis
            if (line.count { it.isLetterOrDigit() } * 2 < line.length) score -= 2
            score
        }
        ?: lines.firstOrNull()
}

/**
 * Build the final caption in the requested [style].
 *
 * Always includes #ad + the FTC disclosure + the affiliate link, regardless of style.
 *
 * - [CaptionStyle.ORIGINAL]: original (full) description block followed by disclosure.
 * - [CaptionStyle.CLEAN_DEAL] (default): "🔥 {title}\n\nPrice/Deal: {price}\n\n#ad…"
 * - [CaptionStyle.SHORT_VIRAL]: single-line hook + product + #ad + link.
 */
fun buildCaption(
    originalDescription: String?,
    affiliateLink: String?,
    style: CaptionStyle = CaptionStyle.CLEAN_DEAL,
    productTitleOverride: String? = null,
): String {
    val link = (affiliateLink ?: "").trim()
    val mined = mineCaption(originalDescription, productTitleOverride)
    return when (style) {
        CaptionStyle.ORIGINAL -> renderOriginal(originalDescription, link)
        CaptionStyle.CLEAN_DEAL -> renderCleanDeal(mined, link)
        CaptionStyle.SHORT_VIRAL -> renderShortViral(mined, link)
    }
}

private fun renderOriginal(originalDescription: String?, link: String): String {
    val desc = (originalDescription ?: "").trim()
    val parts = mutableListOf<String>()
    if (desc.isNotEmpty()) parts += desc
    parts += ""
    parts += "#ad"
    parts += FTC_LINE
    parts += ""
    parts += "Buy here: $link"
    return parts.joinToString("\n").trim() + "\n"
}

private fun renderCleanDeal(mined: CaptionMined, link: String): String {
    val title = mined.productTitle?.trim().orEmpty()
    val priceLine = mined.priceLine?.trim()
    val coupon = mined.couponCode?.trim()
    val sb = StringBuilder()
    if (title.isNotEmpty()) sb.append("🔥 ").append(title).append("\n\n")
    if (!priceLine.isNullOrBlank()) sb.append("💰 Price: ").append(priceLine).append("\n")
    if (!coupon.isNullOrBlank()) sb.append("🏷️ Code: ").append(coupon).append("\n")
    if (!priceLine.isNullOrBlank() || !coupon.isNullOrBlank()) sb.append("\n")
    sb.append("#ad\n")
    sb.append(FTC_LINE).append("\n\n")
    sb.append("Buy here: ").append(link).append("\n")
    return sb.toString().trim() + "\n"
}

private fun renderShortViral(mined: CaptionMined, link: String): String {
    val title = mined.productTitle?.trim().orEmpty().take(80)
    val price = mined.priceLine?.trim().orEmpty()
    val coupon = mined.couponCode?.trim().orEmpty()
    val hookBase = when {
        price.isNotBlank() && title.isNotBlank() -> "🔥 $title — $price"
        title.isNotBlank() -> "🔥 $title"
        price.isNotBlank() -> "🔥 Hot deal: $price"
        else -> "🔥 Hot Amazon deal"
    }
    val hook = if (coupon.isNotBlank()) "$hookBase 🏷️ $coupon" else hookBase
    return "$hook\n\n#ad $FTC_LINE\n\nBuy here: $link\n"
}

fun captionHash(caption: String?): String {
    val normalized = (caption ?: "").trim().lowercase()
        .replace(Regex("\\s+"), " ")
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(normalized.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
