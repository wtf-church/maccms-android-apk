package com.maccms.mobile.data

/**
 * 默认站点（maccms.sanxininfo.cn）的分类。
 * 首页只展示精选分类，浏览页展示全部；分类名称同时用于列表兜底。
 */
object Categories {
    val ALL: List<Pair<Int, String>> = listOf(
        5 to "短剧", 6 to "纪录片",
        20 to "国产剧", 21 to "韩剧", 22 to "日漫", 23 to "美剧",
        24 to "华语电影", 25 to "欧美电影", 26 to "动作片", 27 to "爱情片", 28 to "科幻片",
        29 to "恐怖片", 30 to "剧情片", 31 to "伦理片", 32 to "香港剧", 33 to "台湾剧",
        34 to "日剧", 35 to "海外剧", 36 to "泰剧", 37 to "国漫", 38 to "欧美动漫",
        39 to "动画片", 40 to "大陆综艺", 41 to "港台综艺", 42 to "日韩综艺",
        43 to "欧美综艺", 44 to "电影解说", 45 to "预告片"
    )

    /** 首页精选分类：控制在 10 行以内，避免首屏发起过多请求 */
    val HOME_IDS = listOf(5, 20, 21, 23, 24, 26, 22, 40, 28, 6)

    fun nameOf(id: Int): String = ALL.firstOrNull { it.first == id }?.second ?: "分类 $id"

    fun idOf(name: String): Int? = ALL.firstOrNull { it.second == name }?.first
}
