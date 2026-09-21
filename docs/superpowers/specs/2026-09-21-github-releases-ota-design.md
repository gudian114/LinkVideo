# v0.25 GitHub Releases 应用内更新设计

## 目标与边界

- 以 v0.24 源码为功能基线，新建独立 v0.25 版本，使用 `versionCode 25` 与 `versionName 0.25.0`，不修改或覆盖 v0.24 及其他历史版本。
- 保持原生 Java + XML 技术栈，不调整现有解析、预览、下载、图文和批量功能。
- GitHub 仓库由用户确认是 `https://github.com/gudian114/LinkVideo`。应用固定从 `https://github.com/gudian114/LinkVideo/releases/latest/download/latest.json` 获取更新清单，不保存或使用 GitHub Token。
- 更新必须由用户主动检查、下载并在 Android 系统安装确认界面中决定是否安装；不做静默安装，不宣称普通应用可以自动回滚。
- 使用 v0.24 当前构建采用的同一签名方式和证书生成 v0.25 APK。密钥、密码、Token、`local.properties` 和构建缓存不得进入源码 ZIP 或 GitHub Release 交付目录。

## 用户流程与界面状态

设置页增加“检查更新”入口，打开独立的更新页面。更新页面按以下状态展示：

1. **检查中**：请求固定 `latest.json` 地址，可取消页面，不重复发起请求。
2. **已是最新版**：远端 `versionCode` 小于或等于当前版本时显示当前版本和“已是最新版”。
3. **发现新版本**：显示版本号、更新说明和安装包大小，并提供“下载更新”。`sizeBytes` 为 0 时界面显示“大小未知”，正式交付生成的 `latest.json` 必须写入真实大小。
4. **下载中**：显示已下载字节、总大小和百分比，提供“取消下载”。取消、网络失败或中断时删除 `.part` 临时文件，显示原因和“重试”。
5. **校验中**：下载完成后依次进行文件大小、SHA-256、APK 元数据和签名校验。任何一步失败都禁止安装并删除无效 APK。
6. **等待安装权限**：Android 8.0 及以上如果不能安装未知来源应用，跳转系统授权页。拒绝后明确提示并保留已校验 APK，允许用户稍后重试安装。
7. **系统安装**：通过 `PackageInstaller.Session` 写入已校验 APK并拉起 Android 系统安装确认界面。安装成功、用户取消及安装失败分别显示明确结果；当前版本在系统确认安装成功前不发生改变。

更新页面保存当前清单、下载文件位置和校验状态，以覆盖旋转及从“安装未知应用”设置页返回的场景。恢复时必须重新确认文件存在并重新执行安全校验，不能仅信任保存的状态字段。

## 模块划分

### 纯 Java 核心

- `UpdateManifest`：解析及保存清单字段。
- `UpdateManifestParser`：严格解析 JSON，验证必填字段、数据类型、`schemaVersion == 1`、HTTPS 地址和 SHA-256 格式。
- `UpdatePolicy`：判断最新版、设备最低系统要求、清单包名和版本关系。
- `Sha256Verifier`：以流式读取方式计算 SHA-256，避免把 APK 全部读入内存。
- `DownloadTransfer`：处理输入流到 `.part` 文件的复制、进度、取消、大小上限和中断清理，便于无 Android 环境测试。
- `SignerIdentity` 与 `ApkValidationPolicy`：比较证书摘要集合，并校验清单与 APK 的包名、版本号、版本名、最低系统版本和文件大小是否一致。
- `InstallOutcome`：把安装器状态映射为成功、用户取消、权限拒绝或具体失败提示。

这些类不得依赖 Activity、View 或 Android 网络 API，以便由现有 JUnit 流程直接测试。

### Android 集成层

