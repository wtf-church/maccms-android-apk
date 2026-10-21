package com.maccms.mobile.data

import android.content.Context
import com.maccms.mobile.util.BaseUrlManager
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** maccms 标准 API（api.php/provide/vod） */
interface MaccmsApi {

    /** 列表：ac=videolist 返回全部字段（含 vod_pic/vod_play_url） */
    @GET("api.php/provide/vod")
    suspend fun getVodList(
        @Query("ac") ac: String = "videolist",
        @Query("pg") pg: Int = 1,
        @Query("t") t: String? = null,
        @Query("wd") wd: String? = null,
        @Query("by") by: String? = null,
        @Query("pagesize") pagesize: Int = 30
    ): String

    /** 详情：ac=detail&ids=vod_id */
    @GET("api.php/provide/vod")
    suspend fun getVodDetail(
        @Query("ac") ac: String = "detail",
        @Query("ids") ids: Int
    ): String
}

class ApiClient(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    @Volatile private var cachedBaseUrl: String? = null
    @Volatile private var cachedApi: MaccmsApi? = null

    /** 设置页修改地址后，下一次调用自动重建 Retrofit */
    val api: MaccmsApi
        get() {
            val base = BaseUrlManager.getBaseUrl(context).ensureSlash()
            val current = cachedApi
            if (current != null && cachedBaseUrl == base) return current
            return synchronized(this) {
                if (cachedApi == null || cachedBaseUrl != base) {
                    cachedApi = Retrofit.Builder()
                        .baseUrl(base)
                        .client(okHttp)
                        .addConverterFactory(ScalarsConverterFactory.create())
                        .build()
                        .create(MaccmsApi::class.java)
                    cachedBaseUrl = base
                }
                cachedApi!!
            }
        }

    fun decodeList(raw: String): ApiResponse =
        json.decodeFromString(ApiResponse.serializer(), raw)

    fun decodeDetail(raw: String): Vod? {
        val resp = json.decodeFromString(ApiResponse.serializer(), raw)
        return resp.list.firstOrNull()
    }

    private fun String.ensureSlash(): String = if (endsWith("/")) this else "$this/"
}
