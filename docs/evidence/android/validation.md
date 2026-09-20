# Android 界面验证

日期：2026-09-20。范围：原生界面与离线简单交互。

- Debug 构建通过：`assembleDebug`。
- Lint 通过：0 个错误，10 个警告；警告包括锁定依赖非最新、targetSdk 36、Compose 参数约定和备份 / KTX 建议，未使用 baseline 隐藏错误。
- JVM 单元测试：3 / 3 通过，覆盖筛选组合、关键词标准化、跨平台作品键。
- Android 仪器测试：5 / 5 通过，覆盖探索 / 搜索 / 分类、详情 / 收藏 / 阅读 / 下载队列、设置 / 账号 / 来源 / 主题 / 更新、搜索旋转恢复、连续阅读短拉 / 换章 / 目录 / 末章。
- 测试设备：Medium_Phone_API_36.1，Android SDK 36，1080 × 2400；横屏 2400 × 1080。
- 额外以 1600 × 2560、240 dpi 模拟宽屏检查探索及设置；不是平板专属布局，截图见相册。
- APK：`artifacts/PiComic-0.1.0-ui-debug.apk`；13,108,829 字节；Debug v2 签名验证通过。
- 包名 `io.github.ddmoyu.picomic`，versionName `0.1.0-ui`，versionCode 1；minSdk 26、targetSdk 36。包含 arm64-v8a / armeabi-v7a / x86 / x86_64。
- SHA-256：`1f18153713faa1ed9f96e4d91a591ee29b7c096972d9d7fcbef6c2d2a636e244`。

[查看模拟器截图](index.html)

构建日志：`artifacts/build-validation.log`；仪器测试日志：`artifacts/instrumentation.log`；完整 Lint：`app/build/reports/lint-results-debug.html`。

本轮没有真实平台内容或账号测试、没有网络解析、没有真实下载与升级测试、没有实体手机和超大图片性能验收。设置中尚未实现的行为均以说明或演示入口呈现。未上传或发布 APK。

## arm64-v8a 手机安装包

2026-09-20 补充：按手机架构重新打包，界面代码未改动。

- 构建：`assembleDebug -PtargetAbi=arm64-v8a` 成功，日志 `artifacts/build-arm64.log`。
- 交付：`artifacts/PiComic-0.1.0-ui-arm64-v8a-debug.apk`，12,835,278 字节。
- `aapt dump badging` 确认唯一原生架构为 `arm64-v8a`；包名、版本和 SDK 范围与上方一致。
- `apksigner verify --verbose --print-certs` 验证通过，Android Debug 证书、v2 签名；`zipalign -c -P 16 4` 检查通过。
- SHA-256：`64f5ee07a39f1e1abaaddf4de7ce1733de295c3b011ddade65770f973dc2f574`，同名 `.sha256` 文件随包提供。
- 当前仅连接 x86 模拟器，没有连接实体手机；本次完成打包、架构、签名及对齐检查，未宣称实体手机运行验证。仍是 Debug 界面预览包，未上传外部发布平台。

## 0.1.1-ui：Tab 横向滑动修复

2026-09-20：探索 / 分类的来源页和书架的收藏 / 阅读历史 / 下载管理改为原生 HorizontalPager，点击 Tab 与滑动选中状态同步。各页独立保存纵向滚动位置；作品跳转绑定页面本身的漫画源；从搜索返回时同步最新来源。

