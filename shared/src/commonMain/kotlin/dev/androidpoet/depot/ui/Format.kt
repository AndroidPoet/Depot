package dev.androidpoet.depot.ui

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

fun relativeTime(then: Long, now: Long): String {
    val elapsed = (now - then).coerceAtLeast(0)
    return when {
        elapsed < MINUTE -> "just now"
        elapsed < HOUR -> "${elapsed / MINUTE}m ago"
        elapsed < DAY -> "${elapsed / HOUR}h ago"
        elapsed < 60 * DAY -> "${elapsed / DAY}d ago"
        elapsed < 730 * DAY -> "${elapsed / (30 * DAY)}mo ago"
        else -> "${elapsed / (365 * DAY)}y ago"
    }
}

fun byteSize(bytes: Long): String = when {
    bytes < 1_000 -> "$bytes B"
    bytes < 1_000_000 -> "${oneDecimal(bytes / 1_000.0)} kB"
    bytes < 1_000_000_000 -> "${oneDecimal(bytes / 1_000_000.0)} MB"
    else -> "${oneDecimal(bytes / 1_000_000_000.0)} GB"
}

fun grouped(number: Int): String =
    number.toString().reversed().chunked(3).joinToString(",").reversed()

fun androidRelease(sdk: Int): String = when (sdk) {
    in Int.MIN_VALUE..1 -> "1.0"
    2 -> "1.1"
    3 -> "1.5"
    4 -> "1.6"
    5, 6 -> "2.0"
    7 -> "2.1"
    8 -> "2.2"
    9, 10 -> "2.3"
    11 -> "3.0"
    12 -> "3.1"
    13 -> "3.2"
    14, 15 -> "4.0"
    16 -> "4.1"
    17 -> "4.2"
    18 -> "4.3"
    19, 20 -> "4.4"
    21 -> "5.0"
    22 -> "5.1"
    23 -> "6"
    24 -> "7.0"
    25 -> "7.1"
    26 -> "8.0"
    27 -> "8.1"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31, 32 -> "12"
    else -> (sdk - 20).toString()
}

fun antiFeatureMeaning(key: String): String = when (key) {
    "Ads" -> "Contains advertising"
    "Tracking" -> "Tracks or reports your activity"
    "NonFreeNet" -> "Relies on a non-free network service"
    "NonFreeAdd" -> "Promotes non-free add-ons"
    "NonFreeDep" -> "Depends on non-free software"
    "NonFreeAssets" -> "Contains non-free assets"
    "KnownVuln" -> "Has a known security vulnerability"
    "NoSourceSince" -> "Source code is no longer available"
    "DisabledAlgorithm" -> "Signed with a weak algorithm"
    "TetheredNet" -> "Tied to one specific network service"
    else -> key
}

private val lineBreakTags = Regex("(?i)<br\\s*/?>|</p>|</li>|</h[1-6]>|</ul>|</ol>")
private val listItemTags = Regex("(?i)<li[^>]*>")
private val anyTag = Regex("<[^>]+>")
private val blankRuns = Regex("\n{3,}")

fun htmlToPlain(html: String): String =
    html.replace(lineBreakTags, "\n")
        .replace(listItemTags, "• ")
        .replace(anyTag, "")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
        .replace(blankRuns, "\n\n")
        .trim()

private fun oneDecimal(value: Double): String {
    val tenths = kotlin.math.round(value * 10).toLong()
    return "${tenths / 10}.${tenths % 10}"
}
