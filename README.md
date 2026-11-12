# Alien Input Android

阶段版本 **0.2.0-alpha.1**：安卓 MVP 功能冻结，进入稳定性试用。见 [阶段范围](docs/checkpoint.md) 与 [从源码构建](docs/build-from-source.md)。

当前开发版是基于 Trime v3.3.13 / Rime 的 Android 输入法，应用名“外星同文·测试”。Android 8.0+，当前构建为 arm64 调试版。早期独立 Java 字根原型保留在 `app/`，旧说明见 `docs/legacy-prototype.md`。

## 使用

1. 启用并选择“外星同文·测试”。现成四叶草九宫格（rime-pure）为初始方案，也可在键盘左侧 `… → 方案` 切换全拼或小鹤双拼；应用首页“方案”管理启用列表。
2. 普通输入直接由 Rime 完成。点原工具区“外星”展开外星面板；点“收起”恢复普通输入，同一输入会话保留已组合的草稿。
3. 输入并确认中文词（如“你”“食物”），再点对应外星候选，每次选择依次加入同一图片稿。英文查询用英文模式输入。点草稿项可替换，长按可删除。
4. 点“复制图”复制整张组合 PNG，随后在支持图片粘贴的输入框粘贴。图片剪贴板的接收能力取决于目标应用。微信里隐藏不兼容的直接插图按钮，不自动调用分享流程。
5. 图片固定每行最多四个字形，单行 480×208，各行居中，词组尽量不拆分并均衡换行；配色在“设置 → 虚拟键盘 → 外星图片配色”，默认米黄字／黑底。九宫格可直接点“符”“123”“En”“编辑”使用对应页面。
6. 数字请切换键盘数字页后直接输入，例如 1234；按十进制数值转为七进制数词，目前支持最多 18 位整数。
7. “编码”复制可还原的字形序列；“预览”显示组合图。“插入图”仅向支持的编辑器提交 PNG；“分享”只有用户明确点选时才打开分享面板。

当前实现逐词检索与组合，不是句子翻译。2264 条字形及词组、2749 条检索项按释义、别名、相关概念等来源区分；所有查询在设备本地完成。字形仍是图片，无法作为普通 Unicode 字符在任意应用显示。

## 源码与环境

- `semantic-core/`：独立 Java 词义检索、草稿和 PNG 编码模块。
- `android-integration/`：内部查询缓冲、候选面板、图片与剪贴板输出。
- `lexicon/`：可审计的导入数据和人工补充词表。
- `rime/`：输入方案选择与兼容补丁；`vendor/rime-pure/`：现成九宫格主题、四叶草词库与语言模型。
- `trime/`：固定提交的 Trime 派生宿主，接入脚本为 `tools/integrate_trime.py`。
- `docs/implementation-notes.md`：构建锁定、验证证据、尚未验证的范围。
- `docs/game-resource-analysis.md`：游戏版本、原始字形对照、渲染与配色解析；研究索引和复现脚本位于 `research/game/`、`tools/research/`。

```sh
source /Users/jie/project/dev-env/android-env.sh
cd ~/project/alien-input-android
python3 tools/bootstrap.py
bash tools/test-semantic.sh
bash tools/build-trime.sh
```

环境统一放在 `~/project/dev-env`：JDK 17、Gradle 9.8.1、Android Platform 37、Build Tools 37.0.0。开发构建采用同一官方 Trime 发布的预编译 Rime 原生库，下载摘要和源码提交固定；不需要在 macOS 编译 Linux 原生工具链。

PNG 的 `AlienInput` tEXt 块保存字形与词组边界，例如 `ALIEN1|v6.12.4|F0835;F1047`，可用 `tools/read-png-message.sh` 读回。聊天应用压缩或截图可能丢失元数据；文字编码可单独保留。目前未做手机端图片导入恢复或像素识别。

## 验证与许可

本版通过 9787 项语义、53009 项词组排版、14481 项数字检查与 PNG 往返检查；全部 1344 个统一字形已校验。Android 16 的 1575 项独立检查和真实数字键盘／预览操作通过，最终 APK 已安装。详见 `docs/four-slot-numbers-update.md`；此前九宫格接入记录保留在 `docs/clover-five-column-update.md`。

Trime 派生宿主及整体发行遵循 GPL-3.0-or-later，独立新增模块沿用 MIT；不能把整个 Trime 派生版标为 MIT。游戏字形与内容的权利仍归原权利人，见 `THIRD_PARTY_NOTICES.md`。这是社区工具，不是游戏官方输入法。

现有字库完整，但游戏原图全量映射尚未核实。本版对完整 v6.12.4 字库统一透明度与抗锯齿处理，不混用局部游戏原图；构建沿用共享 dev-env 中已有 Pillow 环境。
