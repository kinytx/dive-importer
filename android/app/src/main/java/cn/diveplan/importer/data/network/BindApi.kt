package cn.diveplan.importer.data.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 账号绑定接口（POST /api/ble-probe/bind-codes/consume）。
 *
 * 服务端 `BleProbeBindController.Consume`：
 *   - 接受 6 位数字 Code（10 分钟 TTL）
 *   - 接受 deviceLabel / probeVersion（可选）
 *   - 成功返回 ApiKey + Prefix + ExpiresAt（**ApiKey 只返回这一次，必须立刻持久化**）
 *
 * 走裸 OkHttp（不用 Retrofit） —— 只有 1 个 endpoint，引 Retrofit 不值
 */
interface BindApi {
    /** 用 6 位码换 ApiKey。失败抛 [BindException]。*/
    suspend fun consumeBindCode(
        code: String,
        deviceLabel: String? = null,
        probeVersion: String? = null,
    ): BindResp
}

@JsonClass(generateAdapter = true)
data class BindReq(
    val code: String,
    val deviceLabel: String? = null,
    val probeVersion: String? = null,
)

@JsonClass(generateAdapter = true)
data class BindResp(
    val bound: Boolean,
    val apiKey: String,
    val prefix: String,
    val expiresAt: String,
)

@JsonClass(generateAdapter = true)
internal data class BindErrorResp(
    val error: String? = null,
)

sealed class BindException(message: String) : Exception(message) {
    /** 6 位码无效 / 过期 / 已用 — 用户级错误 */
    object InvalidOrExpired : BindException("绑定码无效或已过期")
    /** 网络问题 — 可重试 */
    class Network(cause: Throwable) : BindException("网络异常: ${cause.message ?: "未知"}")
    /** 后端 5xx / 其它 */
    class Server(val status: Int, msg: String) : BindException("服务器异常 ($status): $msg")
}

internal class BindApiImpl(
    private val client: OkHttpClient,
    private val moshi: Moshi,
) : BindApi {

    private val reqAdapter = moshi.adapter(BindReq::class.java)
    private val respAdapter = moshi.adapter(BindResp::class.java)
    private val errAdapter = moshi.adapter(BindErrorResp::class.java)
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun consumeBindCode(
        code: String,
        deviceLabel: String?,
        probeVersion: String?,
    ): BindResp {
        // 6 位数字白名单
        val normalized = code.filter { it.isDigit() }.take(6)
        if (normalized.length != 6) throw BindException.InvalidOrExpired

        val body = reqAdapter.toJson(BindReq(normalized, deviceLabel, probeVersion))
            .toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url(DivePlanEndpoints.BASE_URL + DivePlanEndpoints.CONSUME_BIND_CODE)
            .post(body)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw BindException.Network(e)
        }

        response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                // 后端 invalid_or_expired_ble_probe_bind_code 返回 400
                val err = runCatching { errAdapter.fromJson(text) }.getOrNull()
                if (err?.error?.contains("invalid_or_expired") == true) {
                    throw BindException.InvalidOrExpired
                }
                throw BindException.Server(resp.code, err?.error ?: text.take(200))
            }
            return respAdapter.fromJson(text) ?: throw BindException.Server(
                resp.code, "empty response body",
            )
        }
    }
}
