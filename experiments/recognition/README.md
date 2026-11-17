# 外星字形直接识别原型

离线实验，未接入 Android APK。输入为图片，输出为静态字形 ID 顺序及候选；不依赖水印、PNG 元数据、文件名、网络服务或神经模型。使用现有字库和生成器的布局约束；尚未还原游戏全部几何组合规则。

本轮结果见 [实验报告](results/REPORT.md)。在当前固定画布上表现良好，不能视为任意截图 OCR 或普遍微信成功率。

## 范围

- 1344 个 F 字形，单行 1～4 个，480×208 画布或等比缩放版本，输入宽度 240～4096。
- 100×100 字位中的字形、112 像素字距，居中排版。整体对齐补偿范围为标准画布坐标 ±2 像素。
- 同时读取原始素材与统一遮罩两种模板；相同 ID 的不同风格先合并，再比较候选差距。
- 按前景与背景的颜色距离提取形状，本轮测试包含黑底浅色和浅色底黑字。
- 不支持多行、任意截图定位、明显裁切/旋转、动态 N 数字、未知字库版本；当前词组边界输出 `unknown`。
- 本原型不尝试恢复原始中文输入。字形到释义属于词典查询，词组解释需要另外处理。

## 运行

Python 3.9 已验证。可复用本地已有 `alien-watermark` 虚拟环境，但本实验只依赖 NumPy 和 Pillow；不加载 TrustMark、PyTorch 或模型权重。若单独建立环境，安装本目录的 requirements.txt。

从仓库根目录执行：

```sh
RECOGNITION_PY="$HOME/project/dev-env/venvs/alien-watermark/bin/python"
"$RECOGNITION_PY" tools/prepare_uniform_glyphs.py --output build/uniform-glyphs/glyphs

OPENBLAS_NUM_THREADS=1 VECLIB_MAXIMUM_THREADS=1 "$RECOGNITION_PY" experiments/recognition/recognize.py \
  --glyph-dir build/uniform-glyphs/glyphs \
  --glyph-dir app/src/main/assets/glyphs \
  --input experiments/watermark/results/wechat-P2.jpg
```

`recognized` 返回字形顺序，退出码 0；`uncertain` 保留候选但 `glyphs=null`；`unreadable` 表示不支持或没有可接受区域，后两者退出码 2。相似度是算法分数，不是正确概率。

## 识别过程

1. 检查尺寸和比例，估计四角背景，标准化前景对比度。
2. 枚举 1～4 个居中字位，不把一个字内部分离的笔画切成多个字。
3. 对字位做轻微平滑和 48×48 特征化，用归一化点积比较整字模板，保留前三名。
4. 将候选按生成器规则重新排列，对照整行正文图像，惩罚无法解释的笔画和错误字数。
5. 基础判断不通过时搜索整体 ±2 像素平移；同一字形序列的不同对齐结果合并，防止将其误当成多个答案。
6. 字形相似度、候选差距或整行匹配不足时返回不确定。

阈值在首轮测试前设定：字形相似度 ≥0.91，第一和第二候选差 ≥0.025，整行相似度 ≥0.90，不同序列差 ≥0.025。对齐改进没有降低这些阈值。该阈值尚未通过独立大规模真实图片校准。

正文比较范围为标准画布 y=48..160；底部水印区域完全不参与识别。输入假设是独立消息图片，不是带有其他上下文的聊天截图。

## 复现实验

```sh
OPENBLAS_NUM_THREADS=1 VECLIB_MAXIMUM_THREADS=1 "$RECOGNITION_PY" experiments/recognition/benchmark.py \
  --uniform build/uniform-glyphs/glyphs --original app/src/main/assets/glyphs \
  --output build/recognition --field-dir experiments/watermark/results

OPENBLAS_NUM_THREADS=1 VECLIB_MAXIMUM_THREADS=1 "$RECOGNITION_PY" experiments/recognition/stress.py \
  --uniform build/uniform-glyphs/glyphs --original app/src/main/assets/glyphs \
  --output build/recognition
```

两个实验命令均可加 `--no-alignment` 并另指定输出目录，复现最初不做平移搜索的基线。

基准包括全字库单字、固定随机种子的 1～4 字消息、两种前景/背景、JPEG 和缩小、无效输入及旧微信实验文件。压力测试包含整体偏移、模糊、旋转、裁切、加边，以及伪造 PNG 元数据的对照。

为了减少仓库中大量机械行差异，完整逐项结果以无损 gzip JSON 存档；`results/summary.json` 为可直接阅读的汇总。解压后结构与测试程序生成的 JSON 一致。

原型代码沿用独立模块的 MIT 许可；使用的字形仍遵循仓库 THIRD_PARTY_NOTICES.md。
