# PiComic

Android 手机漫画阅读 App · Kotlin / Jetpack Compose · 设计与开发基线 v0.1

项目目录：`E:\ddmoyu\PiComic`。现处于 D1 阅读器技术验证阶段：原生 Kotlin / Jetpack Compose 工程，已实现阅读控制、图片缩放与持久进度。漫画目录仍为原创示意数据，真实平台服务尚未接入。

## 构建 Android 界面版

用 Android Studio 打开项目根目录，配置 Android SDK 后运行 `app`。命令行：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。本轮范围、工程说明与验证记录见 [Android 界面开发](docs/13-Android界面开发.md)。

仅打包 64 位安卓手机：`.\gradlew.bat :app:assembleDebug -PtargetAbi=arm64-v8a`。当前开发版为 `0.2.1-alpha`，保留 Tab 横滑和默认全屏；支持音量键、自动翻页、常亮、缩放及持久阅读进度，并修复切换阅读模式后退出时保存旧页码的问题。详见 [D1 阅读器功能与验证](docs/14-D1阅读器功能与验证.md)。

构建同版本的手机 Release / Debug 体积对比包：

```powershell
.\gradlew.bat :app:assembleRelease :app:assembleDebug :app:lintRelease -PtargetAbi=arm64-v8a -PlocalReleaseSigning=true
```

Release 启用 R8 代码优化、混淆和资源裁剪，关闭调试；保留与 Debug 相同的功能及原创示意图片。`localReleaseSigning=true` 仅用于本机验收，显式沿用现有 Debug 证书，方便覆盖安装；不是正式发布证书。未传入该参数时 Release 保持未签名，避免误用测试证书正式发布。当前没有配置 GitHub 发布仓库。

当前安装包的大小、校验值与签名说明见 [Release 与 Debug 构建](docs/15-Release与Debug构建.md)。

## 开始评审

- [打开 HTML 交互原型](prototype/index.html)：无需安装依赖，可直接双击打开。
- [产品需求与范围](docs/01-产品需求.md)：功能、优先级、边界和验收目标。
- [界面与交互规范](docs/02-界面与交互.md)：页面、导航、状态和手机布局。
- [技术架构与工程规范](docs/03-技术架构.md)：独立 Kotlin 工程、模块、依赖和开发规则。
- [竞品源码调研](docs/04-竞品源码调研.md)：四个参考项目及其规则仓库的实现证据。
- [六平台接入规格](docs/05-平台接入规格.md)：内容获取、认证、地址和平台能力。
- [网络、登录与会话](docs/06-网络登录与会话.md)：系统代理/VPN、HTTP 代理、Token、Cookie、WebView。
- [阅读器、缓存与下载](docs/07-阅读器与下载.md)：翻页、长图、手势、JM 重组和后台下载。
- [数据模型与存储](docs/08-数据模型与存储.md)：数据表、状态隔离、备份与恢复。
- [开发计划与验收](docs/09-开发计划与验收.md)：阶段、风险、验证矩阵、交付标准。
- [原型说明与验证](docs/10-原型说明与验证.md)：可交互范围、演示边界、验证记录。
- [应用更新与 GitHub 发布](docs/11-应用更新与GitHub发布.md)：检查更新、Release 资产、APK 校验与安装。
- [设置与偏好规格](docs/12-设置与偏好规格.md)：分组设置、平台选项、阅读外观、数据与 WebDAV。
- [源码证据索引](docs/evidence/source-index.md)：固定提交链接和调研时间。

## 已确认的约束

1. 名称 PiComic；独立 Android Kotlin 项目，设计和业务独立制定。
2. 手机优先；横屏、分屏、平板不遮挡、不崩溃，暂不开发平板专属工作台或双栏导航。
3. 内置 picacg、e-hentai/exhentai、jmcomic、hitomi、htcomic、nhentai 六组来源；不提供扩展安装、脚本导入或插件市场。
4. 默认跟随系统网络，兼容系统 VPN/代理，不强制直连；API、图片、下载、网页登录必须使用一致的网络策略。
5. 视觉参考原版 PicaComic 的 Material 3、主题色、漫画卡片与来源切换；采用原生 Android 手机交互。
6. 原型已作为 Android 界面实现基线；当前先完成 UI 和简单交互。素材为项目本地原创示意插画，业务数据为演示数据。
7. 2026-09-18 交互调整：探索/分类使用平台 Tab；顶部固定左侧标题、右侧搜索与设置；底部导航仅显示图标。
8. 账号入口统一为“账号管理”；按平台配置 WebView 登录及自动获取/验证会话，支持重新登录与退出。
9. 更新通过 GitHub Releases 发布；App 提供手动及可选启动检查、更新说明、下载及系统安装入口。
10. 设置按内容与来源、阅读体验、APP、网络与关于归类；本轮要求的筛选、平台参数、阅读/外观、数据、日志及 WebDAV 均纳入首版范围。

11. 导航去重：取消“我的”；底部为探索、分类、书架。收藏/阅读历史/下载管理统一在书架，全部配置统一在右上角设置。

## 文档标记

- **已确认**：来自用户明确要求。
- **建议基线**：本稿的产品/技术决策，可在评审中调整。
- **源码证据**：已读固定提交中的实现，不代表远端平台当前可用。
- **待联调**：需要真实网络、账号、图片或 Android 设备验证。

完整来源接入是 v1.0 目标；分批开发只是实施顺序，不会把未接入来源标为已完成。更新发布渠道已确定为 GitHub Releases，具体仓库待配置；应用商店和云端同步服务不预设。
