# 游戏本体字形与输出风格解析记录

记录日期：2026-11-12（Asia/Shanghai）。用途：为外星同文后续字形与图片排版开发提供可复查的证据。本次仅研究、留档，没有更换生产字形、修改 APK 或改动游戏文件。

## 版本与来源

- 游戏：Message From Aliens，Steam App ID 1858720，开发者 Artless Games。
- 本地 Steam build ID：26564542；Unity 版本字符串：6000.6.8f1。
- 数据目录：`/Users/jie/Library/Application Support/Steam/steamapps/common/Message From Aliens/mac.app/Contents/Resources/Data`。
- 当前输入法字形：`app/src/main/assets/glyphs/F0001.png` 至 `F1344.png`，来自 alien-input v6.12.4。目录还含两个独立的斜线符号，统计 F 字形时排除。
- 本次游戏文件 SHA256、大小及完整本地路径：`research/game/provenance.json`。后续游戏更新后先比较指纹，不复用未经核对的 path_id。
- 用户提供的游戏截图：`research/game/reference/user-game-screenshot.png`。颜色只是当前截图状态，用户明确说明游戏支持切换颜色。

## 已确认的资源结构

解析了 resources.assets 和 sharedassets0.assets 的资源索引。合计得到 1476 个 Texture2D、1459 个 Sprite 名称和 1540 个 TextAsset；数量包含 UI 等非字形素材，不等同于词典词条数。

resources.assets 中有 1451 张 100×100 纹理。可直接定位的示例：

| 游戏资源名 | Texture2D path_id | 对照的现有字形 |
| --- | ---: | --- |
| you | 780 | F0835（你） |
| food | 936 | F1047（食物） |
| big | 1308 | F0001（大） |
| eat | 850 | 后续逐项核对 |

前三项已经同色同倍率视觉核对。完整资源索引保存在 inventory.json。尚未完成全部 1344 个现有 ID 与游戏资源名之间的对应表，不能按资源序号或名字直接批量覆盖。

newalien、alieninfo、geometry 等 TextAsset 已定位并保存小范围原始研究副本，但内容是编码文本，尚未解码或解释。资源名本身不能当作游戏语言语义的完整定义。

## 字形质感差异：不是简单的分辨率差异

游戏原始纹理和当前输入法 PNG 都是 100×100。游戏资源通常是白色图形加透明度遮罩，由显示端再着色；现有 PNG 是黑色图形加透明度遮罩。

统一着色为同一种浅色、使用相同黑色背景和相同双线性放大倍率后，三个核对样例均表现为：现用素材笔画更细、边缘更虚；游戏原始纹理的实心笔画更充分，端点更圆润。参见 `research/game/reference/game-glyph-comparison.png`。

可复查的像素证据：

| 样例 | 当前非透明包围盒 | 游戏非透明包围盒 | 当前完全不透明像素数 | 游戏完全不透明像素数 |
| --- | --- | --- | ---: | ---: |
| 你 | (6,30)–(94,70) | (8,31)–(92,69) | 2 | 477 |
| 食物 | (6,34)–(94,66) | (8,34)–(92,66) | 0 | 507 |
| 大 | (6,6)–(94,94) | (8,8)–(92,92) | 0 | 404 |

包围盒采用 Pillow 的右、下边界不包含约定。完全不透明像素数只是一项诊断指标，不能单独充当清晰度评分；这里同时有同倍率视觉对照。

全量 alpha SHA256 比较没有发现逐像素完全一致的 F 字形。这仅说明现有素材经过了不同的处理或生成流程，不代表字义不对应，也不能据此推断处理原因。不可把任何最近似图形未经复核自动匹配为某个字形 ID。

## 本体中的渲染路径

从 Managed/Assembly-CSharp.dll 的元数据与针对性 IL 检查确认：

- `Draw.DrawACToSlot` 从 `Shapes2D Sprites/<资源名>` 加载 Sprite，找不到时使用 `notaword`，再放入既有字位。
- `Draw.DrawACSprite` 使用 `DefaultColor(0)` 给 Sprite 设置颜色。
- `Global.FrontColor`、`Global.BackColor` 从游戏颜色设置生成前景和背景颜色。`Draw.DefaultColor` 还派生透明、混色等界面用途颜色。
- 同时存在 `DrawPath1`、`DrawPolygon`、`DrawLine`、`DrawRect`、`DrawCircle`、`DrawDot` 等几何绘制方法。
- `Global.StringToCharacter` 与 `AlienInfo.GetCharacter` 支持结构到线段／点列表的处理；另外还有组合、缩放、翻转等相关方法。没有在本轮重写或完整验证这些组合规则。
- `Draw` 构造函数中 `default_linewidth` 与 `text_linewidth` 的初值均为 0.05；`DrawPath1` 中有 pathThickness=0.01。这些是游戏坐标／绘制参数，不是 Android 像素，也不保证等于场景最终运行值，不能直接用于手机画笔粗细。

