package com.affiliatemonitor.app.data.amazon

import java.security.MessageDigest

private const val FTC_LINE = "As an Amazon Associate, I earn from qualifying purchases."

/**
 * Build the spec-exact final caption.
 *
 *   {original_description}
 *
 *   #ad
 *   As an Amazon Associate, I earn from qualifying purchases.
 *
 *   Buy here: {affiliate_link}
 */
fun buildCaption(originalDescription: String?, affiliateLink: String?): String {
    val desc = (originalDescription ?: "").trim()
    val link = (affiliateLink ?: "").trim()
    val parts = mutableListOf<String>()
    if (desc.isNotEmpty()) parts += desc
    parts += ""
    parts += "#ad"
    parts += FTC_LINE
    parts += ""
    parts += "Buy here: $link"
    return parts.joinToString("\n").trim() + "\n"
}

fun captionHash(caption: String?): String {
    val normalized = (caption ?: "").trim().lowercase()
        .replace(Regex("\\s+"), " ")
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(normalized.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}