- `UpdateActivity`：控制检查、下载、校验、权限和安装状态，只负责界面及任务调度。
- `UpdateHttpClient`：基于 `HttpURLConnection` 请求清单和 APK；只接受 HTTPS，限制重定向次数，拒绝降级到 HTTP，设置连接/读取超时和清单大小上限。
- `ApkArchiveInspector`：通过 `PackageManager.getPackageArchiveInfo` 读取候选 APK。API 28 及以上使用 `GET_SIGNING_CERTIFICATES`，API 26–27 使用兼容签名 API；同时读取当前已安装应用的证书用于比较。
- `UpdateInstaller`：使用 `PackageInstaller.Session` 提交 APK。
- `UpdateInstallReceiver`：接收 `STATUS_PENDING_USER_ACTION`、`STATUS_SUCCESS`、`STATUS_FAILURE_ABORTED` 和其他失败状态。仅在系统要求用户确认时拉起系统安装界面，失败结果写入应用私有状态并回到更新页面显示。

更新 APK 下载到应用私有缓存目录 `cache/updates/`。安装分享路径仅通过范围限定到该目录的 `FileProvider` 或安装会话流暴露，不申请公共存储权限。

## `latest.json` 契约

正式清单包含以下字段：

```json
{
  "schemaVersion": 1,
  "packageName": "com.linkvideo.parser",
  "versionCode": 25,
  "versionName": "0.25.0",
  "minSdk": 26,
  "apkUrl": "https://github.com/gudian114/LinkVideo/releases/download/v0.25.0/LinkVideo-v0.25.apk",
  "sizeBytes": 123456,
  "sha256": "64位小写十六进制摘要",
  "releaseNotes": "更新说明"
}
```

解析规则：

- 缺少字段、类型错误、未知 `schemaVersion`、非 HTTPS URL、空版本名、非正 `versionCode`、负数 `sizeBytes` 或非 64 位十六进制 SHA-256 都视为无效清单。
- `packageName` 必须等于 `com.linkvideo.parser`。
- `minSdk` 高于设备系统版本时显示“不支持当前设备”，不允许下载或安装。
- 正式发布生成的 `sizeBytes` 必须大于 0；解析器兼容值 0，仅用于显示“大小未知”，但安装前仍根据真实下载文件、SHA-256 和 APK 元数据完成验证。
- APK 的包名、版本号、版本名和最低系统版本必须与清单一致；APK `versionCode` 还必须严格高于当前安装版本。

## 下载与安全校验顺序

1. 清单地址与所有重定向目标必须是 HTTPS。
2. 校验清单结构和适用性，再显示可下载版本。
3. APK 保存为 `.part`；下载完成后，如果 `sizeBytes > 0`，先校验文件大小。
4. 计算文件 SHA-256，并使用常量时间摘要比较；不匹配即删除文件并禁止安装。
5. 使用 Android `PackageManager` 解析 APK，验证包名、版本号、版本名和最低系统版本。
6. 读取当前应用与候选 APK 的活动签名证书，计算 SHA-256 摘要并比较证书集合。v0.25 不引入签名轮换，因此要求活动签名集合完全一致；不一致即禁止安装。
7. 全部通过后把 `.part` 原子移动为最终 APK，并允许进入权限与安装流程。安装前再次确认文件仍存在且哈希未变化。

下载设置合理的连接及读取超时、最大清单大小和最大 APK 大小，防止无限等待或异常占用存储。取消通过线程中断与取消标志共同生效，完成后关闭连接和流。

## 权限与系统安装

