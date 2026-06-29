package cn.diveplan.importer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import cn.diveplan.importer.data.DumpUploadWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口 Application。
 *
 * - Hilt DI 容器在这里实例化（@HiltAndroidApp）
 * - WorkManager 用 Hilt-Work 接管（避免每个 Worker 自己造依赖）
 * - 后续 P0 加：initErrorReporter()（参考 mixer/plan 仓的 shared/utils/error-reporter）
 *
 * AndroidManifest 通过 android:name=".DivePlanImporterApp" 指到这里。
 */
@HiltAndroidApp
class DivePlanImporterApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // P4：冷启动时触发一次上传，处理上次 App 未传完的 pending dump
        DumpUploadWorker.enqueue(this)
        // P6 占位：诊断错误上报（CDID 体系）等 P6 阶段实装
    }
}
