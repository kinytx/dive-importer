package cn.diveplan.importer.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地 dump 文件管理。
 *
 * 目录结构：
 *   filesDir/
 *     import-dumps/
 *       <SafeDeviceName>_<AA-BB-CC-DD-EE-FF>/
 *         20260620_143052.bin          ← 原始 dump
 *         20260620_143052.bin.uploaded ← 上传成功后重命名（保留原文件作审计 / 重传）
 *
 * 职责：
 *   - [saveDump]：写入原始字节，返回 File
 *   - [pendingDumps]：未上传（无 .uploaded 标记）的列表 → P4 WorkManager 用
 *   - [markUploaded]：把 .bin 标记为已上传
 *   - [allDumps]：全量列表，供未来历史页使用
 *
 * 不在这里做上传；上传由 P4 DumpUploadWorker 负责。
 */
@Singleton
class DumpRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File
        get() = File(context.filesDir, "import-dumps").also { it.mkdirs() }

    /**
     * 把 Garmin FIT 文件写到本地。
     * 目录：filesDir/import-dumps/garmin-<AA-BB-CC>/<ts>_<fileName>
     * @return 写入的 .fit 文件
     */
    fun saveFit(address: String, deviceName: String?, bytes: ByteArray, fileName: String): File {
        val safeAddr = address.replace(':', '-')
        val dir = File(root, "garmin-${safeAddr}").also { it.mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        // 确保扩展名为 .fit
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._\\-]"), "_").trimEnd('.').let {
            if (it.endsWith(".fit", ignoreCase = true)) it else "$it.fit"
        }
        val file = File(dir, "${ts}_${safeName}")
        file.writeBytes(bytes)
        return file
    }

    /**
     * 返回所有未上传的 .fit 文件（无对应 .uploaded 标记）。
     * P5 WorkManager 批量上传用。
     */
    fun pendingFits(): List<File> =
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".fit", ignoreCase = true) }
            .filter { !File(it.parent, it.name + ".uploaded").exists() }
            .toList()

    /**
     * 把 dump 写到本地。
     * @return 写入的 .bin 文件，调用方可拿来做 UI 提示（文件大小等）
     */
    fun saveDump(address: String, deviceName: String?, bytes: ByteArray): File {
        val safeName = (deviceName
            ?.replace(Regex("[^A-Za-z0-9 _\\-]"), "_")
            ?.trim()
            ?.take(32)
            ?: "device")
        val safeAddr = address.replace(':', '-')
        val dir = File(root, "${safeName}_${safeAddr}").also { it.mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "$ts.bin")
        file.writeBytes(bytes)
        return file
    }

    /**
     * 返回所有未上传（无对应 .uploaded 文件）的 .bin dump，
     * 供 P4 WorkManager 批量上传。
     */
    fun pendingDumps(): List<File> =
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".bin") }
            .filter { !File(it.parent, it.name + ".uploaded").exists() }
            .toList()

    /**
     * 上传成功后调。把 .bin 改名为 .bin.uploaded，让 [pendingDumps] 不再返回它。
     * 原内容保留（.uploaded 文件），避免意外情况需要重传时无从追溯。
     */
    fun markUploaded(file: File) {
        val dest = File(file.parent, file.name + ".uploaded")
        file.renameTo(dest)
    }

    /** 全量 dump（含已上传），按修改时间倒序，供历史页使用（P6）。 */
    fun allDumps(): List<DumpEntry> =
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".bin") }
            .map { f ->
                DumpEntry(
                    file       = f,
                    uploaded   = File(f.parent, f.name + ".uploaded").exists(),
                    sizeBytes  = f.length(),
                    modifiedAt = f.lastModified(),
                )
            }
            .sortedByDescending { it.modifiedAt }
            .toList()

    /** 全量 FIT 文件（含已上传），供历史页使用（P6）。 */
    fun allFits(): List<DumpEntry> =
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".fit", ignoreCase = true) }
            .map { f ->
                DumpEntry(
                    file       = f,
                    uploaded   = File(f.parent, f.name + ".uploaded").exists(),
                    sizeBytes  = f.length(),
                    modifiedAt = f.lastModified(),
                )
            }
            .sortedByDescending { it.modifiedAt }
            .toList()

    /** 磁盘占用（字节） */
    fun totalDiskUsage(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

data class DumpEntry(
    val file: File,
    val uploaded: Boolean,
    val sizeBytes: Long,
    val modifiedAt: Long,
)
