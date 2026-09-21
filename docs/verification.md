# v0.25 自动验证摘要

详细证据见发布目录中的 `v0.25/verification.md`。

- 238 项独立 JUnit 测试及 5 项生产抖音网页脚本测试通过。
- Android 资源处理、生产 Java ECJ 编译、`lintDebug`、`assembleDebug` 完成，退出码均为 0。
- APK：`com.linkvideo.parser`，versionCode 25，versionName 0.25.0，minSdk 26，targetSdk 35。
- APK：267735 字节，SHA-256 `ec8c4d9a047a8defee6abbe34cd2c57af7b1a0de74557e3a6411833dd8552e81`。
- v2 签名证书 SHA-256 与 v0.24 一致：`7c23aa3891278ed66bcd46f21a1db1b5c51b88082d4e81743d843ed0868a2ad4`。
- 自动化没有替代 Android 8–15 真机的未知应用授权、系统安装确认、取消安装、安装失败、旋转和进程回收验证。
