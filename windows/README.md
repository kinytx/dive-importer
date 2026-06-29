# DivePlan 潜水日志导入助手 · Windows

Windows 桌面版 importer。当前是第一阶段骨架：

- BLE 广播扫描
- 使用共享 `device-rules.json` 识别潜水电脑 vendor / product
- 显示设备列表、RSSI、地址、识别来源和提示

后续阶段：

1. 账号绑定：6 位码 / deep link -> API Key，存 Windows Credential Manager 或 DPAPI。
2. BLE GATT 连接：按 vendor/product 进入具体读取路径。
3. 经典蓝牙 / SPP：用 Windows Rfcomm API 或串口桥接抓取 dump。
4. 离线上传队列：本地保存 dump 和 job 状态，恢复网络后自动上传。
5. Garmin Sidecar：复用 DivePlan WSS 协议，Windows 端重写客户端。

构建：

```powershell
cd S:\GMP\dive-importers\windows\DivePlanImporter.Windows
dotnet build
dotnet run
```

规则来源：

- `S:\GMP\dive-importers\shared\device-rules.json`
- 本工程构建时复制 `Rules\device-rules.json` 到输出目录
