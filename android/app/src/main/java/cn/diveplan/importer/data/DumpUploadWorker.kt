package cn.diveplan.importer.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import cn.diveplan.importer.data.network.DumpUploadApi
import cn.diveplan.importer.data.network.DumpUploadException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * P4 离线上传 Worker：把 [DumpRepository.pendingDumps] 逐个上传到后端。
 *
 * 触发时机：
 *   - [ConnectViewModel] 抓 dump 成功后立即调 [enqueue]
 *   - 应用冷启动时如有 pending dump 也调 [enqueue]（onResume 或 App.onCreate）
 *
 * 重试策略：
 *   - 网络错误 → [Result.retry]（WorkManager 指数退避，最多重试 3 次）
 *   - 文件为空 / 服务器 4xx → [Result.failure]（不再重试，避免无效请求）
 *   - 多文件时某个失败不影响其它文件继续上传；全部成功才返回 [Result.success]
 *
 * WorkManager 约束：
 *   - 需要网络（任意网络，不要求 UNMETERED）
 *   - 不需要充电 / 空闲（船上网络一有就传）
 */
@HiltWorker
class DumpUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dumpRepository: DumpRepository,
    private val dumpUploadApi: DumpUploadApi,
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "DumpUploadWorker"
        private const val MAX_RETRIES = 3

        /** 调用此方法触发一次 dump 上传（幂等：KEEP 策略，已在队列则不重复）*/
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<DumpUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result {
        // .bin (RFCOMM dump) + .fit (Garmin FIT) 分别上传
        val pendingBin = dumpRepository.pendingDumps()
        val pendingFit = dumpRepository.pendingFits()
        if (pendingBin.isEmpty() && pendingFit.isEmpty()) return Result.success()

        var anyNetworkError = false

        for (file in pendingBin) {
            anyNetworkError = uploadOne(file, isFit = false) || anyNetworkError
        }
        for (file in pendingFit) {
            anyNetworkError = uploadOne(file, isFit = true) || anyNetworkError
        }

        return when {
            anyNetworkError && runAttemptCount < MAX_RETRIES -> Result.retry()
            else -> Result.success()
        }
    }

    /**
     * 上传单个文件；返回 true 表示遇到网络/5xx 错误，应触发重试。
     */
    private suspend fun uploadOne(file: java.io.File, isFit: Boolean): Boolean {
        // 从路径反推 device address 和 name
        // .bin 目录格式：<Name>_<AA-BB-CC>/<ts>.bin
        // .fit 目录格式：garmin-<AA-BB-CC>/<ts>_<fileName>.fit
        val dirName = file.parentFile?.name ?: ""
        val (deviceAddress, deviceName) = if (isFit) {
            val addr = dirName.removePrefix("garmin-").replace('-', ':')
            addr to "Garmin"
        } else {
            val lastUnderscore = dirName.lastIndexOf('_')
            val addr = if (lastUnderscore >= 0)
                dirName.substring(lastUnderscore + 1).replace('-', ':')
            else ""
            val name = if (lastUnderscore > 0)
                dirName.substring(0, lastUnderscore)
            else dirName
            addr to name
        }

        val capturedAt = DateTimeFormatter.ISO_INSTANT.format(
            Instant.ofEpochMilli(file.lastModified())
        )

        return try {
            if (isFit) {
                dumpUploadApi.uploadFit(
                    file          = file,
                    deviceAddress = deviceAddress,
                    deviceName    = deviceName,
                    capturedAt    = capturedAt,
                )
            } else {
                dumpUploadApi.uploadDump(
                    file          = file,
                    deviceAddress = deviceAddress,
                    deviceName    = deviceName,
                    capturedAt    = capturedAt,
                )
            }
            dumpRepository.markUploaded(file)
            false
        } catch (e: DumpUploadException.Network) {
            true
        } catch (e: DumpUploadException.EmptyFile) {
            dumpRepository.markUploaded(file)
            false
        } catch (e: DumpUploadException.Server) {
            e.status !in 400..499   // 5xx → retry; 4xx → skip
        }
    }
}
