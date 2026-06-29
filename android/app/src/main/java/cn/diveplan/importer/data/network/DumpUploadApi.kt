package cn.diveplan.importer.data.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * P4 dump 上传接口（POST /api/me/dives/parse）。
 *
 * multipart/form-data 字段：
 *   dump          : 二进制 .bin 文件（原始表数据）
 *   deviceAddress : 蓝牙 MAC（e.g. "00:13:43:9B:28:D4"）
 *   deviceName    : 广播名（e.g. "Petrel"，可为空串）
 *   capturedAt    : ISO-8601 UTC 字符串（客户端抓取时间）
 *
 * 后端 BleProbeCapturesController.ParseDump 用 libdivecomputer 解析 dump，
 * 解析后把日志挂到用户账号下；返回 job / staging 信息。
 *
 * X-Api-Key 由 [ApiKeyAuthInterceptor] 自动注入，本类不处理。
 */
interface DumpUploadApi {
    /**
     * 上传单个经典蓝牙 dump（.bin）→ POST /api/me/dives/parse
     * @return [DumpParseResp]；失败抛 [DumpUploadException]
     */
    suspend fun uploadDump(
        file: File,
        deviceAddress: String,
        deviceName: String,
        capturedAt: String,
    ): DumpParseResp

    /**
     * 上传 Garmin FIT 文件（.fit）→ POST /api/me/dives/fit?async=true&stage=true
     * @return [DumpParseResp]（jobId 字段为 import job ID）；失败抛 [DumpUploadException]
     */
    suspend fun uploadFit(
        file: File,
        deviceAddress: String,
        deviceName: String,
        capturedAt: String,
    ): DumpParseResp
}

@JsonClass(generateAdapter = true)
data class DumpParseResp(
    val jobId: String? = null,
    val status: String? = null,
    val message: String? = null,
)

@JsonClass(generateAdapter = true)
internal data class ErrorResp(val error: String? = null)

sealed class DumpUploadException(message: String) : Exception(message) {
    class Network(cause: Throwable) : DumpUploadException("网络异常: ${cause.message}")
    class Server(val status: Int, msg: String) : DumpUploadException("服务器 $status: $msg")
    class EmptyFile : DumpUploadException("dump 文件为空或不存在")
}

internal class DumpUploadApiImpl(
    private val client: OkHttpClient,
    private val moshi: Moshi,
) : DumpUploadApi {

    private val respAdapter = moshi.adapter(DumpParseResp::class.java)
    private val errAdapter  = moshi.adapter(ErrorResp::class.java)

    override suspend fun uploadDump(
        file: File,
        deviceAddress: String,
        deviceName: String,
        capturedAt: String,
    ): DumpParseResp {
        if (!file.exists() || file.length() == 0L) throw DumpUploadException.EmptyFile()

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "dump",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaTypeOrNull()),
            )
            .addFormDataPart("deviceAddress", deviceAddress)
            .addFormDataPart("deviceName", deviceName)
            .addFormDataPart("capturedAt", capturedAt)
            .build()

        val req = Request.Builder()
            .url(DivePlanEndpoints.BASE_URL + DivePlanEndpoints.DIVES_PARSE)
            .post(multipart)
            .build()

        val resp = try {
            client.newCall(req).execute()
        } catch (e: Exception) {
            throw DumpUploadException.Network(e)
        }

        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val err = runCatching { errAdapter.fromJson(body) }.getOrNull()
                throw DumpUploadException.Server(r.code, err?.error ?: body.take(200))
            }
            return respAdapter.fromJson(body) ?: DumpParseResp()
        }
    }

    override suspend fun uploadFit(
        file: File,
        deviceAddress: String,
        deviceName: String,
        capturedAt: String,
    ): DumpParseResp {
        if (!file.exists() || file.length() == 0L) throw DumpUploadException.EmptyFile()

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaTypeOrNull()),
            )
            .addFormDataPart("deviceAddress", deviceAddress)
            .addFormDataPart("deviceName", deviceName)
            .addFormDataPart("capturedAt", capturedAt)
            .build()

        val req = Request.Builder()
            .url(DivePlanEndpoints.BASE_URL + DivePlanEndpoints.DIVES_FIT + "?async=true&stage=true")
            .post(multipart)
            .build()

        val resp = try {
            client.newCall(req).execute()
        } catch (e: Exception) {
            throw DumpUploadException.Network(e)
        }

        resp.use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val err = runCatching { errAdapter.fromJson(body) }.getOrNull()
                throw DumpUploadException.Server(r.code, err?.error ?: body.take(200))
            }
            return respAdapter.fromJson(body) ?: DumpParseResp()
        }
    }
}
