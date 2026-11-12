# 开发与发布约定

- `main` 始终保留可用、可构建的代码；不设长期 develop 分支。
- 新工作使用 `fix/问题名`、`feat/功能名` 或 `docs/主题` 分支，以小 PR 合并，避免同时改输入法基座、语义映射和图像输出。
- 提交前运行 `bash tools/test-semantic.sh`。GitHub 在 push 与 PR 时自动运行这组不依赖 Android SDK 的检查。
- Android 界面与图片输出改动需另外构建 APK，并按影响范围做实机验证。CI 通过不能代替触屏验证。
- 用 Issue 记录可复现的缺陷与新需求；说明使用场景、预期、实际表现和版本。不要上传聊天记录、私人词库、Token 或签名密钥。
- 每个发布版本绑定一个不可移动的标签、APK、SHA256 和发布说明。alpha 期间先处理丢稿、输入错误和输出错误，再增加功能。
- 仓库版本位于 VERSION，并与 APK versionName、接入脚本和宿主补丁同步；versionCode 递增以支持升级。首个发布版为 v0.2.0-alpha.2；v0.2.0-alpha.1 保留为此前源码快照。Trime 基座仍固定 v3.3.13。
- 发布流程：完成本地检查 → 合并 main → 等待 CI 成功 → 创建标签 → 发布预发布版和验证过的 APK。签名材料只留在仓库外。

后续优先级及明确暂缓的范围见 docs/checkpoint.md。
