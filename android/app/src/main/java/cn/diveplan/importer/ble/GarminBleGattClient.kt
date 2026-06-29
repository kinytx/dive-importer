package cn.diveplan.importer.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Garmin 潜水电脑 BLE GATT 客户端。
 *
 * 负责：
 *  1. 连接 Garmin GATT service（6A4E2800-...）
 *  2. 协商 MTU 247
 *  3. 订阅 notify 特征（CCCD 使能）
 *  4. 暴露 [GarminGattSession] 供 [GarminWssBridgeSession] 使用
 *
 * 被 Garmin sidecar WSS bridge 用作「BLE 字节通道」：
 *   sidecar → transport.write → [GarminGattSession.write]
 *   BLE notify  → [GarminGattSession.notifyFlow] → transport.notify → sidecar
 */
@Singleton
class GarminBleGattClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        val SERVICE_UUID = UUID.fromString("6A4E2800-667B-11E3-949A-0800200C9A66")
        val NOTIFY_UUID  = UUID.fromString("6A4E2810-667B-11E3-949A-0800200C9A66")
        val WRITE_UUID   = UUID.fromString("6A4E2820-667B-11E3-949A-0800200C9A66")
        val CCCD_UUID    = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
        const val MTU_REQUEST = 247
    }

    /**
     * 连接 GATT，完成 MTU + notify 设置，返回可用会话。
     * 必须在 Dispatchers.IO 下调用（或 withContext 包裹）。
     *
     * @throws GarminBleException 各种错误子类
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): GarminGattSession = withContext(Dispatchers.IO) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: throw GarminBleException.NoAdapter
        val device = adapter.getRemoteDevice(address)
            ?: throw GarminBleException.InvalidAddress(address)

        val notifyChannel         = Channel<ByteArray>(Channel.UNLIMITED)
        val writeCompletionChannel = Channel<Boolean>(1)
        val setupDeferred          = CompletableDeferred<BluetoothGatt>()

        val callback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    setupDeferred.completeExceptionally(
                        GarminBleException.ConnectFailed("GATT status=$status"))
                    return
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED    -> gatt.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (!setupDeferred.isCompleted)
                            setupDeferred.completeExceptionally(GarminBleException.ConnectFailed("disconnected during setup"))
                        notifyChannel.close(GarminBleException.Disconnected)
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    setupDeferred.completeExceptionally(
                        GarminBleException.ConnectFailed("discoverServices status=$status")); return
                }
                gatt.requestMtu(MTU_REQUEST)
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                val svc  = gatt.getService(SERVICE_UUID)
                if (svc == null) {
                    setupDeferred.completeExceptionally(GarminBleException.ServiceNotFound); return
                }
                val nc = svc.getCharacteristic(NOTIFY_UUID)
                if (nc == null) {
                    setupDeferred.completeExceptionally(GarminBleException.ServiceNotFound); return
                }
                gatt.setCharacteristicNotification(nc, true)
                val desc = nc.getDescriptor(CCCD_UUID)
                if (desc == null) {
                    setupDeferred.completeExceptionally(GarminBleException.ServiceNotFound); return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(desc)
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int,
            ) {
                if (descriptor.uuid != CCCD_UUID) return
                if (status == BluetoothGatt.GATT_SUCCESS) setupDeferred.complete(gatt)
                else setupDeferred.completeExceptionally(
                    GarminBleException.ConnectFailed("CCCD write status=$status"))
            }

            // API < 33
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic,
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                    characteristic.uuid == NOTIFY_UUID) {
                    @Suppress("DEPRECATION")
                    characteristic.value?.let { notifyChannel.trySend(it) }
                }
            }

            // API >= 33
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray,
            ) {
                if (characteristic.uuid == NOTIFY_UUID) notifyChannel.trySend(value)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int,
            ) {
                writeCompletionChannel.trySend(status == BluetoothGatt.GATT_SUCCESS)
            }
        }

        val gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        val readyGatt = setupDeferred.await()   // throws on failure

        GarminGattSession(
            gatt                  = readyGatt,
            notifyChannel         = notifyChannel,
            writeCompletionChannel = writeCompletionChannel,
            service               = readyGatt.getService(SERVICE_UUID)!!,
        )
    }
}

/**
 * 已就绪的 Garmin GATT 会话（MTU 已协商，notify 已订阅）。
 */
@SuppressLint("MissingPermission")
class GarminGattSession internal constructor(
    private val gatt: BluetoothGatt,
    private val notifyChannel: Channel<ByteArray>,
    private val writeCompletionChannel: Channel<Boolean>,
    private val service: BluetoothGattService,
) {
    /** Garmin notify 字节流 — 用于转发给 sidecar */
    val notifyFlow: Flow<ByteArray> = notifyChannel.receiveAsFlow()

    /**
     * 写入 Garmin write characteristic；等待 onCharacteristicWrite 确认。
     * sidecar 按 writeAck 串行驱动写入，不会并发，不需要额外锁。
     */
    suspend fun write(bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val char = service.getCharacteristic(GarminBleGattClient.WRITE_UUID)
            ?: throw GarminBleException.WriteFailed("write characteristic not found")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
        val ok = writeCompletionChannel.receive()
        if (!ok) throw GarminBleException.WriteFailed("GATT write returned failure")
    }

    fun close() {
        gatt.disconnect()
        gatt.close()
    }
}

sealed class GarminBleException(message: String) : Exception(message) {
    object NoAdapter : GarminBleException("没有蓝牙适配器")
    data class InvalidAddress(val address: String) : GarminBleException("无效设备地址: $address")
    object ServiceNotFound : GarminBleException("未找到 Garmin GATT service")
    data class ConnectFailed(val detail: String) : GarminBleException("GATT 连接失败: $detail")
    data class WriteFailed(val detail: String) : GarminBleException("BLE 写入失败: $detail")
    object Disconnected : GarminBleException("BLE 已断开")
}
