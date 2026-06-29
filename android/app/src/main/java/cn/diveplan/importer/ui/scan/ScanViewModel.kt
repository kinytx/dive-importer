package cn.diveplan.importer.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.diveplan.importer.ble.BleScanError
import cn.diveplan.importer.ble.BleScanner
import cn.diveplan.importer.ble.BondedDiveComputer
import cn.diveplan.importer.ble.ClassicBtConnector
import cn.diveplan.importer.ble.DiscoveredBleDevice
import cn.diveplan.importer.ble.VendorMatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 扫描页 VM：合成 [ScanUiState]，包含：
 *   - BLE 发现列表（[BleScanner]，实时更新）
 *   - 已配对经典蓝牙潜水电脑（[ClassicBtConnector.bondedDiveComputers]，进屏幕时加载一次）
 *
 * BLE 列表排序（vendor 识别优先 > RSSI > firstSeenAt）保持不变。
 * 已配对设备进屏幕时扫一次；用户手动配对后可调 [refreshBonded]。
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val bleScanner: BleScanner,
    private val classicBtConnector: ClassicBtConnector,
) : ViewModel() {

    private val _bonded = MutableStateFlow<List<BondedDiveComputer>>(emptyList())

    val uiState: StateFlow<ScanUiState> = combine(
        bleScanner.devices,
        bleScanner.scanning,
        bleScanner.error,
        _bonded,
    ) { devices, scanning, error, bonded ->
        ScanUiState(
            bleDevices    = devices.sortedWith(deviceComparator),
            scanning      = scanning,
            error         = error,
            bondedDevices = bonded,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ScanUiState(),
    )

    fun startScan() = bleScanner.start()
    fun stopScan()  = bleScanner.stop()

    /** 刷新已配对经典蓝牙列表（进屏幕时调一次；用户在系统蓝牙配对新设备后也可调）*/
    fun refreshBonded() {
        viewModelScope.launch {
            _bonded.value = classicBtConnector.bondedDiveComputers()
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleScanner.stop()
    }

    private val deviceComparator = Comparator<DiscoveredBleDevice> { a, b ->
        val aHit = a.vendorMatch is VendorMatch.Hit
        val bHit = b.vendorMatch is VendorMatch.Hit
        when {
            aHit && !bHit -> -1
            !aHit && bHit ->  1
            else -> compareValues(b.rssi, a.rssi)
                .let { if (it != 0) it else compareValues(a.firstSeenAt, b.firstSeenAt) }
        }
    }
}

data class ScanUiState(
    val bleDevices:    List<DiscoveredBleDevice> = emptyList(),
    val scanning:      Boolean                   = false,
    val error:         BleScanError?             = null,
    val bondedDevices: List<BondedDiveComputer>  = emptyList(),
) {
    /** 向后兼容：其他地方如果还用 state.devices 仍然有效 */
    val devices: List<DiscoveredBleDevice> get() = bleDevices
}
