# 第一阶段实施计划

**目标：** 提供可构建 Android 项目，实现单条公开分享链接的真实解析尝试。
**技术：** Java 17 语法目标、Android SDK 35、AGP 8.9.2、Gradle 8.11.1；生产依赖使用 Android 内置 org.json。
**设计：** [design.md](design.md)

1. 链接与解析核心 → 验证：独立 JVM 测试文案/域名/多链接/平台 JSON/媒体类型/失败分支；先写测试并观察缺失功能失败，再实现。
2. 原生页面与项目配置 → 验证：编译应用、静态检查生命周期与异步状态，输入变化立即清空旧结果。
3. 联网探测和 APK → 验证：测试输出与网络探测分别记录，Gradle assembleDebug 和 lintDebug；导出源码 ZIP 和构建成功时的 APK。

文件范围：app/src/main/java/com/linkvideo/parser 下的链接、HTTP、解析、结果模型和 Activity；app/src/test 的测试；Android Manifest/资源、Gradle 配置、README与验证记录。
