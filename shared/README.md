# Dive import shared rules

共享的潜水电脑识别规则。目标是让 Android、Apple、Windows 三端读取同一份
`device-rules.json`，避免三端各自 hardcode vendor / product 表。

当前 Windows importer 已直接使用这份 JSON。Android 和 Apple 现有实现仍有
hardcode 规则，下一步可以把这份文件复制进各自资源目录并改为启动时加载。

字段说明：

- `serviceUuidHints`: BLE 广播 service UUID 到 vendor 的强匹配。
- `deviceNamePatterns`: 设备名正则 fallback，顺序有意义，更具体的型号要放前面。
- `ambiguous`: 设备名可能对应多个型号，UI 应提示用户确认。
- `weak`: 只识别到系列或需要特殊路径，例如 Garmin 走 Sidecar。
- `hint`: UI 可展示的说明。
