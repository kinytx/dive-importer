package cn.diveplan.importer.data

import android.os.Build
import cn.diveplan.importer.BuildConfig
import cn.diveplan.importer.data.network.BindApi
import cn.diveplan.importer.data.network.BindException
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 绑定流程总入口 —— ViewModel 只跟这个类对话。
 *
 *   1. consume 把 6 位码送给服务端
 *   2. 服务端返回 ApiKey + Prefix + ExpiresAt
 *   3. 立刻塞进 [ApiKeyStore] 持久化（**只有这一次能拿到 ApiKey 明文**，后端不会再吐）
 *   4. 抛错由 ViewModel 翻译成用户文案
 *
 * 解绑（[unbind]）只清本地 ApiKey；不调服务端 revoke endpoint（API Key 在服务端保留，
 * 用户在小程序 / web 端可以主动删；后端也支持过期）。
 */
@Singleton
class BindRepository @Inject constructor(
    private val api: BindApi,
    private val apiKeyStore: ApiKeyStore,
) {
    val apiKey: StateFlow<String?> = apiKeyStore.apiKey
    val prefix: StateFlow<String?> = apiKeyStore.prefix

    fun isBound(): Boolean = apiKeyStore.isBound()

    /**
     * 用 6 位绑定码换 ApiKey。
     *
     * @return [BindResult.Success]（带 prefix 让 UI 显示）/ 各种 [BindResult.Failure]
     */
    suspend fun consumeBindCode(code: String): BindResult {
        return try {
            val resp = api.consumeBindCode(
                code = code,
                deviceLabel = buildDeviceLabel(),
                probeVersion = BuildConfig.VERSION_NAME,
            )
            apiKeyStore.save(resp.apiKey, resp.prefix, resp.expiresAt)
            BindResult.Success(prefix = resp.prefix, expiresAt = resp.expiresAt)
        } catch (e: BindException.InvalidOrExpired) {
            BindResult.Failure.InvalidCode
        } catch (e: BindException.Network) {
            BindResult.Failure.Network(e.message ?: "网络异常")
        } catch (e: BindException.Server) {
            BindResult.Failure.Server(e.status, e.message ?: "服务器异常")
        } catch (e: Exception) {
            BindResult.Failure.Server(0, e.message ?: "未知错误")
        }
    }

    fun unbind() {
        apiKeyStore.clear()
    }

    /** 设备标签：「Pixel 8 Pro · Android 14」—— 让用户在小程序 / web 端看「我的绑定设备」时认得出 */
    private fun buildDeviceLabel(): String {
        val brand = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: "Android"
        val model = Build.MODEL ?: ""
        val ver = Build.VERSION.RELEASE ?: ""
        return "$brand $model · Android $ver".trim().take(64)
    }
}

sealed interface BindResult {
    data class Success(val prefix: String, val expiresAt: String) : BindResult

    sealed interface Failure : BindResult {
        /** 6 位码不存在 / 过期 / 已被消费 */
        object InvalidCode : Failure
        data class Network(val detail: String) : Failure
        data class Server(val status: Int, val detail: String) : Failure
    }
}
