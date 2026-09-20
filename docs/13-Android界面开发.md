# 13 · Android 界面开发

更新：2026-09-20。交付范围是可安装的原生界面版，不代表平台解析、登录和下载功能已经实现。

本文记录 `0.1.x-ui` 界面基线。后续 `0.2.0-alpha` 已实现的阅读功能及当前边界，以 [D1 阅读器功能与验证](14-D1阅读器功能与验证.md) 为准。

## 范围与交互

| 区域 | 本轮实现 |
|---|---|
| 探索 / 分类 | 固定顶栏、搜索与设置、六来源横向 Tab；支持点击或左右滑动内容区切源，选中状态同步、保留各页滚动位置；原创示意书目与平台分类；无续读卡、最新/热门/推荐栏 |
| 底部导航 | 探索、分类、书架；只显示图标，保留无障碍名称 |
| 搜索 | 切源、关键词输入、提交、清空、历史、空态；无“随便看看” |
| 详情 | 封面、简介、目录、收藏切换、开始 / 继续阅读、下载章节面板 |
| 书架 | 收藏、阅读历史、下载管理；支持点击或左右滑动切换 Tab，空页面同样可滑动；下载仅演示入队、暂停 / 继续、删除确认 |
| 阅读 | 本地插画；连续与左右逐页模式；进入默认全屏，单击显示 / 隐藏顶部及底部操作栏；页码滑块、目录、上一话 / 下一话、逐页双击缩放；连续图片零间距、无图间页码、无章末固定空白；上拉换章及末章小提示 |
| 设置 | 内容与来源、阅读体验、APP、网络与关于；十个入口及 WebDAV 子页；账号管理先于漫画源 |
| 账号 | 六来源分组、演示登录、退出；网页登录授权区域占位；不创建真实 WebView、不获取 Token / Cookie |
| 漫画源 | 各平台排序、域名、图片线路、签到等选项界面；配置与浏览源选择互不影响 |
| 内容筛选 | 屏蔽词添加 / 删除、语言多选；实际筛选示意书目，书架不受过滤影响 |
| 外观 | 默认跟随系统；手动浅色、深色、纯黑立即切换并持久化；高刷新率仅保存偏好 |
| 更新 / 数据 / 网络 | 完整设置入口和表单；清楚提示服务尚未接入，不伪造请求成功 |
| 关于 | 标识、介绍、项目与问题反馈占位；版本信息仅放更新页 |

真实平台内容、WebView 会话、自动翻页、音量键、常亮、长按与多指缩放、真实预加载、后台下载、系统目录选择、缓存服务、备份导入导出、WebDAV、GitHub 更新检查及 APK 安装均留待后续实现。读取本地示意图片不需要网络权限，当前 Manifest 未申请 INTERNET 权限。

阅读全屏规则：每次进入阅读默认隐藏操作栏与系统状态栏 / 导航栏，轻点阅读区域显示，再次轻点收起；滚动不唤出工具栏。操作栏覆盖在图片上，显示 / 隐藏不会改变内容区尺寸，按钮避让系统栏及屏幕缺口。退出阅读恢复系统栏，旋转屏幕保留当前工具栏显隐状态。

## 工程与构建

- 单 `app` 模块，包名 / applicationId：`io.github.ddmoyu.picomic`。
- `data/DemoCatalog.kt`：六来源、示意作品和纯函数筛选；`ui/AppViewModel.kt`：StateFlow 界面状态。
- `ui/`：Compose 页面、共享组件、主题、Navigation Compose 路由；`MainActivity` 为原生入口，不嵌套 HTML 原型。
- `gradle/libs.versions.toml` 集中锁定依赖。AGP 9.3.2、Gradle 9.7.1、Compose 编译插件 2.3.21、Compose BOM 2026.08.00、Navigation Compose 2.9.3、JDK 17；compileSdk 37、targetSdk 36、minSdk 26。
- AGP 使用内置 Kotlin；测试显式使用 Espresso 3.7.0，避免旧版 InputManager 反射与 Android 36 模拟器冲突。
- 构建仓库为 Google Maven / Maven Central，跟随开发机的 Gradle 网络配置；无工程硬编码代理。`local.properties`、构建目录、调试产物被 `.gitignore` 排除。
- `tools/export-android-assets.cjs` 将现有原创 SVG 栅格化到 `drawable-nodpi`，无需远程素材。需要 Node.js、Playwright、Chrome；图片已随源码提供，日常构建无需执行该脚本。
- 基础偏好、收藏、搜索历史用本地 SharedPreferences 保存；阅览记录、演示账号、演示下载队列仅在本次进程保留。正式仓储与 DataStore / Room 仍按架构文档接入，不将演示账号迁移为真实会话。
- 没有初始化 Git、提交代码、配置正式签名或发布 GitHub Release。项目地址等待实际仓库确定。

构建与检查：

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

调试包使用 Android 默认 Debug 签名，仅用于开发验收。

64 位安卓手机安装包（仅 `arm64-v8a`）：

```powershell
.\gradlew.bat :app:assembleDebug -PtargetAbi=arm64-v8a
```

输出为 `app/build/outputs/apk/debug/app-debug.apk`，当前交付副本为 `artifacts/PiComic-0.1.2-ui-arm64-v8a-debug.apk`（versionCode 3）。未指定 `targetAbi` 时仍生成多架构包，供模拟器与其他设备开发使用。当前手机包仍为界面预览 Debug 版，未配置正式发布签名或上传 GitHub Release。

## 验证

最终结果见 [Android 界面验证](evidence/android/validation.md)。截图来自模拟器实际运行的 APK，HTML 原型结果不替代原生验证。真实手机手势与大图性能尚未验收。

### 主题与阅读转场修复（2026-09-20）

旧实现把未开启深色开关直接解释为浅色，并通过 `dark || reading` 在进入阅读路由时切换整个 App 主题；Navigation 转场期间仍在显示的详情页因而提前变黑。

- 新增跟随系统、浅色模式、深色模式三种偏好，未设置时跟随系统；新偏好优先于旧版 `dark` 开关，旧版明确保存的手动选择继续有效。偏好支持备份、恢复和同步。
- 全局配色及系统栏图标只由主题偏好和系统配置决定。阅读画布独立使用深灰、纯黑或米白背景，阅读工具栏和面板继续使用所选主题；转场不改全局配色。
- 增加夜间窗口资源；Activity 创建时依据已保存的主题选择窗口样式。
- JVM 62 项通过。Android 8 / API 26 与 Android 16 / API 36 各 9 项定向回归通过：主题切换、手动主题在 Activity 重建后保留、连续转场帧的系统栏主题、纯黑画布及浅色阅读面板、全屏/旋转，以及备份契约。已检查系统深色外观和浅色阅读工具栏截图。
- Debug / Release Lint、正式签名 arm64-v8a Release 构建及本地发布资产校验通过。本次交付 0.3.0 本地候选安装包，不创建版本标签或 GitHub Release。
- 测试使用原创示意页面，没有把模拟器结果表述为实体手机验收。

## 依赖依据

- [AGP 9.3 兼容性](https://developer.android.com/build/releases/agp-9-3-0-release-notes)：JDK 17、Gradle 最低 9.5.0。
- [AGP 内置 Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)。
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom)。
- [AndroidX Test](https://developer.android.com/jetpack/androidx/releases/test)：Espresso 3.7.0 的 InputManager 兼容修复。

依赖锁定为本轮构建验证组合，不宣称所有依赖均为最新版本。AndroidX 与 Kotlin 使用各自开源许可证；插画来自本项目既有原型素材，不复制竞品代码或作品。
