# USB串口WebSocket服务器

Android应用程序，将USB串口转换器绑定到WebSocket客户端，基于 [usb-serial-telnet-server](https://github.com/ClusterM/usb-serial-telnet-server) 修改而来。

只需将USB串口适配器连接到Android设备的USB OTG端口，启动此应用程序，然后使用任何WebSocket客户端连接到它，例如：
* 使用同一Android设备上的浏览器（连接到localhost）
* 使用同一网络上的计算机上的WebSocket客户端（通过Wi-Fi连接）
* 使用任何支持WebSocket的客户端应用程序

## 主要特点

✅ **纯文本发送**：客户端发送的文本直接转发到串口  
✅ **纯文本接收**：串口接收的数据直接作为文本发送给客户端  
✅ **无格式转换**：不进行十六进制、Blob等复杂转换  
✅ **简单易用**：就像普通的聊天工具一样简单  
✅ **跨平台兼容**：任何支持WebSocket的客户端都可以使用

## 兼容设备

此应用程序使用 [mik3y的usb-serial-for-android库](https://github.com/mik3y/usb-serial-for-android) 并支持USB转串口转换器芯片：

* FTDI FT232R, FT232H, FT2232H, FT4232H, FT230X, FT231X, FT234XD
* Prolific PL2303
* Silabs CP2102 和所有其他 CP210x
* Qinheng CH340, CH341A

一些其他设备特定驱动程序：
* GsmModem 设备，例如基于Unisoc的Fibocom GSM调制解调器
* Chrome OS CCD（闭壳调试）

以及实现通用CDC/ACM协议的设备，如：
* Qinheng CH9102
* Microchip MCP2221
* 使用ATmega32U4的Arduino
* 使用V-USB软件USB的Digispark
* ...

## 使用方法

### 1. 启动Android应用
- 安装并启动WebSocket服务器应用
- 连接USB串口设备
- 记录显示的WebSocket地址（如：`ws://192.168.1.100:8080`）

### 2. 使用测试页面
打开 `text_only_test.html` 文件：

1. **输入WebSocket地址**：在输入框中输入Android设备显示的地址
2. **点击连接**：建立WebSocket连接
3. **发送消息**：在消息框中输入文本，点击发送或按回车
4. **查看接收**：串口返回的数据会直接显示在日志中

### 3. 通信流程

```
客户端 → WebSocket → Android应用 → USB串口
USB串口 → Android应用 → WebSocket → 客户端
```

## 技术细节

### 服务器端（Android）
- 接收文本消息 → 直接转换为UTF-8字节 → 发送到串口
- 串口接收字节 → 直接转换为UTF-8文本 → 发送给客户端
- 忽略所有二进制消息

### 客户端（浏览器）
- 发送：`ws.send("文本消息")`
- 接收：`event.data` 直接显示文本内容



现在您可以像使用聊天工具一样与串口设备进行文本通信了！
