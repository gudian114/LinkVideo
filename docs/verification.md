# v0.24 验证与交付

日期：2026-09-21。独立版本文件夹；保留旧版，未上传 GitHub。

## 功能

- 公告新增免费开源防骗说明、个人使用责任说明，并保留抖音网页认证提示。
- 公告自定义内容区固定为 256dp 高；正文放入独立滚动区域，新增文字不继续撑大外框。
- “不再显示”复选框和无背景“关闭”按钮位于同一横行，均保留至少 48dp 高的触控区域。
- 启动隐藏偏好、设置页公告入口、旋转状态恢复与销毁清理逻辑未改。

## 自动验证

- 公告资源契约先在旧布局上失败，再在新布局上通过，覆盖固定高度、可滚动正文、同行操作、无背景关闭和完整三段正文。
- 186 项独立 JUnit 核心测试及 5 项生产抖音网页脚本 Node 测试通过。
- Android 资源处理、42 个生产 Java 类编译、lintDebug、assembleDebug 完成，最终构建退出码 0；Lint：No issues found。
- 工作区使用 ECJ 编译生产 Java 后由 Gradle 打包；未运行 Gradle testDebugUnitTest。独立测试不等同 Android 界面验证。
- APK 包含 233 个 DEX 类定义，所有生产类及公告、批量、设置、视频预览和快捷滚动资源存在。
- VideoParser、AndroidHttpClient、DouyinBrowserPage、BatchActivity、BatchVideoCard 和 AnnouncementDialog 与 v0.23 逐文件 SHA-256 一致。

## APK

- 包名 com.linkvideo.parser；versionCode 24；versionName 0.24.0；minSdk26，targetSdk35。
- 大小 152264 字节；SHA-256 F625CD375421D6046E3B2FDF24940D16C82397029A1FD7C415950A0526C2E6E8。
- v2 签名通过；继续使用证书 SHA-256 7c23aa3891278ed66bcd46f21a1db1b5c51b88082d4e81743d843ed0868a2ad4。

## 手机验证边界

没有连接 Android 真机或模拟器。仍需手机检查：

1. 首次启动公告正文可上下滑动，底部“不再显示”和“关闭”始终同一行并可点击。
2. 勾选“不再显示”后关闭，重启不自动显示；从设置打开公告、取消勾选并关闭后恢复开屏显示。
3. 横屏、大字体和系统显示缩放下，标题与底部操作不被正文遮挡；最末尾文字能滚动阅读。
4. 旋转时公告可见状态及尚未提交的勾选状态恢复。

资源、构建与签名检查不能代替上述真机触控和视觉证据。

## 旧版保护

v0.23 文件与上次交付校验一致：

- APK：952582A2A49918695D918327FF71926746C6D07A226C652F018446B97F6A4161。
- 源码 ZIP：BEE81E2DA4A05AC7E8C8A672B3D6A1EDEDE7BD60620EE58EF75C1816C63A6C99。

归档和逐文件 SHA-256 核对记录追加于本版文件夹 verification.md。
