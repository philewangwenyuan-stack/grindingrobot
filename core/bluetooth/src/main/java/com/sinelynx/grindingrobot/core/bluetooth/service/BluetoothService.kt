package com.sinelynx.grindingrobot.core.bluetooth.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import com.sinelynx.grindingrobot.core.util.log.LogUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 蓝牙服务 - 提供蓝牙连接、扫描、数据收发等功能
 *
 * @author Dreamj
 */
@Singleton
class BluetoothService @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        bluetoothManager?.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private val _receivedDataFlow = MutableStateFlow(ByteArray(0))
    val receivedDataFlow: StateFlow<ByteArray> = _receivedDataFlow
    private val desiredMtu = 247
    private var mtuRequested = false
    private var negotiatedMtu = 23
    private val _serviceDiscoveryStarted = MutableStateFlow(false)
    val serviceDiscoveryStarted: StateFlow<Boolean> = _serviceDiscoveryStarted.asStateFlow()
    private var serviceDiscoveryRequested = false

    // 保存扫描回调
    private var currentScanCallback: android.bluetooth.le.ScanCallback? = null

    /**
     * 检查设备是否支持蓝牙
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    /**
     * 检查蓝牙是否已启用
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * 开始扫描蓝牙设备
     */
    @SuppressLint("MissingPermission")
    fun startScan(): Flow<BluetoothDevice> = callbackFlow {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            LogUtils.e("Bluetooth scan permission not granted")
            close()
            return@callbackFlow
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            LogUtils.e("Bluetooth LE scanner not available")
            close()
            return@callbackFlow
        }

        val scanCallback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult?) {
                result?.device?.let { device ->
                    // 过滤设备名称以"FJLQ"或"SL"开头的设备
                    if (device.name?.startsWith("SL") == true || device.name?.contains("ESP") == true) {
                        LogUtils.d("Found Bluetooth device: ${device.name} - ${device.address}")
                        trySend(device)
                    }
                }
            }

            override fun onBatchScanResults(results: MutableList<android.bluetooth.le.ScanResult>?) {
                results?.forEach { result ->
                    result.device?.let { device ->
                        // 过滤设备名称以"FJLQ"或"SL"开头的设备
                        if (device.name?.startsWith("SL") == true || device.name?.contains("ESP") == true) {
                            LogUtils.d("Found Bluetooth device: ${device.name} - ${device.address}")
                            trySend(device)
                        }
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                LogUtils.e("Bluetooth scan failed with error code: $errorCode")
                close()
            }
        }
        
        currentScanCallback = scanCallback

        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(null, settings, scanCallback)

        awaitClose {
            scanner.stopScan(scanCallback)
            currentScanCallback = null
            LogUtils.d("Bluetooth scan stopped")
        }
    }

    /**
     * 停止扫描蓝牙设备
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        currentScanCallback?.let { callback ->
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(callback)
                currentScanCallback = null
                LogUtils.d("Bluetooth scan stopped manually")
            } catch (e: Exception) {
                LogUtils.e("Error stopping Bluetooth scan: ${e.message}")
            }
        }
    }
    
    /**
     * 解析特征属性为可读格式
     */
    private fun getCharacteristicProperties(properties: Int): String {
        val propertyList = mutableListOf<String>()
        
        if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_BROADCAST > 0) {
            propertyList.add("BROADCAST")
        }
        if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ > 0) {
            propertyList.add("READ")
        }
        if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE > 0) {
            propertyList.add("WRITE")
        }
        if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE > 0) {
            propertyList.add("WRITE_NO_RESPONSE")
        }
        if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
            propertyList.add("NOTIFY")
        }
        if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_INDICATE > 0) {
            propertyList.add("INDICATE")
        }
        if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE > 0) {
            propertyList.add("SIGNED_WRITE")
        }
        if (properties and android.bluetooth.BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS > 0) {
            propertyList.add("EXTENDED_PROPS")
        }
        
        return propertyList.joinToString(", ")
    }
    
    /**
     * 获取远程蓝牙设备
     *
     * @param address 设备MAC地址
     * @return 蓝牙设备对象
     */
    fun getRemoteDevice(address: String): BluetoothDevice? {
        return try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: Exception) {
            LogUtils.e("Error getting remote device: ${e.message}")
            null
        }
    }

    /**
     * 连接到蓝牙设备
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice): Boolean {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            LogUtils.e("Bluetooth connect permission not granted")
            return false
        }

        try {
            LogUtils.d("Attempting to connect to device: ${device.address}")
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
            return true
        } catch (e: Exception) {
            LogUtils.e("Failed to connect to device: ${e.message}")
            return false
        }
    }

    /**
     * 断开蓝牙连接
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
            mtuRequested = false
            negotiatedMtu = 23
            serviceDiscoveryRequested = false
            _serviceDiscoveryStarted.value = false
            LogUtils.d("Bluetooth disconnected")
        } catch (e: Exception) {
            LogUtils.e("Error disconnecting Bluetooth: ${e.message}")
        }
    }

    /**
     * 发送数据到蓝牙设备
     */
    @SuppressLint("MissingPermission")
    fun sendData(data: ByteArray): Boolean {
        val hexPayload = data.joinToString(" ") { "%02X".format(it) }
        LogUtils.d("Sending data to Bluetooth hex: $hexPayload")
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            LogUtils.e("Bluetooth connect permission not granted")
            return false
        }

        val gatt = bluetoothGatt ?: run {
            LogUtils.e("BluetoothGatt is null, not connected to any device")
            return false
        }

        LogUtils.d("Writing data to Bluetooth")
        // 这里需要根据实际的蓝牙设备服务和特征 UUID 进行修改
        // 示例：使用通用的串口服务 UUID (Serial Port Profile)
        val service = gatt.getService(UUID.fromString("6fafc201-1fb5-459e-8fcc-c5c9c331914b"))
        val characteristic = service?.getCharacteristic(UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")) ?: run {
            // 如果设备不支持串口服务，尝试获取第一个可用的可写特征
            gatt.services.forEach { svc ->
                svc.characteristics.forEach { char ->
                    if (char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE > 0) {
                        LogUtils.d("Found writable characteristic: ${char.uuid}")
                        return@run char
                    }
                }
            }
            null
        }

        return if (characteristic != null) {
            LogUtils.d("Writing data to characteristic: ${characteristic.uuid}")
            characteristic.value = data
            gatt.writeCharacteristic(characteristic)
        } else {
            LogUtils.d("Characteristic not found")
            false
        }
    }

    private val notifyUuid = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val notifyCccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    private fun enableNotify(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        LogUtils.d("Enabling notification for characteristic: ${characteristic.uuid}")
        gatt.setCharacteristicNotification(characteristic, true)
        characteristic.getDescriptor(notifyCccdUuid)?.let { cccd ->
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(cccd)
            LogUtils.d("Enabled notification for characteristic: ${characteristic.uuid}")
        }
    }
    /**
     * GATT回调处理连接状态和数据传输
     */
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    LogUtils.d("Bluetooth device connected (status=$status)")
                    _serviceDiscoveryStarted.value = false
                    serviceDiscoveryRequested = false
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val ok = gatt?.requestMtu(desiredMtu)
                        mtuRequested = ok == true
                        LogUtils.d("MTU request sent on connect ($desiredMtu): $ok")
                        if (ok != true) {
                            startServiceDiscovery(gatt, "mtu request failed/unsupported")
                        }
                    } else {
                        LogUtils.w("BLUETOOTH_CONNECT not granted, skip MTU request")
                        startServiceDiscovery(gatt, "no permission for mtu")
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    LogUtils.d("Bluetooth device disconnected")
                    _serviceDiscoveryStarted.value = false
                    serviceDiscoveryRequested = false
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        private fun startServiceDiscovery(gatt: BluetoothGatt?, reason: String) {
            if (serviceDiscoveryRequested) return
            serviceDiscoveryRequested = true
            val ok = gatt?.discoverServices() ?: false
            LogUtils.d("Discover services requested (reason=$reason): $ok")
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            LogUtils.d("onServicesDiscovered status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _serviceDiscoveryStarted.value = true
                LogUtils.d("Bluetooth services discovered")
                // 打印所有蓝牙服务和特征
                gatt?.services?.forEach { service ->
                    LogUtils.d("Service UUID: ${service.uuid}")
                    service.characteristics.forEach { characteristic ->
                        val properties = getCharacteristicProperties(characteristic.properties)
                        LogUtils.d("Characteristic UUID: ${characteristic.uuid}, Properties: $properties")
                    }
                }
                LogUtils.d("Trying to enable notification for characteristic: $notifyUuid")
                val notifyChar = gatt?.services
                    ?.firstOrNull { service ->
                        service.getCharacteristic(notifyUuid) != null
                    }
                    ?.getCharacteristic(notifyUuid)
                if (notifyChar != null && (notifyChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                    enableNotify(gatt, notifyChar)
                } else {
                    LogUtils.e("Notify characteristic not found or does not support notify")
                }
            } else {
                _serviceDiscoveryStarted.value = false
                LogUtils.e("Bluetooth services discovery failed with status: $status")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
                mtuRequested = true
                LogUtils.d("MTU changed successfully to $mtu")
                startServiceDiscovery(gatt, "onMtuChanged success")
            } else {
                mtuRequested = false
                LogUtils.e("Failed to change MTU with status: $status")
                startServiceDiscovery(gatt, "onMtuChanged failed status=$status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            LogUtils.d("Characteristic read from Bluetooth")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            LogUtils.d("Characteristic  onCharacteristicChanged read from Bluetooth")

            val bytes = value
            val hex = bytes.joinToString(" ") { "%02X".format(it) }
            LogUtils.d("Received data from Bluetooth hex: $hex")
            try {
                _receivedDataFlow.value = bytes
            } catch (e: Exception) {
                LogUtils.e("Error emitting received data: ${e.message}")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: android.bluetooth.BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                LogUtils.d("Data written to Bluetooth device successfully")
                LogUtils.d("Data  onCharacteristicWrite uuid: ${characteristic?.uuid}")
            } else {
                LogUtils.e("Failed to write data to Bluetooth device with status: $status")
            }
        }
    }
}