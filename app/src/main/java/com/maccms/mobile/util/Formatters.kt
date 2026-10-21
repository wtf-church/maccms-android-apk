package com.maccms.mobile.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 毫秒 → mm:ss / h:mm:ss */
fun formatClock(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun relativeTime(ts: Long): String {
    if (ts <= 0) return ""
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(ts))
    }
}

fun shortDate(ts: Long): String =
    if (ts <= 0) "" else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts))
