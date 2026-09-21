# v0.15 批量解析输入与取消修正

保留 v0.14 的视频批量解析功能：主页平台名称下方的蓝色“批量解析”入口、初始一个输入框、最多十个输入框、三个可切换的单项按钮，以及“一键解析”“一键下载”“一键清空”。图文作品显示“此操作仅限与视频批量解析”，不进入图片页，继续处理其他链接。详细批量功能见 feature-v0.14.md。

## 输入处理

回车、单次换行和退格打开或切换下一框，保留当前框全部文字。原有向后删除接口与键盘按键拦截继续使用；新增文字变化兜底，处理输入法通过 composing 更新或空 commit 表达的删除。

正常候选转换不触发下一框。复制的一段多行分享文案保留在当前框；选区粘贴较短链接正常替换，剪切和清空按钮也可修改输入。Android API 33 的带 TextAttribute 编辑调用、API 34 的显式范围 replaceText 均在实际替换前捕获选区，结束后恢复保护状态。新类型放在 API 33 类中，Android 8 起的旧接口路径不引用该类型。

输入变化不启动解析，清空或编辑输入不清除已有成功结果。下一次成功解析视频才替换对应结果。设备键盘具体表现仍需实测，不能由纯 Java 策略测试代替。

## 取消处理

HTTP 请求开始、重定向之间、响应读取前后以及解析阶段回调检查线程中断；平台网络后备请求不吞掉取消异常或 InterruptedException。所有已建立的页面连接在退出请求时断开。

这是协作取消：已阻塞的 HttpURLConnection 操作并非立刻中止，沿用 10 秒连接超时与 15 秒读取超时；操作返回或超时后，不再继续后备接口或下一批量项。退出页面后不更新旧页面。

## 依据和验证

输入法删除流程的依据为 [AOSP LatinIME InputLogic](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/7f58115a861d1c7a926b8f2eb8612c02b388456a/java/src/com/android/inputmethod/latin/inputlogic/InputLogic.java) 的 handleBackspace；显式替换范围语义见 [Android InputConnection.replaceText](https://developer.android.com/reference/android/view/inputmethod/InputConnection#replaceText(int,int,java.lang.CharSequence,int,android.view.inputmethod.TextAttribute))。这些来源说明接口行为，不代表已在用户手机复现。

新增六项输入策略测试与七项取消回归，独立 JUnit 共 124 项。七项取消测试在旧实现失败后通过；输入策略新增接口之前编译失败，实现后通过。最终编译、Lint、APK 签名与归档结果见 verification.md。本版本未进行真机、模拟器和平台链接实测。
