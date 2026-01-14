# Android BLE SDK Demo 工程总结与说明书

## 1. 工程概述

本工程是基于 Veepoo 蓝牙 SDK 开发的 Android 演示应用。项目已从传统的多个 Activity 结构重构为现代化的 **单 Activity + Fragment** 架构，旨在提供更流畅的交互体验和更清晰的代码组织结构。

## 2. 核心架构设计

### 2.1 单 Activity 模式

- **宿主**: `MainActivity`
- **导航**: 使用 `BottomNavigationView` 实现底部导航。
- **容器**: `R.id.fragment_container` 用于承载各功能 Fragment。

### 2.2 功能模块划分

项目分为四个核心业务模块：

1. **设备连接 (`ConnectFragment`)**: 负责蓝牙扫描、设备绑定、密码验证及连接状态管理。
2. **健康数据 (`HealthDataFragment`)**: 实时同步并展示步数、睡眠、心率、血氧、血压、血糖等生理指标。
3. **自觉提醒 (`ReminderFragment`)**: 实现互斥的震动提醒模式（读书、吃药、出行、洗手），支持复杂的指令下发逻辑。
4. **通用设置 (`SettingsFragment`)**: 包含常灭屏开关、表盘切换、设备复位及恢复出厂设置等功能。

### 2.3 关键技术实现

- **状态共享**: 采用 `SharedPreferences` (文件名: `ble_prefs`) 在不同 Fragment 之间共享已连接设备的 MAC 地址。
- **BLE 指令顺序控制**: 在提醒设置中，通过 `Handler.postDelayed` 实现指令的顺序下发（先关闭所有提醒，再开启目标提醒），确保设备响应的稳定性。
- **UI 响应式更新**: 统一使用 `runOnUiThread` 处理 SDK 回调，确保 UI 更新在主线程执行，避免崩溃。

## 3. 核心功能详细说明

### 3.1 设备连接逻辑

- 扫描设备并获取 MAC 地址。
- 调用 `confirmDevicePwd` 进行密码验证（默认 0000）。
- 验证成功后，持久化 MAC 地址并触发各 Fragment 的数据读取。

### 3.2 健康数据同步 (符合 task3.md)

- **同步机制**: 顶部“同步数据”按钮触发全量健康数据读取。
- **数据展示**: 严格遵循“无数据不展示”原则，避免显示 0 或无效占位符。
- **实时性**: 监听 SDK 回调，实时更新心率等动态指标。

### 3.3 自觉提醒设置 (符合 task5.md)

- **互斥模式**:
  - 单次柔和 (读书)
  - 单次强度 (吃药)
  - 双次柔和 (读书 + 出行)
  - 一柔一强 (读书 + 洗手)
  - 双次强度 (吃药 + 洗手)
- **安全机制**: 开启新模式前自动清空旧模式，防止提醒冲突。

## 4. 已修复的重大问题记录

| 问题类型         | 描述                                                | 修复方案                                         |
| :--------------- | :-------------------------------------------------- | :----------------------------------------------- |
| **编译错误**     | `ReminderFragment` 中 `switch_reading` 等 ID 找不到 | 重写 Fragment 逻辑并对齐布局文件 ID。            |
| **SDK 调用错误** | `confirmDevicePwd` 参数缺失                         | 补全所有必需的监听器参数。                       |
| **API 不匹配**   | `isBluetoothEnabled` 方法不存在                     | 改为使用 `BluetoothUtils.isBluetoothEnabled()`。 |
| **状态丢失**     | 切换 Fragment 后失去连接引用                        | 引入 `SharedPreferences` 持久化 MAC 地址。       |

## 5. 开发与维护建议

- **SDK 更新**: 建议定期检查 Veepoo SDK 版本，确保协议兼容性。
- **权限处理**: 针对 Android 12+，需确保 `BLUETOOTH_SCAN` 和 `BLUETOOTH_CONNECT` 权限已动态申请。
- **日志调试**: 已集成 `Logger` 库，可通过 Logcat 过滤 `timaimee` 标签查看详细 BLE 通讯日志。

---

_文档版本: 1.0_
_最后更新: 2026-01-06_