- `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过；Lint 0 错误、11 警告（增加的警告是可选 ABI 筛选的 ChromeOS 提示）。构建日志：`artifacts/build-tab-swipe.log`。
- 模拟器仪器测试 8 / 8 通过：原有 5 个流程及新增 3 个手势流程。新增覆盖左右切换、首尾边界、短拖回弹、纵向滚动不切源、滑动后作品来源、搜索返回、分类内容同步、书架空页面滑动、点击跨页、导航返回和旋转保留选中项。日志：`artifacts/tab-swipe-tests.log`。
- 手机包：`artifacts/PiComic-0.1.1-ui-arm64-v8a-debug.apk`；12,851,662 字节，versionCode 2，唯一 ABI 为 `arm64-v8a`。
- Debug v2 签名和 `zipalign -c -P 16 4` 验证通过；签名证书与 `0.1.0-ui` 相同，可以覆盖安装。架构构建日志：`artifacts/build-arm64-tab-swipe.log`。
- SHA-256：`ceec82647a7b165b76d9de9850659b9181e7fcbd2d2b860192ed803aedd9f1ab`。
- 手势运行验证来自 Android 36 模拟器；arm64 实体手机未连接，未完成真机运行验证。

## 0.1.2-ui：进入阅读默认全屏

2026-09-20：进入阅读默认隐藏顶部 / 底部操作栏及系统状态栏 / 导航栏；单击显示，再次单击收起。操作栏覆盖图片，内容区不随系统栏显隐缩放；退出恢复系统栏。

- `assembleDebug`、`assembleDebugAndroidTest`、`lintDebug` 通过；Lint 0 错误、11 个既有警告。构建日志：`artifacts/build-reader-fullscreen.log`。
- Android 36 模拟器仪器测试 10 / 10 通过：新增测试覆盖连续 / 左右逐页模式默认全屏、点击显隐、滚动不唤出菜单、系统栏真实可见性、内容铺满窗口且点击前后尺寸不变、重新进入阅读、横屏及两种退出方式；原有阅读 / Tab / 页面流程回归通过。日志：`artifacts/reader-fullscreen-tests.log`。
- 已检查实际 Compose 截图：[默认全屏](screenshots/17-reader-fullscreen.png)、[点击后操作栏](screenshots/18-reader-controls.png)。
- 手机包：`artifacts/PiComic-0.1.2-ui-arm64-v8a-debug.apk`；12,851,662 字节，versionCode 3，唯一 ABI 为 `arm64-v8a`。
- Debug v2 签名及 16 KB 对齐检查通过，证书与上一版相同，支持覆盖安装。架构构建日志：`artifacts/build-arm64-reader-fullscreen.log`。
- SHA-256：`524ce5bf5e7b25c8756088ad7d4ee418d1a5a1fde10514a1d6424dbf40733e0b`。
- 未连接 arm64 实体手机，本次不包含真机运行验收；未上传 GitHub Release。

## 0.2.0-alpha：D1 阅读器子阶段

2026-09-20：阅读器接入 Coil / ZoomImage、Room 进度、音量键、自动翻页、常亮、预加载、双击 / 多指 / 临时长按缩放。范围与待验证项见 [D1 阅读器功能与验证](../../14-D1阅读器功能与验证.md)。

- Debug 和 AndroidTest 构建通过；Lint 0 错误、13 警告，无 baseline。构建日志：`artifacts/build-reader-d1-final.log`。
- JVM 测试 6 / 6 通过：原有目录筛选 3 项，以及预取边界、跨尺寸页内比例、计时输入校验 3 项。
- 原生流程共 17 个不同测试通过：`artifacts/reader-d1-tests.log` 为完整 16 项通过；`artifacts/reader-d1-extra-tests.log` 为新增章末自动停止及精确续读复测 2 项通过。没有把重复执行计入独立测试数。
- 实际注入音量键事件，验证正反翻页；暂停 / 退出后音量键处理和 `FLAG_KEEP_SCREEN_ON` 释放；自动翻页退后台和章末停止；双击、按住放大 / 松开复原覆盖连续及逐页两种布局。
- Room 写入队列、数据库关闭重开、跨来源隔离与页内比例恢复通过。初测发现的初次测量宽度为零导致恢复偏移丢失、后台等待重组导致按键未释放两项问题均已修复并复测。
- 本地生成 640 × 16,000 PNG，ZoomImage 分块解码 ready、tile grid 和放大状态验证通过；不代表真实平台全部图片格式与真机内存压力验证完成。
- `tools/check-reader-process.ps1` 在测试模拟器运行：读取到第 6 页约 14.9% 位置，强制结束进程，重新启动后通过详情继续阅读。恢复前后 PNG 的 SHA-256 相同，画面与页内位置一致。日志：`artifacts/process-reader-result.log`；[恢复前](screenshots/19-reader-resumed.png)、[重启后](screenshots/20-reader-process-restored.png)。
- 手机包：`artifacts/PiComic-0.2.0-alpha-arm64-v8a-debug.apk`，14,233,707 字节；versionCode 4，minSdk 26、targetSdk 36，唯一 ABI 为 `arm64-v8a`。
- Debug v2 签名、16 KB 对齐检查通过，与上版相同证书，可覆盖安装；SHA-256：`fb3973354deb85a0f5d3d39e66c5cec1516621adbd0c8bacbe9458c76e68a963`。
- 当前平台目录仍为原创示意内容；网络 / 代理、WebView、真实平台服务、下载 / 同步 / 更新尚未接入，D1 整体未标记完成。未连接实体手机或上传发布平台。

## 0.2.1-alpha：Release / Debug 体积对比与续读修复

2026-09-20：启用 Release 的 R8 代码优化与资源裁剪，并修复切换阅读模式后退出时写回旧页码的问题。签名采用显式本地测试配置，不默认将测试证书用于正式发布。

- 同版本 / 同功能 / 单一 arm64-v8a：Release 2,530,720 字节（2.41 MiB），Debug 14,233,703 字节（13.57 MiB），减少 82.22%。全部 15 张原创图片的解码尺寸与像素一致。
- versionCode 5、minSdk 26、targetSdk 36；Release 的 `debuggable=false`。两包 v2 签名、16 KB 对齐及 SHA-256 校验完成。
- `assembleRelease` / `assembleDebug` / `lintRelease` 通过；Release Lint 0 错误、8 警告；JVM 6 / 6、阅读器原生回归 6 / 6 通过。
- 同优化配置的 x86_64 Release 已在模拟器完成覆盖安装、既有进度恢复、切换模式、图片缩放、换章保存、进程重启续读检查。`artifacts/0.2.1-release-smoke.log` 留有各步骤结果。
- 安装包、校验值、映射文件、构建命令和验证范围见 [Release 与 Debug 构建](../../15-Release与Debug构建.md)。未连接实体手机，未上传 GitHub Releases。
