# 从源码恢复构建

源码固定在 VERSION 所标记的阶段。无需安装 Unity 或提供游戏本体即可构建当前输入法；游戏解析是独立的可选研究流程。

## 已验证环境

JDK 17、Python 3.9+、Pillow 11.3.0、Gradle 9.8.1、Android Platform 37 / Build Tools 37.0.0。独立设备检查另外使用 Android Platform / Build Tools 35。当前 APK 是 arm64 调试版。

将工具链放在仓库外，设置 JAVA_HOME、ANDROID_HOME、DEV_ENV_ROOT、GRADLE_USER_HOME。现有本机环境统一位于 ~/project/dev-env，可直接 source ~/project/dev-env/android-env.sh。

```sh
python3 tools/bootstrap.py
# 首次安装图片构建依赖（已存在时无需重复执行）：
python3 -m venv "$DEV_ENV_ROOT/venvs/game-assets"
"$DEV_ENV_ROOT/venvs/game-assets/bin/python" -m pip install Pillow==11.3.0
bash tools/test-semantic.sh
bash tools/build-trime.sh
```

bootstrap 会校验固定源码提交、应用完整主机补丁、恢复四叶草资源和官方 Trime 原生库来源。已存在但内容不符的依赖会报错，不覆盖个人改动。下载首次需要网络；后续本地构建无需重新下载。

Gradle 可执行文件默认为 $DEV_ENV_ROOT/gradle/gradle-9.8.1/bin/gradle。构建产物在 trime/app/build/outputs/apk/debug。私有调试签名不提交；其他机器生成的调试包可能无法直接覆盖本机签名的已安装应用，不要因此清空正在使用的个人词库。

仅运行语义、数字和排版测试需要 JDK 17，无需 Android SDK 或联网。游戏研究工具的额外解析依赖和本体路径见 game-resource-analysis.md；原始游戏副本与用户截图不随仓库发布。
