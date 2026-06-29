package cn.diveplan.importer.data.network

import cn.diveplan.importer.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 全局网络栈 —— OkHttp 单例 + Moshi 单例。
 *
 * - `X-Api-Key` 由 [ApiKeyAuthInterceptor] 注入；只有 P2+ 的业务接口才需要它，
 *   P1 的 bind-codes/consume 是匿名 endpoint，不带 header
 * - 调试包打开 HTTP body 日志，release 关
 *
 * ECS baseUrl 写死在 [DivePlanEndpoints]，所有调用都走相对路径
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        apiKeyInterceptor: ApiKeyAuthInterceptor,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideBindApi(client: OkHttpClient, moshi: Moshi): BindApi =
        BindApiImpl(client, moshi)

    @Provides
    @Singleton
    fun provideDumpUploadApi(client: OkHttpClient, moshi: Moshi): DumpUploadApi =
        DumpUploadApiImpl(client, moshi)

    @Provides
    @Singleton
    fun provideDiveImportJobApi(client: OkHttpClient, moshi: Moshi): DiveImportJobApi =
        DiveImportJobApiImpl(client, moshi)
}
