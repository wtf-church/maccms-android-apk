package com.maccms.mobile.data

import kotlinx.serialization.Serializable

/** maccms API 顶层响应 */
@Serializable
data class ApiResponse(
    val code: Int = 0,
    val msg: String = "",
    val page: Int = 1,
    val pagecount: Int = 1,
    val limit: String = "",
    val total: Int = 0,
    val list: List<Vod> = emptyList()
)

/** 影片条目（只取需要的字段；maccms 会返回更多字段） */
@Serializable
data class Vod(
    val vod_id: Int = 0,
    val type_id: Int = 0,
    val type_name: String? = null,
    val vod_name: String = "",
    val vod_en: String = "",
    val vod_remarks: String = "",
    val vod_actor: String = "",
    val vod_director: String = "",
    val vod_area: String = "",
    val vod_lang: String = "",
    val vod_year: String = "",
    val vod_score: String = "",
    val vod_hits: Int = 0,
    val vod_pic: String = "",
    val vod_pic_slide: String = "",
    val vod_content: String = "",
    val vod_time: String = "",
    val vod_play_from: String = "",
    val vod_play_url: String = ""
) {
    val displayScore: String
        get() = vod_score.takeIf { it.isNotBlank() && it != "0" && it != "0.0" } ?: ""

    val metaLine: String
        get() = listOf(vod_year, vod_area, vod_lang)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
}

/** 解析后的分集：集名 + 地址 */
data class Episode(val name: String, val url: String)

/** 播放源：源名（如 demo）+ 该源的所有分集 */
data class PlaySource(val from: String, val episodes: List<Episode>) {
    val firstEpisode: Episode? get() = episodes.firstOrNull()
}

object VodParser {

    private val videoExtRegex = Regex("(?i)\\.(m3u8?|mp4|flv|mkv|avi|mov|webm|rmvb|wmv|ts)([?#]|$)")

    /** 是否为可直接交给播放器的媒体 URL（m3u8/mp4 等）；false 表示 HTML 分享页等 */
    fun isVideoUrl(url: String): Boolean = videoExtRegex.containsMatchIn(url)

    /** 所有候选播放地址：m3u8 排前，其它排后；去重 */
    fun allUrls(playFrom: String, playUrl: String): List<String> {
        val sources = parsePlaySources(playFrom, playUrl)
        if (sources.isEmpty()) return emptyList()
        val m3u8 = mutableListOf<String>()
        val other = mutableListOf<String>()
        for (s in sources) {
            for (ep in s.episodes) {
                if (ep.url.contains(".m3u8") || ep.url.contains(".m3u")) m3u8 += ep.url
                else other += ep.url
            }
        }
        return (m3u8 + other).distinct()
    }

    /** 按分集名收集所有源中的同名分集地址（m3u8 优先） */
    fun urlsByName(playFrom: String, playUrl: String, name: String): List<String> {
        val sources = parsePlaySources(playFrom, playUrl)
        val m3u8 = mutableListOf<String>()
        val other = mutableListOf<String>()
        for (s in sources) {
            for (ep in s.episodes) {
                if (ep.name == name) {
                    if (ep.url.contains(".m3u8") || ep.url.contains(".m3u")) m3u8 += ep.url
                    else other += ep.url
                }
            }
        }
        return (m3u8 + other).distinct()
    }

    /**
     * 从 HTML 分享页提取真实 m3u8 地址。
     * 支持 liangzi / 非凡等分享页里 `var main = '...'`、`const url = '...'` 的写法。
     */
    fun m3u8FromHtml(html: String, pageUrl: String): String? {
        val raw = runCatching {
            val declared = Regex("(?is)(?:var\\s+main|const\\s+url)\\s*=\\s*[\"']([^\"']+)[\"']").find(html)
                ?.groupValues?.get(1)?.trim()
            if (declared != null) {
                declared
            } else {
                Regex("(?is)[\"']([^\"']+\\.m3u8[^\"']*)[\"']").find(html)
                    ?.groupValues?.get(1)?.trim()
            }
        }.getOrNull() ?: return null

        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        val host = runCatching {
            val u = java.net.URI(pageUrl)
            "${u.scheme}://${u.host}"
        }.getOrNull() ?: return null
        return if (raw.startsWith("/")) host + raw else host + "/" + raw
    }

    /**
     * 解析 vod_play_url。
     * 格式：源组之间用 $$$ 分隔；每组内分集用 # 分隔；每集为 "集名$URL"。
     * vod_play_from 用逗号或 $$$ 分隔的源名与之对应。
     */
    fun parsePlaySources(vodPlayFrom: String, vodPlayUrl: String): List<PlaySource> {
        if (vodPlayUrl.isBlank()) return emptyList()
        val fromNames = vodPlayFrom.split(",", "$$$").map { it.trim() }.filter { it.isNotBlank() }
        val groups = vodPlayUrl.split("$$$")

        return groups.mapIndexedNotNull { index, group ->
            val episodes = group.split("#").mapNotNull { seg -> parseEpisode(seg) }
            if (episodes.isEmpty()) null
            else PlaySource(
                from = fromNames.getOrNull(index) ?: "线路${index + 1}",
                episodes = episodes
            )
        }
    }

    private fun parseEpisode(seg: String): Episode? {
        val s = seg.trim()
        if (s.isEmpty()) return null
        val dollar = s.lastIndexOf('$')
        val at = s.lastIndexOf('@')
        val sep = if (dollar > 0 && dollar < s.length - 1) dollar
        else if (at > 0 && at < s.length - 1) at
        else -1
        if (sep > 0) {
            return Episode(s.substring(0, sep).trim(), s.substring(sep + 1).trim())
        }
        return if (s.startsWith("http")) Episode(s, s) else null
    }
}
