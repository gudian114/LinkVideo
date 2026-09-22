# v0.26 自动验证摘要

- 246 项独立 JUnit 测试和 5 项生产抖音网页脚本测试通过。
- Android 资源处理、生产 Java ECJ 编译、`lintDebug` 和 `assembleDebug` 完成，退出码均为 0。
- 公共下载目录：`Download/图文/`、`Download/视频/`、`Download/封面/`。
- 文件名从 `downloadfile` 开始，重名时使用 `downloadfile (1)`、`downloadfile (2)`，并保留媒体格式后缀。
- APK：`com.linkvideo.parser`，versionCode 26，versionName 0.26.0，minSdk 26，targetSdk 35。
- APK：299868 字节，SHA-256 `e3a779888d89700caa3858317c24aab3dbe0697c981bb53fff1c66d076aeba90`。
- v2 签名证书与 v0.25 一致；OTA 更新类和更新页面与 v0.25 逐文件一致。
- 未连接 Android 真机或模拟器；卸载后文件保留、MediaStore 重名处理和不同厂商文件管理器显示仍需真机验证。
