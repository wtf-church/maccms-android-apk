package com.maccms.mobile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 数据仓库：封装 API 调用与解析，UI 层只依赖它。 */
class VodRepository(context: Context) {

    private val client = ApiClient(context.applicationContext)

    /** 首页：按更新时间倒序 */
    suspend fun getHomeList(): Result<List<Vod>> = withContext(Dispatchers.IO) {
        runCatching { client.decodeList(client.api.getVodList(by = "time")).list }
    }

    /** 按分类拉取第一页（首页分类行用） */
    suspend fun getListByType(typeId: Int, page: Int = 1): Result<List<Vod>> =
        getListByTypePage(typeId, page).map { it.list }

    /** 按分类分页，返回原始响应（含 pagecount） */
    suspend fun getListByTypePage(typeId: Int, page: Int, pageSize: Int = 30): Result<ApiResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                client.decodeList(
                    client.api.getVodList(
                        t = typeId.toString(), pg = page, by = "time", pagesize = pageSize
                    )
                )
            }
        }

    /** 搜索分页 */
    suspend fun searchPage(keyword: String, page: Int, pageSize: Int = 30): Result<ApiResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                client.decodeList(
                    client.api.getVodList(wd = keyword, pg = page, pagesize = pageSize)
                )
            }
        }

    suspend fun search(keyword: String): Result<List<Vod>> =
        searchPage(keyword, 1).map { it.list }

    suspend fun getDetail(vodId: Int): Result<Vod> = withContext(Dispatchers.IO) {
        runCatching {
            client.decodeDetail(client.api.getVodDetail(ids = vodId))
                ?: throw IllegalStateException("影片详情为空")
        }
    }
}

/** 跨页面传影片快照，避免每次从列表进入详情都重新请求（详情/播放页仍会拉最新数据） */
object VodCache {
    private val map = mutableMapOf<Int, Vod>()

    fun put(vod: Vod) { map[vod.vod_id] = vod }

    fun get(id: Int): Vod? = map[id]
}
