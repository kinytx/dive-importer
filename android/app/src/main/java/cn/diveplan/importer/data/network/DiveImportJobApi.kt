package cn.diveplan.importer.data.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * P6 历史页：查询服务端 dive-import-jobs 列表。
 *
 * GET /api/me/dive-import-jobs?source=<source>
 * X-Api-Key 由 ApiKeyAuthInterceptor 自动注入。
 */
interface DiveImportJobApi {
    /**
     * 返回最近 50 条 import job（服务端分页限制）。
     * [source]  可选过滤：classic_bt_dump / garmin_fit
     */
    suspend fun listJobs(source: String? = null): List<DiveImportJobSummary>
}

@JsonClass(generateAdapter = true)
data class DiveImportJobSummary(
    val id: String             = "",
    val source: String         = "",
    val status: String         = "",    // pending / running / completed / failed
    val stage: Boolean         = true,
    val sourceFileName: String?= null,
    val rawSizeBytes: Int      = 0,
    val totalCount: Int        = 0,
    val parsedCount: Int       = 0,
    val createdCount: Int      = 0,
    val stagedCount: Int       = 0,
    val duplicateCount: Int    = 0,
    val failedCount: Int       = 0,
    val errorMessage: String?  = null,
    val createdAt: String      = "",
    val completedAt: String?   = null,
    val updatedAt: String      = "",
)

@JsonClass(generateAdapter = true)
internal data class JobListResp(
    val items: List<DiveImportJobSummary>? = null,
)

internal class DiveImportJobApiImpl(
    private val client: OkHttpClient,
    private val moshi: Moshi,
) : DiveImportJobApi {

    private val listAdapter = moshi.adapter(JobListResp::class.java)
    private val itemAdapter = moshi.adapter(DiveImportJobSummary::class.java)

    override suspend fun listJobs(source: String?): List<DiveImportJobSummary> {
        val url = buildString {
            append(DivePlanEndpoints.BASE_URL)
            append("/api/me/dive-import-jobs")
            if (source != null) append("?source=$source")
        }
        val req = Request.Builder().url(url).get().build()
        val resp = try {
            client.newCall(req).execute()
        } catch (e: Exception) {
            return emptyList()
        }
        return resp.use { r ->
            if (!r.isSuccessful) return emptyList()
            val body = r.body?.string() ?: return emptyList()
            // 服务端可能返回 { items: [...] } 或直接返回 [...]
            runCatching {
                // 先尝试对象格式
                listAdapter.fromJson(body)?.items?.filterNotNull() ?: emptyList()
            }.getOrElse {
                runCatching {
                    // 再尝试数组格式
                    val type = moshi.adapter<List<DiveImportJobSummary>>(
                        com.squareup.moshi.Types.newParameterizedType(
                            List::class.java, DiveImportJobSummary::class.java)
                    )
                    type.fromJson(body) ?: emptyList()
                }.getOrDefault(emptyList())
            }
        }
    }
}