- Manifest 增加 `android.permission.REQUEST_INSTALL_PACKAGES`。
- API 26 及以上调用 `PackageManager.canRequestPackageInstalls()`；没有权限时用 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` 打开本应用的系统授权页。
- 用户拒绝权限时提示“未允许安装未知应用，当前版本未改变”，不自动反复弹出系统页面。
- `PackageInstaller.Session` 使用完整安装模式并写入已验证 APK。提交后由系统提供确认界面，应用不自动点击或绕过确认。
- 安装器返回 `STATUS_FAILURE_ABORTED` 时提示“安装已取消，当前版本未改变”；其他失败显示系统状态说明；成功显示“更新安装成功”。
- 如果进程被系统回收，安装结果由显式接收器保存；下次打开更新页面时显示最近结果。

## 测试设计

### 自动化测试

- 已是最新版与发现新版本。
- `latest.json` 缺少字段、类型错误、格式错误、未知 schema、非 HTTPS 地址和非法哈希。
- 模拟网络读取失败、下载中断和取消，确认临时文件清理及可重试状态。
- SHA-256 匹配与不匹配。
- 包名不匹配、清单与 APK 版本不一致、APK 版本号没有提高、最低系统版本不兼容。
- 签名摘要集合匹配与不匹配。
- 未知来源权限拒绝、用户取消安装及其他安装器状态的结果映射。
- 现有核心 JUnit 和浏览器脚本回归。
- Android 资源处理、Java 编译、Lint、APK 构建与签名检查。

### 构建产物检查

- 使用 Android 构建工具读取最终 APK，确认包名、`versionCode`、`versionName` 和最低系统版本。
- 使用 `apksigner` 验证 APK 完整签名，并将证书摘要与 v0.24 APK 比较。
- 重新计算交付 APK 的文件大小和 SHA-256，确认与 `latest.json` 逐字一致。
- 解压源码 ZIP 检查，不得包含 keystore、密码文件、Token、`local.properties`、`.gradle`、`build`、APK 或其他生成缓存。

### 必须保留给真机验证的项目

- 设置页入口、下载进度、取消和失败重试的实际交互。
- Android 8 及以上未知来源授权允许和拒绝。
- 系统安装确认、用户取消、安装失败和成功覆盖升级。
- 应用进程被回收后的安装结果恢复。
- GitHub Release 发布后固定 `latest/download/latest.json` 地址及 APK 重定向在真实网络中的表现。

自动测试或构建成功不能表述为已完成真机安装验证。

## 交付目录

新建 `v0.25/`，其中保留可审查的版本源码和验证记录。可上传 Release 的目录至少包含：

- `LinkVideo-v0.25.apk`：使用与 v0.24 相同证书签名的 APK。
- `latest.json`：填写真实大小、SHA-256、版本号、APK URL 和更新说明。
- `LinkVideo-v0.25-source.zip`：不含密钥与缓存的源码。
- `SHA256SUMS.txt`：APK、清单和源码 ZIP 的 SHA-256。
- `RELEASE_NOTES.md`：发布说明。
- `DEVICE_TEST_CHECKLIST.md`：真机验证清单和未验证状态。
- `verification.md`：自动测试、编译、Lint、构建、元数据及签名证据。

不创建或发布 GitHub Release，不上传任何文件。

## 后续发布流程

1. 从最新版本源码复制到新的独立版本目录并提高 `versionCode`/`versionName`。
2. 使用同一签名证书构建签名 APK。
3. 运行完整测试、Lint、构建、APK 元数据和签名检查。
4. 计算 APK 大小和 SHA-256，写入新的 `latest.json`，并再次执行一致性校验。
5. 生成不含敏感信息和缓存的源码 ZIP及校验文件。
6. 在 GitHub 创建草稿 Release，标签与 `apkUrl` 中的标签一致，同时上传 APK 和 `latest.json`。
7. 在草稿状态核对附件名、哈希和发布说明；发布后验证 `releases/latest/download/latest.json` 和 APK 地址，再用真机完成更新测试。

## 依据

- GitHub 官方文档允许用 `/releases/latest/download/<asset-name>` 链接到最新 Release 中同名资源：https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases
- Android `PackageManager` 提供未知来源安装权限检查及 APK 包信息读取：https://developer.android.com/reference/android/content/pm/PackageManager
- Android `PackageInfo`/`SigningInfo` 提供长版本号与签名信息：https://developer.android.com/reference/android/content/pm/PackageInfo 和 https://developer.android.com/reference/android/content/pm/SigningInfo
- Android `PackageInstaller` 提供会话提交、用户确认和安装结果状态：https://developer.android.com/reference/android/content/pm/PackageInstaller

