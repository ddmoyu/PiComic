# 第三方依赖

当前为开发版本，最终发行应随解析后的完整依赖清单复核许可证。本项目原创示意插画不来自第三方作品；未移植竞品源码。

| 直接依赖 | 当前版本 | 许可 / 来源 |
|---|---|---|
| AndroidX Compose、Activity、Lifecycle、Navigation、Room | 见 `gradle/libs.versions.toml` | [Apache-2.0 / AndroidX](https://android.googlesource.com/platform/frameworks/support/+/androidx-main/LICENSE.txt) |
| Coil | 3.4.0 | [Apache-2.0](https://github.com/coil-kt/coil/blob/main/LICENSE.txt) |
| ZoomImage | 1.5.0 | [Apache-2.0](https://github.com/panpf/zoomimage/blob/main/LICENSE.txt) |
| Kotlin / Coroutines / KSP | 见版本目录及 Gradle 解析结果 | [Kotlin Apache-2.0](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt)、[Coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt)、[KSP](https://github.com/google/ksp/blob/main/LICENSE) |

ZoomImage 提供缩放及局部解码组件；章节、阅读调度、进度存储及生命周期逻辑由 PiComic 实现。
