# 运行依赖、许可与可重建输入

公开预览构建：`debug`；许可清单继续使用固定配置 `privatePreviewRuntimeClasspath`。机器清单位于 [runtime-dependencies.json](android/app/src/main/assets/licenses/runtime-dependencies.json)：记录 Gradle 实际解析的 73 个独立外部 AAR/JAR 制品、精确坐标、原始制品 SHA256、POM/父 POM 证据、完整许可文本路径、随附文本及依赖输入中的 12 个 native ELF 来源（最终 APK 打包其中 8 个，另 4 个属于 Compose inspector 工具归档）。同坐标/文件名/SHA的重复变体记录去重；不用手写直接依赖列表冒充完整传递依赖。BOM/平台约束在 resolvedComponents 中，未伪装成打包二进制。73项仅为该固定配置的基线；debug另含ui-test-manifest开发测试宿主，不能把73视为debug的完整制品计数。

完整文本从「观测 → 设置 → 关于 startracker 与开源许可」离线阅读。GPL/本地修改、Astronomy MIT、GeoNames CC BY4.0、ERFA 条款、Apache2和实际制品附带的许可原文均在 APK assets；[NOTICE](NOTICE) 描述来源、修改、图标和对应源码位置。

当前运行制品的 POM/父 POM 均声明 Apache2；`listenablefuture:1.0` 的许可来自 `guava-parent:26.0-android`。分类依据冻结 [POM文件](docs/dependencies/poms/) 和SHA，而非从库名推断。实际随包的 astronomy-jvm 来自已 vendored MIT 源码；Heliodos 派生app为GPLv3；ERFA常量和GeoNames数据另遵循其条款。Colorpicker仅为保留的源码归档，无app运行依赖，原LICENSE/NOTICE留在 `android/colorpicker/`。JPL/USNO/Astropy测试样本、测试框架和构建工具不列作APK运行组件。

Gradle wrapper9.6.0、AGP9.4.0、Kotlin2.4.21、JDK21.0.13+9、build-tools36.0.0、compile37.2/target37/min30沿用本项目固定工具链，见 [构建说明](docs/BUILD.md)。不因新版提示自动升级。

锁定分两层：

- `android/{app,astronomy-jvm,colorpicker-android}/gradle.lockfile` 是Gradle正常生成的版本锁；文件逐条列出覆盖配置。包括privatePreview编译/运行/单测及所执行debug/AndroidTest、lint和其他已解析配置；未解析的新配置仍须补锁并审查。
- `android/gradle/verification-metadata.xml` 对本次解析的工具/插件、元数据和制品固定SHA256，正常构建使用严格校验。初始摘要从本机已用官方仓库缓存建立，属于可追踪的信任首次使用基线；不冒称每个制品都另经发布者签名鉴证。仓库只启用Google、Maven Central和Gradle Plugin Portal。

一个明确的锁定例外：`kotlin-stdlib-common:2.4.21` 的 `.module` 不含二进制，只声明转向stdlib2.4.21。Gradle9.6正常写锁会省略其runtime条目，严格重放却要求该条目，已重复复现。因此仅以Gradle的 `ignoredDependencies` 排除此元数据门面；其精确 `.module` SHA仍在verification-metadata中，实际stdlib制品及版本仍严格锁定，没有全局lenient模式、关闭摘要校验或升级依赖。

再核对（不改锁、不改许可）：

```sh
python3 scripts/build-android.py :app:exportPrivatePreviewDependencies
python3 scripts/export-dependency-licenses.py --check
python3 scripts/build-android.py :app:assemblePrivatePreview :app:testPrivatePreviewUnitTest :app:lintPrivatePreview
```

有意改变依赖后，先审查实际坐标和许可，再用Gradle的 `--write-locks --write-verification-metadata sha256` 生成并逐项复核变更；运行 `export-dependency-licenses.py` 更新离线材料，最后无写入参数重跑严格校验。未知许可、POM缺失、哈希不符会失败，不自动跳过。脚本导出的是原始AAR/JAR；本地缓存路径只在忽略的临时导出中，进入APK/源码的清单不含用户缓存绝对路径。

GeoNames原ZIP哈希固定在 [provenance](android/app/src/main/assets/geonames-provenance.json)。已存在的 `android/app/src/test/resources/reference/build-city-index.py ORIGINAL_ZIP` 按精确源哈希、字段选择和mtime=0重建 `cities15000.data`，不能替换成当天可变下载。`.data` 内容仍是gzip，避开AAPT自动展开 `.gz` 的行为。CC BY完整文本取自Creative Commons官方仓库固定提交，取回地址/日期/哈希见 [cc-by-source.json](android/app/src/main/assets/licenses/cc-by-source.json)。

冻结包记录源码commit/tree、证书指纹、APK SHA及锁定文件哈希。固定输入和构建记录支持重建审计；未完成两次字节一致比较时不声称跨机器位级可复现。

Gradle机制依据：[dependency locking](https://docs.gradle.org/9.6.0/userguide/dependency_locking.html)、[dependency verification](https://docs.gradle.org/9.6.0/userguide/dependency_verification.html)。


高清星空使用 HYG v4.1 的 99,049 颗背景星，数值数据为 CC BY-SA 4.0；25 颗精确叠加同源。来源与再生见 [星表](docs/data/star-catalog.md)。NASA/Goddard SVS 与 ESA/Gaia/DPAC 的星图经 sRGB 转换和分块，原始来源与派生哈希见 [高清数据](docs/data/sky-detail/sources.json)、[旧4K图](docs/data/legacy-sky-maps.json) 和 [署名](android/app/src/main/assets/sky/ATTRIBUTION.txt)。这些数据各自保留其署名/条款，不由应用代码许可替代。
