package com.sinelynx.grindingrobot.core.bluetooth

import android.Manifest
import androidx.annotation.RequiresPermission
import com.sinelynx.grindingrobot.core.bluetooth.service.BluetoothService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 蓝牙助手类 - 提供蓝牙功能的高级封装接口
 *
 * 该类封装了蓝牙服务的使用方法，为上层应用提供简化的蓝牙操作接口
 *
 * @param bluetoothService 蓝牙服务实例
 * @author Dreamj
 */
@Singleton
class BluetoothHelper @Inject constructor(
    private val bluetoothService: BluetoothService
) {
    
    // 设备发现回调
    private var onDeviceFoundListener: ((android.bluetooth.BluetoothDevice, Int) -> Unit)? = null

    /**
     * 设置设备发现监听器
     *
     * @param listener 设备发现回调
     */
    fun setOnDeviceFoundListener(listener: (android.bluetooth.BluetoothDevice, Int) -> Unit) {
        onDeviceFoundListener = listener
    }
    
    /**
     * 开始扫描蓝牙设备
     *
     * @return 扫描是否启动成功
     */
    fun startScan(): Boolean {
        CoroutineScope(Dispatchers.IO).launch {
            bluetoothService.startScan().collect { device ->
                // 获取RSSI值，默认-50
                onDeviceFoundListener?.invoke(device, -50)
            }
        }
        return true
    }
    
    /**
     * 停止扫描蓝牙设备
     */
    fun stopScan() {
        bluetoothService.stopScan()
    }
    
    /**
     * 连接蓝牙设备
     *
     * @param address 设备地址
     * @return 连接是否成功
     */
    fun connect(address: String): Boolean {
        val device = bluetoothService.getRemoteDevice(address) ?: return false
        return bluetoothService.connectToDevice(device)
    }

    /**
     * 检查设备是否支持蓝牙
     *
     * @return 设备是否支持蓝牙
     */
    fun isBluetoothSupported(): Boolean {
        return bluetoothService.isBluetoothSupported()
    }

    /**
     * 检查蓝牙是否已启用
     *
     * @return 蓝牙是否已启用
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothService.isBluetoothEnabled()
    }

    /**
     * 开始扫描蓝牙设备(返回Flow)
     *
     * @return 蓝牙设备流
     */
    fun startScanFlow(): Flow<android.bluetooth.BluetoothDevice> {
        return bluetoothService.startScan()
    }

    /**
     * 连接到蓝牙设备
     *
     * @param device 蓝牙设备
     * @return 连接是否成功
     */
    fun connectToDevice(device: android.bluetooth.BluetoothDevice): Boolean {
        return bluetoothService.connectToDevice(device)
    }

    /**
     * 断开蓝牙连接
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        bluetoothService.disconnect()
    }

    /**
     * 发送数据到蓝牙设备
     *
     * @param data 要发送的数据
     * @return 发送是否成功
     */
    fun sendData(data: ByteArray): Boolean {
        return bluetoothService.sendData(data)
    }

    /**
     * 发送字符串数据到蓝牙设备
     *
     * @param data 要发送的字符串数据
     * @return 发送是否成功
     */
    fun sendStringData(data: String): Boolean {
        return bluetoothService.sendData(data.toByteArray())
    }

    /**
     * 接收数据的流
     */
    fun getReceivedDataFlow() = bluetoothService.receivedDataFlow

    /**
     * 服务发现状态流，true 表示已完成服务发现
     */
    fun getServiceDiscoveryStartedFlow(): StateFlow<Boolean> = bluetoothService.serviceDiscoveryStarted
}