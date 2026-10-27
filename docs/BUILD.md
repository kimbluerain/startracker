# 构建 · Build

请使用 Git 克隆仓库，保留 `.git`：构建会记录当前提交和源码树。直接下载 ZIP 不包含这些信息。
Use a Git clone with its `.git` directory: the build records the commit and source tree. A downloaded ZIP lacks this metadata.

## 固定工具链 · Pinned toolchain

| 工具 / Tool | 版本 / Version |
| --- | --- |
| Java | JDK 21 (verified with 21.0.13+9) |
| Python | Python 3, standard library for the build/license checks |
| Android SDK | platform `android-37.2`, Build Tools `36.0.0` |
| Gradle | 9.6.0, bundled wrapper |
| Android Gradle Plugin | 9.4.0 |
| Kotlin / Compose compiler | 2.4.21 |
| App SDK | min 30 / target 37 / compile 37.2 |

设置 `JAVA_HOME`、`ANDROID_HOME` 为本机路径。脚本在 macOS 提供默认位置，其他环境请显式设置；SDK 许可需由开发者接受。依赖版本与校验和已锁定。
Set `JAVA_HOME` and `ANDROID_HOME` for your machine. The helper has macOS defaults; set both explicitly elsewhere. Accept the Android SDK licenses as required. Dependencies and checksums are pinned.

```sh
python3 scripts/build-android.py :app:assembleDebug
python3 scripts/build-android.py :app:testDebugUnitTest :app:lintDebug
```

输出 / Output: `android/app/build/outputs/apk/debug/app-debug.apk`.

`debug` 使用本机 Android 调试签名；不要提交签名文件。不同开发者的签名不能直接覆盖彼此的安装。
`debug` uses your local Android debug certificate. Never commit signing keys. Builds signed by different developers cannot overwrite one another directly.

运行时核心离线；首次构建下载工具链和依赖。Android 模拟器或手机仅在运行 instrumentation tests 时需要。数据生成脚本的额外依赖见脚本说明，普通构建直接使用已随仓库提供的派生资源。
The runtime core is offline; the first build downloads tools and dependencies. A device/emulator is needed only for instrumentation tests. Data-generation extras are documented in their scripts; ordinary builds use the bundled derived assets.

许可检查 / License verification:

```sh
python3 scripts/build-android.py :app:exportPrivatePreviewDependencies
python3 scripts/export-dependency-licenses.py --check
```

`privatePreviewRuntimeClasspath` 是保留的许可清单配置名；这不表示仓库仍为私有。
`privatePreviewRuntimeClasspath` is the retained license-inventory configuration name; it does not indicate repository visibility.
