# Phosphor 图标

来自 [官方 Phosphor Core](https://github.com/phosphor-icons/core) 的 `@phosphor-icons/core@2.1.1`，遵循同目录 MIT LICENSE。

- 全应用使用 Regular；收藏选中态使用同库 Heart Fill。
- 官方 SVG 原样保存在本目录，`manifest.json` 记录版本、包完整性和图标映射。
- `python tools/sync-phosphor-icons.py` 离线生成 Android VectorDrawable 和 HTML 图标数据；不手绘或修改路径。
- `--fetch` 按固定版本下载 npm 官方包并校验 SHA-512，不在构建时联网。
- Android 仅打包使用的 25 个矢量文件，许可同时打包到 `assets/licenses/phosphor-icons.txt`。
- UI 统一从 `Glyph` / `AppIcon` 使用图标，默认 24 dp；图标按钮点击区域 48 dp。装饰图标不重复朗读，独立按钮保留中文操作名称。
