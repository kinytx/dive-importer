package cn.diveplan.importer.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE 扫描 + vendor 自动识别。
 *
 * - 设计成单例（@Singleton），多个 screen 共享一份「最近发现的设备」状态
 * - 暴露 [devices] StateFlow 让 Compose 直接观察
 * - 内部 maintain 一个按 deviceAddress 去重的 map，最新 RSSI 覆盖旧值，但 firstSeenAt 保留
 *
 * 权限 + adapter enabled 检查由调用方（ScanViewModel）先做完再调 [start]。
 */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _devices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredBleDevice>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _error = MutableStateFlow<BleScanError?>(null)
    val error: StateFlow<BleScanError?> = _error.asStateFlow()

    /** 内部 map：address → DiscoveredBleDevice，便于 O(1) 更新 RSSI */
    private val devMap = LinkedHashMap<String, DiscoveredBleDevice>()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            ingest(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach(::ingest)
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
            _error.value = BleScanError.Failed(errorCode)
        }
    }

    @SuppressLint("MissingPermission")  // 调用前必须先过 BlePermissions.hasAllPermissions
    fun start() {
        if (_scanning.value) return
        val a = adapter
        if (a == null) {
            _error.value = BleScanError.NoAdapter
            return
        }
        if (!a.isEnabled) {
            _error.value = BleScanError.BluetoothOff
            return
        }
        val scanner = a.bluetoothLeScanner
        if (scanner == null) {
            _error.value = BleScanError.NoAdapter
            return
        }
        _error.value = null
        devMap.clear()
        _devices.value = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, callback)
            _scanning.value = true
        } catch (e: SecurityException) {
            _error.value = BleScanError.MissingPermission(e.message ?: "")
        }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!_scanning.value) return
        try {
            adapter?.bluetoothLeScanner?.stopScan(callback)
        } catch (_: SecurityException) { /* 静默 */ }
        _scanning.value = false
    }

    private fun ingest(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val name = try { device.name } catch (_: SecurityException) { null }
            ?: result.scanRecord?.deviceName
        val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()

        val now = System.currentTimeMillis()
        val match = VendorDetector.detect(name, serviceUuids)

        val prev = devMap[address]
        val updated = DiscoveredBleDevice(
            address = address,
            name = name,
            rssi = result.rssi,
            serviceUuids = serviceUuids,
            vendorMatch = match,
            firstSeenAt = prev?.firstSeenAt ?: now,
            lastSeenAt = now,
        )
        devMap[address] = updated
        _devices.update { devMap.values.toList() }
    }
}

data class DiscoveredBleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<java.util.UUID>,
    val vendorMatch: VendorMatch,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
)

sealed interface BleScanError {
    object NoAdapter : BleScanError
    object BluetoothOff : BleScanError
    data class MissingPermission(val detail: String) : BleScanError
    data class Failed(val code: Int) : BleScanError
}
