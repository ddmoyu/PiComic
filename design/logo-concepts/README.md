# PiComic · π 漫画图标提案

六款独立 SVG，核心符号均为圆周率 **π**。每个符号均由路径绘制，不依赖字体、外部图片或网络资源。

打开 `index.html` 对比六款图标，可切换圆角、圆形和方形裁切。页面内的 SVG 按钮可下载对应母版；`preview.png` 与 `preview-circle.png` 是静态对比图。

| 编号 | 文件 | 风格 |
| --- | --- | --- |
| 01 | `01-shonen-burst.svg` | 热血爆框：朱红 π、爆炸对白框与黄色集中线 |
| 02 | `02-manga-ink.svg` | 黑白墨绘：毛笔 π、黑白分镜与纸张底色 |
| 03 | `03-shojo-ribbon.svg` | 少女漫书页：粉色丝带 π、翻开的漫画与闪光 |
| 04 | `04-retro-pop.svg` | 复古波普：青绿、珊瑚红错版与立体奶油 π |
| 05 | `05-cyber-panels.svg` | 赛博漫格：深色分镜、电光青与几何 π |
| 06 | `06-minimal-bubble.svg` | 极简气泡：钴蓝、折角对白气泡与负形 π |

## Android 适配

- 母版 `viewBox="0 0 108 108"`，满幅不透明背景；包含 `background` 与 `foreground` 分组。
- 核心 `pi` 路径及其描边位于中央直径 66 单位安全圆内。
- 母版不预先裁圆角；对比页按中央 72 × 72 可见区域模拟启动器裁切，同时提供 48 px 预览。边缘集中线等装饰允许裁切。
- SVG 是设计母版。Android 应用正式接入时，需要转换为 VectorDrawable，并配置 AdaptiveIconDrawable 的前景、背景及单色资源；SVG 或 Windows `.ico` 文件不能直接作为 Android 自适应图标资源。
- 本批文件用于选稿，当前 App 的启动图标尚未替换。

依据：[Android Developers — Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive?hl=en)。

## 重新生成预览

在具备 `sharp` 和 `playwright`（含 Chromium）的 Node.js 环境中执行：

```powershell
node design/logo-concepts/build-preview.cjs
```

脚本检查六份 SVG 的分层与纯矢量结构，栅格测量 π 的安全区，生成自包含 HTML 和两张对比 PNG，并检查形状切换及 390 px 页面宽度。

如使用已安装的 Chrome，可先设置 `$env:PICOM_ICON_BROWSER_CHANNEL='chrome'`。
