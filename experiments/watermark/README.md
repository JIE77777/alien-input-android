# 外星图片抗压缩水印原型

这是独立的离线实验，尚未接入 Android 输入法或发布 APK。它使用 Adobe TrustMark 0.9.2，将字形编号和词组边界写进图片像素；解码器仅收到图片，不读取 PNG 文本、文件名、原始查询或发送端缓存。

**实测状态：普通 JPEG 压缩后的恢复率尚不足以接入输入法。** 参见 [完整实验结果](results/REPORT.md) 与 [可解码的 JPEG 样例](results/v-me-50-compressed.jpg)。

## 当前范围

- 支持 1～4 个 `F0001`～`F1344` 静态字形，保留词组分界。
- 输出可以恢复为现有 `ALIEN1|v6.12.4|…` 编码，便于后续放回草稿。
- 使用 TrustMark Q/P 模型和 BCH_3 纠错；应用层另外检查协议标记、版本、合法编号和 CRC16。
- 超长消息、动态 `N…` 数字字形和其他字库版本明确拒绝，不截断。
- 当前输出原尺寸 RGB PNG；透明输入在黑底合成。并不承诺任意裁切、聊天截图或严重缩小后都可恢复。
- 语义解释应由现有词典完成。水印保存字形，不保存中文输入原文，不表示经过验证的完整翻译。

## 环境与模型

实验环境放在仓库外，避免扩大 Android 构建的依赖。当前验证环境使用 Python 3.9；完整依赖以 `requirements-lock.txt` 为准。

```sh
python3 -m venv "$HOME/project/dev-env/venvs/alien-watermark"
WM_PY="$HOME/project/dev-env/venvs/alien-watermark/bin/python"
"$WM_PY" -m pip install -r experiments/watermark/requirements-lock.txt
export ALIEN_WATERMARK_MODELS="$HOME/project/dev-env/models/trustmark-0.9.2"
"$WM_PY" experiments/watermark/fetch_models.py --output "$ALIEN_WATERMARK_MODELS"
```

下载脚本仅使用 Adobe 官方模型地址，并检查上游 MD5；同时记录下载文件 SHA256。模型从 TrustMark 的虚拟环境目录通过符号链接访问，首次加载前再次核对摘要。所有模型齐备后，编解码不需要网络。

## 编码与解码

从仓库根目录运行：

```sh
"$WM_PY" experiments/watermark/watermark.py encode \
  --input docs/images/v-me-50.png \
  --output build/watermark/v-me-50-Q.png \
  --groups 'F0902;F0834;F1305,F1302' --variant Q --strength 1

"$WM_PY" experiments/watermark/watermark.py decode \
  --input build/watermark/v-me-50-Q.png --variant Q
```

解码成功输出字形分组和现有文本编码，退出码为 0；未能可靠解码或编码后的自校验失败，退出码为 2。编码自校验失败时仍保留实验图片用于分析，但会明确报告 `verification-failed`。

解码要使用匹配的模型变体。没有声称一个变体可以读取另一变体。示例中的强度仅为实验参数，最终取舍见实验报告。

## 可复现测试

```sh
python3 -m unittest discover -s experiments/watermark -p 'test_*.py' -v

# 使用生产构建生成的统一字形；首次可单独准备：
"$WM_PY" tools/prepare_uniform_glyphs.py --output build/uniform-glyphs/glyphs

"$WM_PY" experiments/watermark/benchmark.py \
  --glyph-dir build/uniform-glyphs/glyphs \
  --meme docs/images/v-me-50.png \
  --output build/watermark/benchmark
```

基准固定随机种子，覆盖八张单行图片：实际“V我50”图、1/2/4 字形消息、三组随机字形和浅色背景。每张图比较 Q/P 两种模型、1.0/1.5 两种强度，并测试 PNG 像素读取、JPEG 90/75/50/30、缩至 360/240 宽后压缩、多次 JPEG 编码、裁边和加边。另用未加水印的原图、压缩原图与纯色图检查误接受。

`results.json` 保存全部结果，不只保留成功样例；`comparison.png` 显示原图、水印图与放大八倍的差异。像素 PSNR 和平均变化只是辅助指标，不能单独证明“肉眼不可见”。这些是本地合成变换测试，不等于微信真实传输验证，也不把 Mac 推理耗时当作 Android 性能。

## 二进制协议 v1（75 bit）

| 字段 | 位数 | 含义 |
| --- | ---: | --- |
| 标记 | 8 | 固定 `10110110` |
| 版本 | 2 | 固定 `01`，对应本实验静态 v6.12.4 字库 |
| 数量 | 2 | 字形数减一 |
| 字形编号 | 44 | 四个 11 bit 编号；未使用项必须为零 |
| 词组边界 | 3 | 相邻字形之间是否结束词组；未使用位必须为零 |
| CRC16 | 16 | 前 59 bit 在右侧补五个零后，使用 CRC-CCITT，初值 0xffff |

先由 TrustMark 的纠错层恢复位串，再验证应用层协议；失败时不猜测文字。本协议是实验格式，尚未冻结为正式交换标准。

## 上游与许可

实现依赖 [Adobe TrustMark](https://github.com/adobe/trustmark)，[Python 0.9.2](https://pypi.org/project/trustmark/0.9.2/)，上游采用 MIT 许可。模型与配置下载来源和摘要见 `model-provenance.json`；权重不提交到本仓库。模型容量、纠错与强度说明见 [官方配置文档](https://github.com/adobe/trustmark/blob/main/python/CONFIG.md)。

新增包装代码沿用本仓库独立模块的 MIT 许可；外星字形仍遵循仓库 `THIRD_PARTY_NOTICES.md` 的权利说明。

## 重放参考文件的压缩参数

```sh
"$WM_PY" experiments/watermark/profile_benchmark.py --profile experiments/watermark/sample-jpeg-profile.json --benchmark-dir build/watermark/benchmark --output build/watermark/sample-profile
```

参数表可以独立使用，无需原始用户图片。
