package cn.diveplan.importer.data.network

import cn.diveplan.importer.data.ApiKeyStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 给所有非匿名请求自动塞 `X-Api-Key`。
 *
 * 匿名 endpoint（[DivePlanEndpoints.CONSUME_BIND_CODE]）显式跳过，
 * 因为 ApiKey 是这个 endpoint 的「输出」，请求时还没有。
 *
 * P1 阶段 ApiKey 还没产生时，业务调用都不该发生，但作为防御性 fallback：
 * key 为空时不塞 header，让后端返回 401，UI 层处理「未绑定」状态。
 */
@Singleton
class ApiKeyAuthInterceptor @Inject constructor(
    private val apiKeyStore: ApiKeyStore,
) : Interceptor {

    /** 不需要 ApiKey 的 endpoint 白名单 */
    private val anonymousPaths = listOf(
        DivePlanEndpoints.CONSUME_BIND_CODE,
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val isAnonymous = anonymousPaths.any { path.startsWith(it) }
        if (isAnonymous) return chain.proceed(original)

        val key = apiKeyStore.get()
        val req = if (key.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header("X-Api-Key", key)
                .build()
        }
        return chain.proceed(req)
    }
}
