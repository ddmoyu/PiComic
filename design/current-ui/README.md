# 当前 Android 界面的视觉对照

2026-09-22 根据用户要求，删除旧 `prototype/`、上一轮 HTML 方案及历史截图后重新采集。此次方案只调整当前页面的字体、颜色、间距、对齐和图标，不添加产品功能或入口。

- 独立 HTML：`artifacts/picomic-ui-current.html`，内嵌本次原生截图和安全测试素材，可直接打开；不自动在办公电脑上打开窗口。
- 真实基线：当前生产 `PiComicApp` 的 Compose 页面，`debugDemo=false`。通过仪器测试注入安全封面及完整合成元数据，没有加载成人作品。
- 设备：一次性 Android 36.1 模拟器，1080 × 2400、420 dpi，`-no-window`。保持网络可用；采集结束已退出模拟器。
- 当前截图：`artifacts/ui-current/baseline/`，共 18 张；时间及 APK/截图 SHA-256 见 `artifacts/ui-current/capture-manifest.json`。
- 用户确认后，已将方案落实到 Android 产品界面。HTML 保留“调整前原生基线 / 已确认视觉方案”对照，不模拟平台登录、下载或设置保存。
- 新版实际运行截图保存在 `artifacts/ui-current/implemented/`；与 `baseline/` 分开记录，不覆盖基线或将旧截图标为新版。
- Android 与 HTML 共用官方 Phosphor 2.1.1 图标路径；采用 Regular 风格，已收藏使用 Heart Fill。来源、MIT 许可及离线生成步骤见 [图标说明](../../third_party/phosphor-icons/README.md)。

## 页面对应

| 本次截图 | 当前界面 | 保持的结构 |
|---|---|---|
| 01 / 11 | 探索浅色 / 深色 | 来源 Tab 下直接显示单列作品；作品行去掉与 Tab 重复的来源名称，保留 ID 和语言；无续读、推荐或额外栏目 |
| 02 | 分类 | 来源 Tab、分类内容和顺序不变；采用接近原版的浅底圆角标签，可见高度 32 px、点击区域 48 px，缩小内边距与行间距 |
| 03 / 04 | 详情 / 目录 | 刷新移至“作品详情”标题栏右侧；下方保留封面、收藏/下载/阅读、信息、简介、单行章节列表 |
| 05 / 06 / 07 / 08 | 收藏 / 空历史 / 历史 / 下载 | 原有三个书架标签、原有字段和操作 |
| 09 / 10 | 设置浅色 / 深色 | 内容与来源、阅读体验、APP、网络与关于四组，入口顺序不变 |
| 12 / 13 / 14 | 搜索空状态 / 深色结果 / 浅色结果 | 输入、来源和最近搜索；结果仍为封面网格，仅封面、标题、作者 |
| 15 / 16 | 阅读器隐藏 / 显示工具栏 | 连续图片零间距；原有顶部、底部工具 |
| 17 / 18 | 阅读 / 外观设置 | 现有分组、选项、开关和说明 |

## 构建与验证

首次采集完整基线后，执行 `python design/current-ui/build.py --capture-apk app/build/outputs/apk/debug/app-debug.apk`，记录与截图对应的 APK。此后执行 `python design/current-ui/build.py` 仅重新生成 HTML，校验 18 张基线截图，不以新构建 APK 覆盖基线的来源记录。图标变更可先执行 `python tools/sync-phosphor-icons.py` 离线重新生成。

截图脚本为 `app/src/androidTest/java/io/github/ddmoyu/picomic/UiAuditScreenshotTest.kt`，只在显式传入 `auditStage` 的一次性模拟器上运行；本次原生实现验收使用 `implemented` 阶段，仍注入安全测试内容。

本轮 Debug 与 AndroidTest 构建通过；原生截图采集测试通过。HTML 已在无窗口 Chrome 检查 18 页 × 412/360/320 px，共 54 组，以及 390 px 浏览器布局与现有页面跳转；无横向溢出、脚本异常或远端请求。HTML 结果不替代 Android 真机验收。

原生实现验证：92 项 JVM 测试、32 项界面回归均通过；竖屏 1080 × 2400 / 420 dpi、横屏 1920 × 1080 / 320 dpi 各采集 18 张新版截图。覆盖 320 dp 列表与 1.6 倍字体、360 dp 大字体设置、详情刷新/缓存/收藏/5000 章虚拟目录、三类书架操作、主题与阅读返回等。详见 [本次验证记录](../../docs/28-当前UI视觉实现与验证.md)。

Android 原有测试插画母版移至 `design/illustrations/`；它们是测试内容资源，不是旧界面截图或旧原型。
