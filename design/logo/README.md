# PiComic 正式图标 · D7

2026-09-20 确定使用第三轮的 **D7 轻横平切**：保留 D 的双腿和右侧弯尾，去掉左端下钩，横画减细。采用选稿预览中的钴蓝底色 `#4560E8` 与白色 π。

- `picomic.svg`：108 × 108 自适应图标设计母版。
- `picomic-mark.svg`：透明背景的独立 π 字形。
- `picomic-rounded.svg`：圆角展示版，与原型标识一致。
- `preview.png`：圆角展示 PNG。

π 为路径轮廓，无字体依赖。字形轮廓和缩放系数保持与已确认的 D7 一致。依据后续居中反馈，整体向右移动 2.88407 个母版单位，使实际轮廓的左右留白一致；不改变字形和大小。

## 应用资源

- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`：Android 8 及以上自适应启动图标。
- `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`：增加 Android 13 及以上单色主题图标。
- `app/src/main/res/drawable/ic_launcher_foreground.xml`：透明前景，彩色和单色主题共用同一 π 轮廓。
- `app/src/main/res/drawable/ic_launcher.xml`：关于页使用的圆角矢量图标。
- `app/src/main/res/drawable/ic_stat_picomic.xml`：下载通知使用的透明单色小图标。
- `prototype/assets/icon.svg`：原型标识与页面图标。

启动器负责裁切外轮廓；自适应母版不预制圆角。设计依据：[Android Developers — Adaptive icons](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive?hl=en)。
