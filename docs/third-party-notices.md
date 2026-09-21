# 第三方实现说明

`DouyinRequestSignature.java` 是抖音网页请求签名算法的 Java 适配，参考以下公开实现，进行了语言移植，并用独立固定向量与真实链接验证：

- Evil0ctal/Douyin_TikTok_Download_API：https://github.com/Evil0ctal/Douyin_TikTok_Download_API（Apache-2.0）。
- mubaiqq/dyjx 的 `douyin_abogus.py`：https://github.com/mubaiqq/dyjx/blob/main/douyin_abogus.py，其文件说明标注源实现为上述 Apache-2.0 项目，并致谢 JoeanAmier/TikTokDownloader。

参考实现的 Apache-2.0 许可证全文见 `licenses/Apache-2.0.txt`。

本项目在手机本地生成请求签名和解析公开作品，不调用上述项目的在线解析服务。
