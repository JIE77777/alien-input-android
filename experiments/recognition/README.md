# 外星字形直接识别原型

离线实验，尚未接入 Android 输入法。根据图片像素返回字形顺序、各行候选及可能的整数值，不读取水印、PNG 元数据、文件名映射或原始文字。

当前支持固定生产画布中的 **多行、最多 120 个字形、1344 个静态 F 字形和 108 个动态 N 数字字形**。参见 [多行与数字实验](results/multiline/REPORT.md)；之前单行基线的原始结果仍保留在 [第一轮报告](results/REPORT.md)。

## 输入范围和输出含义

- 使用生产 ImageLayout 的 480 像素标准宽度、112 像素字距、100 像素字形及逐行居中规则。
- 单行高度 208；多行高度 `112 × 行数 + 32`。允许等比缩小或放大，输入宽度 240～4096，总像素不超过 1600 万。
- 在最多 120 字形、相邻两行不能合并的约束下，最多允许 48 行。Android 样张覆盖到 40 行/120 字形；48 行上界另用合成样张验证。
- 静态字形同时加载原始、统一遮罩两种模板。动态数字模板由 Android 正式绘图函数导出，完整覆盖 `N1P4`～`N6P21`。
- 结果为 `recognized`、`uncertain` 或 `unreadable`。任何一行未确认，整条 `glyphs` 就为 null；各行候选仍保留，供未来交互确认。
- `phrase_boundaries=unknown`：换行位置不等于词组边界。
- `numeric_candidates` 只解释规范的连续数字序列，始终带 `requires_group_confirmation=true`。例如“49、1”与“50”的字形顺序可能相同，识别器不能凭图判断原来是几次输入。
- 不保证任意截图定位、裁切或旋转。默认配色测试较好，低对比度配色中的超长整数可能返回不确定。

## 运行

Python 3.9，NumPy 2.0.2、Pillow 11.3.0。无需神经网络或模型权重，可复用已有 alien-watermark 虚拟环境。

```sh
RECOGNITION_PY="$HOME/project/dev-env/venvs/alien-watermark/bin/python"
"$RECOGNITION_PY" tools/prepare_uniform_glyphs.py --output build/uniform-glyphs/glyphs
OPENBLAS_NUM_THREADS=1 VECLIB_MAXIMUM_THREADS=1 "$RECOGNITION_PY" experiments/recognition/recognize.py \
  --glyph-dir build/uniform-glyphs/glyphs --glyph-dir app/src/main/assets/glyphs \
  --input experiments/recognition/fixtures/android/number-123456.png
```

CLI 默认使用 `fixtures/android/glyphs`，可用 `--generated-dir` 指定其他经过验证的动态模板。Python API 需显式传入 `generated_dir`；省略则为静态字库模式，供第一轮实验复现。`--no-alignment` 禁用 ±2 像素的小范围平移搜索。

成功退出码为 0；不确定或不支持为 2。相似度是算法分数，不是概率。不会生成带有虚构词组边界的 ALIEN1 编码。

## 怎样利用生成规则

1. 从画布尺寸推断合法行数，按生产行距划分行；每行独立枚举 1～4 个居中字位。
2. 用模板比较字形，再把候选按布局重新绘制，与整行正文对照。
3. 基础判断不通过时搜索 ±2 像素偏移；合并同一字形序列的不同对齐结果。
4. 动态数字额外根据幂次候选，将图形分为上方横线区域与下方基础数字区域，分别比较后等权合并，避免大量横线淹没基础数字的差别。保留原尺寸和半宽缩放两种模板变体。
5. 数字细节验证要求整体粗匹配 ≥0.91、与静态字候选差 ≥0.025、结构得分 ≥0.94、结构候选差 ≥0.025、两个区域各 ≥0.90，并且细节候选位于粗匹配前三名。
6. 仍保留整行相似度 ≥0.90、不同字形序列差 ≥0.025；普通字形的相似度 ≥0.91 和候选差 ≥0.025 不变。
7. 数字解释严格使用生产 Numerals 的七进制规则，校验规范顺序、重复位、零和正负号，且限制为 18 位十进制整数。

数字模板允许的全部 108 种字形中，有些最高位系数组合会超出 18 位十进制范围；可以识别其字形，但不会输出超范围的整数候选。

## 复现验证

```sh
python3 -m unittest discover -s experiments/recognition -p 'test_*.py' -v
OPENBLAS_NUM_THREADS=1 VECLIB_MAXIMUM_THREADS=1 "$RECOGNITION_PY" experiments/recognition/evaluate_multiline.py \
  --fixtures experiments/recognition/fixtures/android \
  --uniform build/uniform-glyphs/glyphs --original app/src/main/assets/glyphs \
  --output build/recognition-multiline
OPENBLAS_NUM_THREADS=1 VECLIB_MAXIMUM_THREADS=1 "$RECOGNITION_PY" experiments/recognition/holdout_multiline.py \
  --fixtures experiments/recognition/fixtures/android \
  --uniform build/uniform-glyphs/glyphs --original app/src/main/assets/glyphs \
  --output build/recognition-multiline
```

评估脚本可添加 `--coarse-numbers --skip-static`，对比不使用数字结构复核的结果。完整逐项数据使用 gzip JSON 留档，summary.json 提供汇总。第一轮 benchmark.py / stress.py 保留用于静态单行测试，但其历史负样本与范围描述仅适用于当时的静态识别器。

重新生成 Android 参考样张：

```sh
source "$HOME/project/dev-env/android-env.sh"
bash tools/build-recognition-fixtures.sh
adb install --no-incremental -r build/recognition-fixtures/fixtures.apk
adb shell am instrument -w io.github.alieninput.recognitionfixtures/io.github.alieninput.androidsemantic.RecognitionFixtures
adb exec-out run-as io.github.alieninput.recognitionfixtures tar -cf - -C files recognition-fixtures > build/recognition-fixtures/android-fixtures.tar
```

独立检查包仅调用生产绘图代码导出合成测试图片，无活动界面，不访问剪贴板、不切换输入法、不联网。导出后先比对来源和哈希，不静默替换冻结的模板或期望结果。

原型代码沿用独立模块 MIT 许可；字形权利与许可仍遵循仓库 THIRD_PARTY_NOTICES.md。
