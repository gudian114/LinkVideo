# v0.29 自动验证摘要

日期：2026-10-09。以 v0.28 为功能基线，在独立 v0.29 目录交付；历史版本未覆盖。

- 274 项独立 JUnit 测试和 13 项生产抖音网页脚本测试通过。
- 新增合约测试确认公告包含重装后首次验证说明，仓库 README 标题为“视频解析（Android）”。
- Android 资源处理、生产 Java ECJ 编译、`lintDebug` 和 `assembleDebug` 完成，退出码均为 0。
- APK：`com.linkvideo.parser`，versionCode 29，versionName 0.29.0，minSdk 26，targetSdk 35。
- APK：187636 字节，SHA-256 `3037bdea688618d9c837a48c305ccb06eb8aab9027066c165c283b1465d0a2cb`。
- APK 内核对 315 个 DEX 类定义、下载类、OTA 类和必要资源。
- APK v2 签名证书 SHA-256 与 v0.28 一致：`7c23aa3891278ed66bcd46f21a1db1b5c51b88082d4e81743d843ed0868a2ad4`。

未连接 Android 真机或模拟器。公告实际滑动布局、重装后首条抖音验证、后续身份复用、覆盖安装和 OTA 系统安装流程仍需真机验证。
