package cn.diveplan.importer.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 跨 API 等级（26~36）的 BLE 权限抽象。
 *
 *   - API 31+（Android 12）：BLUETOOTH_SCAN + BLUETOOTH_CONNECT
 *       BLUETOOTH_SCAN 配 usesPermissionFlags="neverForLocation"（声明在 manifest）
 *       → 不再需要 ACCESS_FINE_LOCATION
 *   - API 26-30：旧 BLUETOOTH + BLUETOOTH_ADMIN + ACCESS_FINE_LOCATION
 *
 * 业务层调 [requiredPermissions] 拿到当前 API 等级该问的权限组，
 * 然后 ActivityResultLauncher 一并请求。
 */
object BlePermissions {

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            // Android 11- 旧权限模型
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }

    fun hasAllPermissions(context: Context): Boolean =
        requiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
}