结论：游戏同时有预生成 Sprite 和几何绘制能力，不能仅凭存在绘制方法就声称所有文字都是实时矢量渲染。眼下应先校准原始 Sprite 的遮罩、尺度和字位排版；是否实现矢量渲染留待清晰度需求与完整结构映射验证后决定。

## 颜色是独立配置

已从 FrontColor/BackColor 提取 9 组游戏预设，见 color-presets.json：white、black、red、red2、orange、yellow、yellowgreen、green、infrablue。

例如 `white` 分支的前景为 RGB(1,0.9,0.7)、背景为 RGB(0,0,0)；`black` 分支反过来。内部命名不应按字面误读为纯白／纯黑字体。值是 Unity 浮点颜色，实际截屏可能受颜色空间和屏幕链路影响。

后续输入法设计建议分开保存：

1. 字形身份与原始透明度遮罩。
2. 前景色、背景色及是否透明；可以提供游戏原生配色预设与用户自定义。
3. 字位、字间距、行距、页面留白及换行策略。
4. 微信等接收端适配，例如固定宽度／固定画布；不要为了适配缩略图去改变字形内部部件比例。

## 后续实施建议与尚未验证项

优先级建议：先对准原始字形的笔画和留白，再调整行列排版，最后处理消息图片在微信里的缩放。

- 先用“你、食物、大”等已人工核对的字形做小样；保持每个字形自身部件比例，不对每个内部部件单独拉伸。
- 完成可审计的“F 编号 → 游戏资源名”映射，再讨论替换全量素材。未知或有歧义的对应项保留现有资源，不静默猜测。
- 用游戏截图对照字位占比、行距及换行。当前尚未还原完整运行时相机、缩放、场景布局参数，不能声称排版已 1:1 复刻。
- 矢量化可作为后续高清输出路线；不应把 100×100 本身当成风格差异的唯一原因。
- 微信缩略图缩放属于另外一层问题。即使字形素材完全一致，也不会自动获得稳定的屏幕字号。

## 工具、复现与兼容性记录

共享环境：`/Users/jie/project/dev-env/venvs/game-assets`。完整依赖锁定：`research/game/requirements-lock.txt`。

核心版本：Python 3.9、UnityPy 1.20.26、Pillow 11.3.0、dnfile 0.18.0、dncil 1.0.2。

UnityPy 的原生加速路径在此 Unity 6 文件上发生过退出码 139。研究脚本通过导入前禁用 `UnityPy.UnityPyBoost` 使用纯 Python 路径。该版本可以读取本次 Texture2D、TextAsset，但 Sprite 完整体解析报长度错误；因此仅使用 `peek_name()` 建立 Sprite 名称索引，图像直接从独立 Texture2D 读取。**没有把未解码的 Sprite pivot、rect、border 等字段当成已验证信息。**

新版 UnityPy 安装曾停在额外构建依赖；已改用上述固定版本。不应在以后开发中悄悄升级解析器而沿用旧验证结论。

在开发目录执行：

```sh
GAME_DATA='/Users/jie/Library/Application Support/Steam/steamapps/common/Message From Aliens/mac.app/Contents/Resources/Data'
GAME_PY='/Users/jie/project/dev-env/venvs/game-assets/bin/python'
"$GAME_PY" tools/research/inspect_game_assets.py --game-data "$GAME_DATA" --output research/game
"$GAME_PY" tools/research/inspect_game_render.py --game-data "$GAME_DATA" --output research/game
"$GAME_PY" tools/research/compare_game_glyphs.py --game-data "$GAME_DATA" --glyph-dir app/src/main/assets/glyphs --output research/game
```

脚本只读取指定游戏目录，输出到研究目录，不改动游戏或生产字形。研究目录含本地游戏样例，不应被通用 APK 打包脚本自动纳入发行资源。

工具参考：[UnityPy](https://github.com/K0lb3/UnityPy)。原始游戏素材与代码权利仍归原权利人；本记录没有改变这些素材的许可范围。


## 后续生产处理记录（2026-11-12 晚）

微信实测发现三张原始纹理与其余旧素材混用时笔画质感明显不一致。生产构建已停止调用 `export_reviewed_glyphs.py`，改由 `prepare_uniform_glyphs.py` 对完整 1344 字形做同一遮罩处理并校验来源。原始素材、已核对映射与未核对状态均保留，不覆盖研究证据；本次不是全量原图映射完成。详见 `four-slot-numbers-update.md`。
