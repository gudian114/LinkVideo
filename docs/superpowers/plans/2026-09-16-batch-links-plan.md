# Batch Links Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement the tasks sequentially in the current workspace. No subagents or GitHub upload are authorized for this task.

**Goal:** 实现 10 项批量输入、逐项解析、视频批量下载和用户指定的主页/设置调整。

**Architecture:** BatchState 管理输入及解析结果；BatchActivity 管理原生 Android 控件和串行任务；BatchLinkInput 拦截退格而保留文字。复用已有解析器、下载器和配色。

**Tech Stack:** Java、Android SDK 35、原生 Activity/LinearLayout/ExecutorService、SharedPreferences、独立 JUnit。

**Spec:** ../specs/2026-09-16-batch-links-design.md

## Global Constraints

- 最多 10 项，初始 1 项。
- 回车、换行和退格只新增/切换下一框，不删除当前链接；仅限视频，图文弹出用户指定文字并跳过。
- 按钮文字不包含用户引号外的沟通内容。
- 版本 14 / 0.14.0，独立 v0.14 归档。
- 不上传 GitHub，保留旧版本。
- 不引入第三方解析服务或新的构建依赖。

## Task 1: 批量状态与队列筛选

Files: 新增 app/src/main/java/com/linkvideo/parser/BatchState.java、app/src/test/java/com/linkvideo/parser/BatchStateTest.java；修改 ParsedResult.java、VideoResult.java、MediaStream.java 以支持状态序列化。

Interfaces: size()、entry(int)、next(int)、parseTargets()、videoTargets()、clearInputs()；Entry 保存 input 和 result。

- [x] 写初始/上限与复用行为测试：连续 next(last) 后 size 不超过 10，next(0) 复用第 2 项。
- [x] 写空项筛选、清空输入保留结果、视频筛选与 ObjectOutputStream 状态回读测试。
- [x] 运行 work/run-core-tests.ps1，观察缺失 BatchState 的预期失败。
- [x] 实现最小状态类及结果序列化，运行核心测试通过。

## Task 2: 主页头部与设置

Files: 修改 activity_main.xml、MainActivity.java、SettingsActivity.java、activity_settings.xml、strings.xml；新增 drawable/ic_settings.xml。

Interfaces: SettingsActivity.batchButtonEnabled(Context,String)；BATCH_PARSE、BATCH_REPARSE、BATCH_CLEAR 键，默认 true。

- [x] 主页外层改为垂直布局，固定白色顶部水印栏，齿轮位于右端，原内容在滚动区。
- [x] 加入批量入口，复用原配色。
- [x] 设置保留原图文切换项，下方追加三个开关，设置内容放在滚动区，选择持久保存。

## Task 3: 批量页面

Files: 新增 BatchActivity.java、BatchLinkInput.java、activity_batch.xml、view_batch_entry.xml；注册 Activity，更新构建脚本的 Android 类排除与版本输出。

Consumes: Task 1 状态、Task 2 开关、VideoParser.parse(String,ProgressListener)、VideoDownloader.download(Context,VideoResult,Progress)。

- [x] 动态添加输入项，回车/IME Next/单字符换行/退格复用或创建下一项；软键盘删除接口与按键事件均拦截退格，不删除已有文字；多字符分享粘贴不拆分。
- [x] 按启用数排布每项三个操作：三个为 1+2，两个为 1+1，一个为 1，零个隐藏。
- [x] 个别/全部解析串行执行，逐项显示阶段进度、摘要和错误；失败继续其余项。
- [x] 检测图文时显示“此操作仅限与视频批量解析”弹窗和行内提示，不替换原视频结果，继续其他视频。
- [x] 全部下载筛选视频，复用存储权限及下载进度，每项记录保存位置；失败继续其余项。
- [x] 一键清空只清空全部链接；保存/恢复 BatchState，退出取消队列。

## Task 4: 验证与归档

- [x] 运行 work/build-linkvideo-debug.ps1，核对核心测试、资源编译、Java 编译、Lint、DEX 和 APK 构建结果。
- [x] 核对 APK 版本、v2 签名、设置图标、批量布局及 Activity 注册。
- [x] 比较旧版本哈希，创建干净源码归档，核对 ZIP 内容与当前源码一致。
- [x] 保存 v0.14 README、更新说明、progress 与 verification；报告设备验证边界。


