# DivePlan 潜水日志导入助手 · Android

一个 Android app，用于把潜水电脑表上的潜水日志通过蓝牙读出来，自动上传到 DivePlan
账号，跟小程序 / web 端看到的是同一份数据。

## 当前进度

- [x] **P0 项目骨架** —— Kotlin + Compose + Material3，主题对齐小程序 dark/light，Hilt + Room + WorkManager 依赖就位
- [x] **P1 账号绑定** —— 二维码 / 6 位码 → ApiKey；EncryptedSharedPreferences 持久化；deep link 冷/热启动
- [x] **P2 BLE 扫描 + vendor 识别** —— BleScanner + VendorDetector；ScanScreen 实时列表；排序（vendor > RSSI）
- [x] **P3 经典蓝牙 RFCOMM dump 抓取** —— ClassicBtConnector（三段式 socket + 轮询读 + 8s 超时）；DumpRepository（filesDir/import-dumps）；ConnectScreen 状态机（Connecting→Capturing→Done/Failed）；ScanScreen 双分区（已配对 + BLE）
- [x] **P4 离线上传队列** —— DumpUploadWorker（WorkManager + 指数退避）；DumpUploadApi multipart POST /api/me/dives/parse；冷启动 + dump 成功后自动 enqueue
- [x] **P5 Garmin 路径（BLE GATT → WSS sidecar → FIT 落盘）** —— GarminBleGattClient（MTU 247 + notify CCCD）；GarminWssBridgeSession（OkHttp WS 桥接 + device.dive 落 .fit）；GarminConnectViewModel + GarminConnectScreen（离线提示/进度/完成）；DumpUploadWorker 扩展支持 .fit → POST /api/me/dives/fit；MainActivity 按 weak=true 路由
- [x] **P6 历史 / 设置页** —— HistoryScreen（本地 .bin/.fit 列表 + 服务端 job 状态轮询）；SettingsScreen（磁盘用量 + 解绑二次确认）；DiveImportJobApi GET /api/me/dive-import-jobs；ScanScreen 顶栏历史入口；MainActivity 三级路由 scan/history/settings
- [x] **P7 后端 `/api/me/dives/parse` 接受 X-Api-Key** —— multipart POST；`CreateClassicBtDumpJobAsync`（SHA-256 去重 + MetadataJson 存 vendor/product）；worker `classic_bt_dump` case 调 ldc-parser；迁移 `20260620_AddDiveImportJobMetadataJson`
- [ ] 真机校验 + 第一台 Shearwater 抓 dump 上传成功

## 双路径架构

```
扫描发现设备（BLE + 经典蓝牙）
  ├─ vendor 是 Shearwater/Suunto/Mares/... → libdc / native transport 路径
  │     BLE GATT 或 Classic RFCOMM/SPP → 抓 dump 字节流 → filesDir 落盘（离线 OK）
  │     → 上传队列 POST /api/me/dives/parse (X-Api-Key)
  │     → 网络断：队列保留，联网补传（WorkManager）
  │
  └─ vendor 是 Garmin → garmin-sidecar 路径
        BLE → WSS 实时 → server DeviceSessionRouter (driver=garmin-sidecar)
        协议复用 gas-dive-plan/shared/utils/garmin-ble-wss-bridge.ts
        离线时显示「需要网络」（v1.0 不支持续传）
```

## Shearwater Classic Bluetooth 路线

Petrel / Petrel 2 这类老设备不能假设一定能走 BLE GATT。2026-06-11
实测上传的 Petrel 样本：

- 设备：`Petrel`
- 地址：`00:13:43:9B:28:D4`
- Probe：`0.6.34`
- 发现来源：`ble+classic`
- 广播里可见 Shearwater UUID：`fe25c237-0ece-443c-b0aa-e02033e7029d`
- GATT 连接成功：`gattStatus=0`
- GATT 服务发现为空：`gattServices=[]`

结论：这台设备应按 **经典蓝牙日志上传助手** 路线处理，而不是要求小程序
直接转发 BLE 包。Android app 是这条路线的主入口。

目标架构：

```text
Shearwater Petrel classic Bluetooth
  → Android App BluetoothSocket / RFCOMM / SPP
  → 本地 dump 文件（可离线）
  → 上传到后端 staging / parse
  → 用户确认后进入 DivePlan 日志
```

可选实时架构：

```text
Shearwater Petrel classic Bluetooth
  → Android App 透明字节流转发
  → WSS sidecar / libdivecomputer worker
  → device.progress / device.dive / import staging
```

产品约束：

- 微信小程序不承诺经典蓝牙/RFCOMM 支持。
- Android 原生 app 负责配对、连接、重连、权限和本地缓存。
- v1 优先“抓 dump 后上传”，比实时 WSS 更容易恢复失败，也适合船上弱网。
- BLE 广播只用于识别设备；如果 GATT service 为空，不再卡在 BLE RDBI。

P3 最小实现：

1. 经典蓝牙发现和已配对设备列表分区显示。
2. 对 Shearwater Petrel 系列显示“经典蓝牙日志上传”入口。
3. 引导用户先在系统蓝牙里配对。
4. 建立 RFCOMM/SPP 连接，记录 socket open/read/write 错误。
5. 将原始会话 dump 保存到 `filesDir/import-dumps/<device>/<time>.bin`。
6. 上传 dump 到后端 staging；后端使用 libdivecomputer / worker 解析。
7. 失败时保留 dump 和日志，允许用户重新上传。

## 鉴权

不走 Bearer JWT，走 **API Key**（独立 token 域）：

- 用户在小程序 / web 端 `POST /api/me/ble-probe-bind-codes` 生成 6 位码 + 二维码 URL
- Android 端扫码 / 输入码 → `POST /api/ble-probe/bind-codes/consume` → 返回 `ApiKey`
- 所有后续请求带 `X-Api-Key: dpk_xxx` header
- 本地 ApiKey 存 `EncryptedSharedPreferences`

## 构建

```powershell
cd S:\GMP\dive-importers\android
.\gradlew.bat assembleDebug
```

APK 输出：`app\build\outputs\apk\debug\app-debug.apk`

## 相关仓库

- `gas-dive-server` —— ECS 后端，含 `BleProbeBindController` / `BleProbeCapturesController`
- `gas-dive-server/tools/device-import/android-ble-probe` —— 早期 BLE 嗅探调试工具（保留，不要混用）
- `garmin-sidecar` —— Garmin 协议 C 核心 + WSS bridge（clean-room 实现，严禁 copy Gadgetbridge）
- `gas-dive-plan/shared/utils/garmin-ble-wss-bridge.ts` —— 小程序版 Garmin BLE WSS 客户端，本 app P5 port 到 Kotlin

## IP / 法务边界

- ❌ **严禁** import `GMP/Gadgetbridge/` 任何源码到本工程（AGPL-3.0）
- ✅ 复用 `gas-dive-plan/shared/utils/garmin-ble-wss-bridge.ts` 的协议消息格式（DivePlan 自研）
- ✅ 通过观察行为重写实现（参见 `garmin-sidecar/CLEAN_ROOM.md`）
