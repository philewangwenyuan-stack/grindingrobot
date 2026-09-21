# TCP 模块使用指南

## 概述
TCP 模块提供了完整的 TCP 客户端功能，包括连接管理、数据收发、心跳检测、自动重连等功能。

## 主要功能

### 1. TCP 连接
- 支持异步连接
- 自动重连机制（最多5次，间隔5秒）
- 连接超时控制

### 2. 数据收发
- 支持发送字节数组
- 支持发送文本消息
- 自动接收服务器数据流

### 3. 心跳检测
- 自动心跳发送（默认30秒间隔）
- 可自定义心跳数据
- 心跳失败自动断开重连

### 4. 连接状态管理
- 实时获取连接状态
- 连接/断开/重连事件回调

## 使用示例

### 基础使用

```kotlin
// 1. 创建 TcpManager 实例
val tcpManager = TcpManager()

// 2. 设置回调（可选，如果需要自定义处理）
tcpManager.setCallback(object : TcpService.TcpCallback {
    override fun onConnected() {
        println("连接成功")
    }

    override fun onDataReceived(data: ByteArray) {
        val message = String(data, Charsets.UTF_8)
        println("收到数据: $message")
    }

    override fun onDisconnected(reason: String) {
        println("连接断开: $reason")
    }

    override fun onFailure(error: Throwable) {
        println("连接失败: ${error.message}")
    }

    override fun onHeartbeatSent() {
        println("心跳已发送")
    }
})

// 3. 连接服务器
tcpManager.connect("192.168.1.100", 8080)

// 4. 发送数据
tcpManager.sendText("Hello Server")
tcpManager.sendData(byteArrayOf(0x01, 0x02, 0x03))

// 5. 设置心跳数据
tcpManager.setHeartbeatText("PING")

// 6. 检查连接状态
if (tcpManager.isConnected()) {
    println("当前已连接")
}

// 7. 断开连接
tcpManager.disconnect()

// 8. 释放资源（在不再使用时调用）
tcpManager.release()
```

### 在 ViewModel 中使用

```kotlin
class MyViewModel : ViewModel() {
    private val tcpManager = TcpManager()
    
    init {
        tcpManager.setCallback(object : TcpService.TcpCallback {
            override fun onConnected() {
                // 更新UI状态
            }
            
            override fun onDataReceived(data: ByteArray) {
                // 处理接收到的数据
            }
            
            // ... 其他回调
        })
    }
    
    fun connectToServer(host: String, port: Int) {
        tcpManager.connect(host, port)
    }
    
    fun sendMessage(message: String) {
        tcpManager.sendText(message)
    }
    
    override fun onCleared() {
        super.onCleared()
        tcpManager.release()
    }
}
```

### 使用示例类

```kotlin
// 直接使用提供的示例类
val tcpExample = TcpExample()
tcpExample.connect("192.168.1.100", 8080)
tcpExample.sendMessage("Hello")
```

## API 说明

### TcpManager

| 方法 | 说明 | 参数 | 返回值 |
|------|------|------|--------|
| connect(host, port) | 连接到TCP服务器 | host: 服务器地址<br>port: 端口号 | 无 |
| sendData(data) | 发送字节数据 | data: ByteArray | Boolean |
| sendText(message) | 发送文本消息 | message: String | Boolean |
| setHeartbeatData(data) | 设置心跳数据 | data: ByteArray | 无 |
| setHeartbeatText(message) | 设置心跳文本 | message: String | 无 |
| disconnect() | 断开连接 | 无 | 无 |
| reconnect() | 手动重连 | 无 | 无 |
| isConnected() | 检查连接状态 | 无 | Boolean |
| getConnectionStatus() | 获取连接状态描述 | 无 | String |
| setCallback(callback) | 设置回调接口 | callback: TcpCallback | 无 |
| release() | 释放所有资源 | 无 | 无 |

### TcpCallback 接口

```kotlin
interface TcpCallback {
    fun onConnected()                    // 连接成功
    fun onDataReceived(data: ByteArray)  // 接收到数据
    fun onDisconnected(reason: String)   // 连接断开
    fun onFailure(error: Throwable)      // 连接失败
    fun onHeartbeatSent()                // 心跳已发送
}
```

## 配置说明

### 可配置项（在 TcpService 中）
- `connectTimeout`: 连接超时时间（默认10秒）
- `readTimeout`: 读取超时时间（默认10秒）
- `heartbeatInterval`: 心跳间隔（默认30秒）
- `reconnectInterval`: 重连间隔（默认5秒）
- `maxReconnectAttempts`: 最大重连次数（默认5次）

## 注意事项

1. **网络权限**：模块已自动声明网络权限，无需手动添加
2. **线程安全**：所有网络操作在IO线程执行，回调在主线程
3. **资源释放**：使用完毕后务必调用 `release()` 方法释放资源
4. **心跳机制**：连接成功后自动启动心跳，无需手动管理
5. **自动重连**：连接断开后自动尝试重连，最多5次

## 依赖项

模块依赖以下库：
- Kotlin Coroutines（协程支持）
- core:common
- core:model
- core:util
