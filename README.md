<div align="center">
  <img src="design/logo/picomic-rounded.svg" width="112" height="112" alt="PiComic 图标" />
  <h1>PiComic</h1>
  <p>把喜欢的漫画，放进随身书架。</p>
  <p>面向 Android 的多来源漫画阅读器，集发现、阅读、收藏与离线下载于一体。</p>
  <p>
    <a href="https://github.com/ddmoyu/PiComic/releases/latest"><img src="https://img.shields.io/github/v/release/ddmoyu/PiComic?style=flat-square&amp;label=Release&amp;color=4560E8" alt="最新版本" /></a>
    <a href="https://github.com/ddmoyu/PiComic/releases/latest"><img src="https://img.shields.io/badge/APK-%E2%89%88%203%20MB-4560E8?style=flat-square" alt="安装包约 3 MB" /></a>
    <a href="#轻量安装与权限"><img src="https://img.shields.io/badge/%E6%9D%83%E9%99%90-%E6%8C%89%E9%9C%80%E7%94%B3%E8%AF%B7-2E7D32?style=flat-square" alt="权限按需申请" /></a>
    <a href="https://github.com/ddmoyu/PiComic/releases"><img src="https://img.shields.io/github/downloads/ddmoyu/PiComic/total?style=flat-square&amp;label=Downloads&amp;color=4560E8" alt="累计下载量" /></a>
    <a href="https://github.com/ddmoyu/PiComic/releases/latest"><img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android 8.0 及以上" /></a>
    <a href="https://github.com/ddmoyu/PiComic/releases/latest"><img src="https://img.shields.io/badge/ABI-arm64--v8a-555555?style=flat-square" alt="arm64-v8a" /></a>
    <a href="https://github.com/ddmoyu/PiComic/stargazers"><img src="https://img.shields.io/github/stars/ddmoyu/PiComic?style=flat-square&amp;label=Stars&amp;color=E8A045" alt="GitHub Stars" /></a>
  </p>
  <p>
    <a href="https://github.com/ddmoyu/PiComic/releases/latest">下载 Android APK</a> ·
    <a href="https://github.com/ddmoyu/PiComic/releases">更新日志</a> ·
    <a href="https://github.com/ddmoyu/PiComic/issues">问题反馈</a>
  </p>
</div>

## 轻量安装与权限

**约 3 MB 的安装包，轻松装进手机。** 正式版 APK 保持在几 MB 的量级；实际占用空间会随阅读缓存和离线下载增加。

**不申请定位、通讯录、相机、麦克风或全盘存储权限。** 需要你确认的授权，随相关操作出现：

| 授权 | 何时使用 |
| --- | --- |
| 通知 | Android 13 及以上加入下载时申请，用于显示下载进度。 |
| 安装应用 | 点击安装更新时，引导允许 PiComic 安装新版本。 |
| 文件与目录 | 导入、导出备份或选择下载目录时，通过系统选择器授权访问你选中的文件或目录。 |

## 为阅读而设计

从探索页发现作品，在分类中按兴趣查找，把喜欢的漫画收藏到书架。打开作品即可阅读，阅读进度会自动保存在本机，下次可以从作品详情或书架历史接着看。

| 功能 | 你可以做什么 |
| --- | --- |
| 多来源浏览 | 在六组内置漫画源之间切换，按来源搜索作品、浏览分类、查看详情与章节。 |
| 按习惯阅读 | 选择纵向连续、从左向右或从右向左阅读，使用双击缩放、音量键翻页与自动翻页。 |
| 随身书架 | 集中管理本地收藏、阅读历史与下载，恢复上次的章节、页码和滚动位置。 |
| 离线阅读 | 下载章节后离线打开，支持任务暂停、继续与下载目录选择。 |
| 个性化设置 | 调整主题、阅读显示与作品阅读偏好，按关键词和语言筛选内容。 |
| 账号管理 | 按来源管理登录状态，加密保存账号；哔咔、JM、绅士漫画可记住密码并一键重新登录。 |
| 备份与迁移 | 导出带密码保护的 `.picomic` 备份，可选择包含账号；通过 WebDAV 同步书架、历史与可迁移偏好。 |
| 应用内更新 | 在设置中检查新版本、查看更新说明并下载安装包。 |

## 支持的漫画源

内置以下六组来源，无需额外安装扩展：

- **哔咔漫画**（picacg）
- **E-Hentai / ExHentai**
- **禁漫天堂**（JMComic）
- **Hitomi**
- **绅士漫画**（Wnacg / htcomic）
- **nhentai**

各来源的登录方式、可访问内容与可用性取决于对应平台和网络环境。可在「设置 → 账号管理」登录，在「设置 → 漫画源」调整来源选项。

## 下载与使用

**系统要求：Android 8.0 及以上，64 位 ARM 设备（arm64-v8a）。**

1. 前往 [最新版本](https://github.com/ddmoyu/PiComic/releases/latest)，在 **Assets** 中下载 `.apk` 文件并安装。
2. 打开应用，选择漫画源；需要登录的来源可先在「设置 → 账号管理」中配置。
3. 在探索或分类页找到作品，进入详情阅读、收藏或下载。

已安装正式版时，可通过「设置 → 更新」获取新版本。若从旧测试签名版本迁移，请先导出备份，再卸载旧版并安装正式版。

## 让阅读记录跟着你

- **换设备**：在「设置 → 数据与同步」导出加密配置，在新设备导入，可按需一并迁移平台账号。
- **同步书架**：配置自己的 WebDAV 服务，同步书架、历史与可迁移偏好；账号凭据和下载的漫画图片不参与 WebDAV 同步。
- **调整网络**：默认跟随系统网络，支持系统 VPN / 代理，也可在「设置 → 设置代理」配置 HTTP 代理。

## 反馈与建议

遇到问题或有功能建议，欢迎提交 [Issue](https://github.com/ddmoyu/PiComic/issues)。报告问题时，请附上应用版本、Android 版本、涉及的漫画源与复现步骤，便于定位。

喜欢 PiComic 的话，欢迎点亮 **Star**，关注后续更新。
