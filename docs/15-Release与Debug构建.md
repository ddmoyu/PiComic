# Release 与 Debug 安装包

2026-09-20，`0.2.1-alpha`，versionCode `5`。本次提供同一份功能代码的两种构建，供 64 位安卓手机安装与体积比较。内容仍为 D1 阅读器的原创示意漫画，真实平台未接入。

| 构建 | 文件 | 精确大小 | MiB |
| --- | --- | ---: | ---: |
| Release | `artifacts/PiComic-0.2.1-alpha-arm64-v8a-release.apk` | 2,530,720 字节 | 2.41 |
| Debug | `artifacts/PiComic-0.2.1-alpha-arm64-v8a-debug.apk` | 14,233,703 字节 | 13.57 |

Release 比 Debug 减少 **82.22%**。MiB 按 1,048,576 字节计算；按十进制 MB 分别约为 2.53 MB 和 14.23 MB。以上是 APK 文件大小，不是安装后占用或运行内存。

## 构建差异

- Release 关闭调试，开启 R8 代码优化、混淆与未使用资源裁剪，使用 Android 默认优化规则及依赖自身的 consumer rules；未添加整包保留或忽略全部告警的规则。配置参考 [Android 官方 R8 文档](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)。
- Debug 保留调试能力与 Compose 调试工具，便于继续开发。
- 两包功能一致，均只有 `arm64-v8a`；包名 `io.github.ddmoyu.picomic`，minSdk 26、targetSdk 36。
- 两包中的全部 9 张封面、6 张阅读示意图，解码后尺寸与 RGBA 像素逐一相同。Release 的 PNG 编码与资源路径可能经构建工具优化，不依赖 APK 内文件名比较图片内容。
- 体积主要减少在 DEX 代码部分；包内压缩大小分类见 `artifacts/0.2.1-apk-size-comparison.json`。

## 签名与安装

本次为本地 alpha 验收包。Release 虽关闭调试且执行了优化，**签名仍显式沿用当前 Android Debug 证书**，方便从已有开发包覆盖安装并保留本地数据，不是正式发行签名。两包不能作为两个独立应用同时安装。

证书 SHA-256：`5d8fb3d2a07485b8832f1f7e4c6b53a4c9c1144b80ba2a34cac77f1b906b434e`。

`apksigner` v2 签名及 `zipalign -c -P 16 4` 检查均通过。尚未配置 GitHub 发布仓库或正式证书，本次交付本地 APK，没有上传 GitHub Releases。

## 重复构建

```powershell
.\gradlew.bat :app:assembleRelease :app:assembleDebug :app:lintRelease -PtargetAbi=arm64-v8a -PlocalReleaseSigning=true
```

输出分别为 `app/build/outputs/apk/release/app-release.apk`、`app/build/outputs/apk/debug/app-debug.apk`。不传 `localReleaseSigning=true` 时，Release 保持未签名；禁止将测试证书默认为后续正式发布证书。默认不指定 `targetAbi` 时生成开发用多架构包，不能将其大小与本次单架构包混比。

Release 映射文件副本：`artifacts/PiComic-0.2.1-alpha-release-mapping.txt`，用于反混淆对应版本的堆栈。

## 校验值

- Release SHA-256：`90435882d5ad146047cce0e93c3a5445060e533de4df6e346663d9b4f9bf73ca`
- Debug SHA-256：`badb42908a3252817f7be307b26aaa345a9a7d18a0d5ddbf978b3794c31b0d06`

每个 APK 旁均附 `.sha256` 文件。构建日志：`artifacts/build-0.2.1-arm64-release-debug.log`；Release Lint 为 0 错误、8 警告。

## 本次附带修复

打包前运行检查发现：从连续阅读切为逐页后进入下一章，退出时旧阅读模式的保存回调可能将旧页码写回。现在按阅读模式与图片尺寸更新生命周期回调，并同步更新音量键回调，使退出、后台保存及翻页使用当前阅读模式。新增原生回归用例覆盖切换模式、换章、退出保存、再次续读。

## 验证

- JVM 单元测试 6 / 6 通过。
- `ReaderControlsTest` 6 / 6 通过，覆盖模式切换后的换章续读、音量键与常亮生命周期、双击 / 长按缩放、自动翻页和进度恢复。日志：`artifacts/release-reader-regression-clean.log`。
- 新增回归用例在修复前复现“预期第 1 页、实际第 6 页”，修复后通过；对比日志为 `artifacts/chapter-regression-before.log`、`artifacts/chapter-regression-fixed.log`。
- 本次未连接实体手机；手机 APK 完成 ABI、版本、签名与对齐检查，运行验证使用同代码、同优化配置的 x86_64 Release 模拟器包。
- 优化后的 Release 在 Android 36 模拟器覆盖安装通过，保留既有 Room 第 6 页进度；默认隐藏操作栏；切换阅读模式、图片解码、放大复原、进入第 2 章并保存第 1 页、强制结束进程后继续阅读均通过。日志：`artifacts/0.2.1-release-smoke.log`。
