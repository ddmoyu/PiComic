# 第三方依赖

当前为开发版本，最终发行应随解析后的完整依赖清单复核许可证。本项目原创示意插画不来自第三方作品。哔咔协议参数和签名规则参考 PicaComic，网络、会话管理及原生界面由 PiComic 实现。

| 直接依赖 | 当前版本 | 许可 / 来源 |
|---|---|---|
| AndroidX Compose、Activity、Lifecycle、Navigation、Room、WebKit、WorkManager | 见 `gradle/libs.versions.toml` | [Apache-2.0 / AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| OkHttp / MockWebServer（测试） | 5.3.0 | [Apache-2.0](https://github.com/square/okhttp/blob/master/LICENSE.txt) |
| Coil、Coil GIF | 3.4.0 | [Apache-2.0](https://github.com/coil-kt/coil/blob/main/LICENSE.txt) |
| ZoomImage | 1.5.0 | [Apache-2.0](https://github.com/panpf/zoomimage/blob/main/LICENSE.txt) |
| Jsoup | 1.23.2 | [MIT](https://github.com/jhy/jsoup/blob/jsoup-1.23.2/LICENSE)，完整许可随 APK assets 打包 |
| desugar_jdk_libs_nio | 2.1.5 | [GPL-2.0 with Classpath Exception](https://github.com/google/desugar_jdk_libs/blob/master/LICENSE)，用于旧版 Android 的 Java NIO 支持；附带完整 LICENSE、ADDITIONAL_LICENSE_INFO 与 ASSEMBLY_EXCEPTION |
| Kotlin / Coroutines / KSP | 见版本目录及 Gradle 解析结果 | [Kotlin Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)、[Coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)、[KSP](https://github.com/google/ksp/blob/main/LICENSE) |

ZoomImage 提供缩放及局部解码组件；章节、阅读调度、进度存储及生命周期逻辑由 PiComic 实现。

`tools/runtime-inventory.init.gradle` 与 `tools/build-license-notices.py` 根据实际 Release 解析结果生成[完整运行时依赖声明](app/src/main/assets/licenses/runtime-dependencies.md)，包含 179 个运行时/平台元数据及 desugaring 组件，并收集依赖包内的许可声明。Apache-2.0 全文、MIT 及 OpenJDK 例外文本随 APK assets 打包。desugar 上游声明固定读取提交 `092407c51c3eaaaa9e46f7b7e436dc642f614064`；实际构件 POM 声明为 GPL-2.0 with Classpath Exception。

阅读图片适配层 `ReaderZoomImage.kt` 使用 ZoomImage 1.5.0 的公开渲染/分块 API，按其 Apache-2.0 适配流程将状态变更移到 Compose 提交后，保留 panpf 与 Coil Contributors 署名。未复制无许可来源实现。

## PicaComic 协议参考

哔咔公开参数与 HMAC 输入格式、JM 图片条带和协议字段、EH/HT 页面路径、Hitomi hash 分桶规则参考 [PicaComic 固定提交](https://github.com/wgh136/PicaComic/tree/155cf0c6d7018f9521f8c375b142bafd85e47a4d)。原生适配器、结构校验、会话隔离、图片管线和测试由 PiComic 实现；近期协议同时与当前公开响应核对，未引入远端脚本执行器。

Copyright (c) 2023 Nyne · MIT License。完整许可保存在 [PicaComic-MIT.txt](app/src/main/assets/licenses/PicaComic-MIT.txt)，并随 APK 的 assets 一同打包。未复制无许可的规则仓库实现。
