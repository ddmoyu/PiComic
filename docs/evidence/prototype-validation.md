# HTML 原型验证

日期：2026-09-18T02:56:21.607Z。浏览器：Chrome 152.0.7977.83。

- 通过：首页加载：9 部示意作品、无外部资源
- 通过：探索：固定标题/搜索/设置及平台 Tab，底部仅图标且保留无障碍名称
- 通过：分类：六个平台独立分类、切换保持分类页/滚动位置、分类结果与搜索/设置返回正确、键盘切 Tab
- 通过：搜索重设计：六源 Tab、切源保留输入并检索、详情来源与返回保持、清空/无历史/无结果，无推荐书目，360–430 宽度按钮单行
- 通过：搜索 → 详情 → 收藏状态
- 通过：阅读：跳页、缩放、RTL 方向、连续模式、保存进度
- 通过：下载：入队、暂停、继续、取消确认可退出
- 通过：账号：NH 模拟 Key 登录、敏感输入不持久化、Hitomi 无需登录
- 通过：账号管理：平台分组/资料/会话折叠/重新登录/退出；WebView 自动获取后验证成功返回、缺失/失败/取消不覆盖旧会话、跨平台隔离
- 通过：网络：系统默认、HTTP 表单、口令不保存、线路切换
- 通过：六来源选择、当前来源展示、线路配置按来源隔离
- 通过：四种异常状态、深色文字颜色与刷新持久化
- 通过：视口检查：360×800、390×844、430×932、800×1024、844×390，无横向溢出、图片完整
- 通过：面板键盘焦点与 Escape 关闭
- 通过：检查更新：统一设置入口、版本/说明/发布页、最新/失败/限流/无发布/不兼容、暂停/继续/取消、校验与下载失败阻止安装、安装权限与系统交接演示不伪造升级
- 通过：无 JavaScript 页面异常，无外部网络请求
- 通过：file:// 直接打开可用

## 截图

[整体评审](prototype-overview.png) · [探索](prototype-home.png) · [分类](prototype-categories.png) · [搜索](prototype-search.png) · [详情](prototype-detail.png) · [阅读](prototype-reader.png) · [账号管理](prototype-accounts.png) · [网页登录](prototype-web-login.png) · [网络](prototype-network.png) · [检查更新](prototype-updates.png) · [深色](prototype-dark.png) · [手机全屏](prototype-mobile.png) · [宽窗口](prototype-wide.png)

## 未验证范围

- 仅浏览器 HTML 原型验证
- 真实平台登录/取图、Android 代理路由与性能尚未实测
- GitHub 发布、真实 APK 下载/校验/系统安装尚未实现
- 浏览器缩放不是 Android sp 字体缩放验收

机器记录：[prototype-validation.json](prototype-validation.json)。
