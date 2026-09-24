# GitHub Actions 发布与镜像更新

## 已确认的发布方式

- 源码和安装包仓库：公开的 `ddmoyu/PiComic`。
- `.github/workflows/release.yml` 仅监听推送标签 `vX.Y.Z`，没有分支提交、PR、定时或手动打包触发器。标签还会执行严格格式校验：不接受前导零、预发布或 build 后缀。
- 三个分别包含 `arm64-v8a`、`armeabi-v7a`、`x86_64` 的独立完整 Release APK，最低 Android 8；启用 R8，关闭调试，使用固定专用发行证书。
- 发布文件为 `PiComic-X.Y.Z-arm64-v8a.apk`、`PiComic-X.Y.Z-armeabi-v7a.apk`、`PiComic-X.Y.Z-x86_64.apk`、`picomic-update.json`、`SHA256SUMS.txt`；构建校验报告及 R8 mapping 保留在 Actions artifact 14 天，不加入安装包。

### 多架构构建与更新匹配

- 正式构建使用 `-PreleaseAbiSplits=true`，通过 Android Gradle 的 [ABI 多 APK 构建](https://developer.android.com/build/configure-apk-splits) 一次生成三个独立完整包，共享同一次 R8 优化。此参数与本地单架构参数 `targetAbi` 互斥；默认开发构建保持原状。
- 三个包使用相同 `versionName`、`versionCode`、包名、minSdk 和发行证书。不按 ABI 修改 versionCode，避免设备跨架构更新时出现降级判断。
- `prepare-release.ps1 -Apk <路径数组>` 从每个实际 APK 检查签名和 native lib ABI，拒绝版本不一致或重复架构；合并生成 schemaVersion 1 更新清单，保持旧客户端兼容。
- 发布脚本强制检查三种架构齐全、文件名与实际 ABI 对应、大小和摘要不变；草稿中五项资产全部读回一致后才公开为 Latest。
- 客户端按 `Build.SUPPORTED_ABIS` 的设备优先顺序匹配清单，ARM64 优先 ARM64、32 位 ARM 选择 ARMv7、x86_64 优先 x86_64；下载恢复保留所选资产 ID，安装前再核对 APK 实际 ABI、签名与版本。

## 版本和操作

版本名来自标签去掉 `v`；`versionCode = major × 1,000,000 + minor × 1,000 + patch`。major 最大 2099，minor/patch 最大 999，结果必须大于零。例：`v0.3.0` 对应版本名 `0.3.0`、整数版本 `3000`。发布时还核对上一稳定版本，拒绝相同或降低的 versionCode。

先完成测试，在 `CHANGELOG.md` 中填写 `## X.Y.Z` 或 `## 未发布` 下的完整用户更新说明，再提交推送。下面以 `v0.3.0` 为示例，应替换为本次实际版本：

```powershell
git tag v0.3.0
git push origin v0.3.0
```

工作流验证标签与说明 → 准备 Java / SDK → 单元测试与 lint → 签名构建 → 读取 APK 生成清单 → 创建草稿 → 上传并逐个下载核对摘要 → 发布稳定版本并设为 Latest。任一校验失败不执行公开发布；中断后可以重跑同一工作流，自动继续本流程拥有的草稿。已公开的资产不会被覆盖。

普通本地开发仍为 `0.3.0-alpha`；仅传入 `-PreleaseVersionName=X.Y.Z` 才启用标签版本。PowerShell 中请将该参数整体加引号。

## 固定发行签名

用户已明确选择新建专用发行证书，并接受测试版先备份、卸载后重装。后续正式版本固定使用这套证书，覆盖升级不再更换签名。

已配置以下仓库 Secrets：

| Secret | 用途 |
|---|---|
| ANDROID_KEYSTORE_BASE64 | PKCS12 发行密钥文件 |
| ANDROID_KEYSTORE_PASSWORD | 密钥库口令 |
| ANDROID_KEY_ALIAS | 私钥别名 |
| ANDROID_KEY_PASSWORD | 私钥口令 |
| ANDROID_SIGNER_SHA256 | 预期发行证书摘要 |

证书为 RSA 4096 位，有效期 10000 天。公开证书 SHA-256：

```text
9051885a076e183a06db8b9832b4f9dcf9a053306a698f64ba413a7c9e54e07a
```

本机备份位于 `E:\ddmoyu\PiComic\artifacts\signing\`：`picomic-release.p12`、`signing-secrets.json` 和公开证书 `picomic-release.cer`。目录已被 Git 忽略；应另行离线保管密钥与口令，丢失后无法按当前同签名更新策略继续升级。不要把该目录上传为 Actions artifact 或提交到仓库。

Runner 只在临时目录恢复密钥，构建结束清理。Gradle 从 `PICOMIC_KEYSTORE_FILE`、`PICOMIC_KEYSTORE_PASSWORD`、`PICOMIC_KEY_ALIAS`、`PICOMIC_KEY_PASSWORD` 读取签名配置；缺少配置会失败，CI 不回退使用测试证书。

## 备用线路

“设置 → 更新 → 更新备用线路”默认开启，依次尝试：

| 请求 | 首选 | 备用 |
|---|---|---|
| Release 元数据 | api.github.com | gh-proxy.com |
| 清单和 APK | github.com | gh-proxy.com → ghproxy.net |

[GH-Proxy 文档](https://gh-proxy.com/docs/github-accelerator)说明 API 及 Release 资产代理；[GHProxy.net](https://ghproxy.net/)用于 Release 资产。2026-09-20 对公开 GitHub CLI 元数据和校验和附件进行了实时请求，两种备用资产线路及 GH-Proxy API 均返回 200。第三方服务可能变化，失败会保留当前应用并允许重试。

线路遵守用户当前系统网络/VPN/HTTP 代理配置，不强制改为直连。每条线路独立遵守限流冷却；换线路不会把旧分片追加到不同响应上。只接受白名单 HTTPS 地址；最终校验包名、版本、ABI、摘要和本机签名，镜像不能通过修改清单授权换签名。

## 本轮验证边界

已完成 API 26/36 各 16 项更新与下载专项测试、发布脚本测试及本地正式签名 Release 验证。2026-09-20 完成首次云端正式发布：

- [v0.3.1 Release](https://github.com/ddmoyu/PiComic/releases/tag/v0.3.1) 已公开并设为 Latest，附简体中文、英文、日文更新日志。
- [Actions 运行 35506622603](https://github.com/ddmoyu/PiComic/actions/runs/35506622603) 成功；标签源码为 `56da240`，75 项单元测试、6 项发布脚本测试通过，Release lint 为 0 errors、52 warnings、1 hint。
- `PiComic-0.3.1.apk`：版本名 `0.3.1`、versionCode `3001`、包名 `io.github.ddmoyu.picomic`、最低 API 26、仅 `arm64-v8a`，大小 3,226,133 字节。
- APK SHA-256：`5bd64339a6583abb6829d6d813553a4f3b207ab85a91edacb1255e92aa1c4617`。独立匿名下载后，摘要、包信息和正式发行证书均与云端报告一致，v2 签名验证通过。
- 公开 `latest` API、APK、更新清单、`SHA256SUMS.txt` 均已读回验证；GH-Proxy 的元数据与更新清单、GHProxy.net 的更新清单均与本次正式发布一致。此次没有重复下载镜像 APK。
- 本地读回证据位于忽略目录 `artifacts/github-release/v0.3.1/`；云端验证报告、测试报告及 mapping 保存在对应 Actions artifact。

先前 `v0.3.0` 运行因 `sdkmanager` 未在 PATH 中而终止，未产生 Release。已修正为 SDK 内绝对工具路径和 `platforms;android-37.0`，保留失败标签，使用新标签 `v0.3.1` 发布。普通文档提交仍不会触发打包。

## v0.3.2 发布验证

2026-09-20 通过 `v0.3.2` 标签发布，源码提交为 `02e43cb`；[Actions 运行 35515205730](https://github.com/ddmoyu/PiComic/actions/runs/35515205730) 成功，[Release](https://github.com/ddmoyu/PiComic/releases/tag/v0.3.2) 已公开并设为 Latest，包含简体中文、英文、日文更新日志。

- 发版前完成 143 项 API 36.1 Android 回归、92 项单元测试和 6 项发布脚本测试，全部通过。Android 测试在无窗口模拟器中使用测试数据运行，覆盖账号、分类和排序、列表、详情缓存、阅读预加载与重试、下载、备份、系统返回及更新控制；未进行旧 Android 或真实平台账号登录测试。
- 本地 Release lint 为 0 errors、48 warnings、1 hint；云端重新执行 92 项单元测试和 6 项发布脚本测试，Release lint 为 0 errors、52 warnings、1 hint，随后构建并验证正式签名 APK。
- `PiComic-0.3.2.apk`：版本名 `0.3.2`、versionCode `3002`、包名 `io.github.ddmoyu.picomic`、最低 API 26、仅 `arm64-v8a`，大小 3,258,905 字节。
- APK SHA-256：`f8c769c1ffad31a6b11c22338c357c1f2211d2f7b68a8204dffa3c143ff01ad1`。下载后独立检查 APK 包信息、ABI、v2 签名和正式发行证书，均与云端报告一致；更新清单、GitHub 资产摘要和 `SHA256SUMS.txt` 全部匹配。
- 当前网络出口匿名访问 GitHub `latest` API 返回 403 限流；GH-Proxy `latest` API 返回 200，并正确提供 `v0.3.2`。GitHub、GH-Proxy、GHProxy.net 的版本更新清单均返回 200，内容完全一致；本轮未重复下载镜像 APK。经认证的 GitHub `latest` 也确认新版本及三语言日志。
- 本地证据位于忽略目录 `artifacts/github-release/v0.3.2/`，Android 回归日志为 `artifacts/v0.3.2-android-tests.log`；云端验证报告、单元测试报告、lint 和 mapping 保存在 `verification-v0.3.2` Actions artifact。

实体手机覆盖升级及数据保留仍需实际设备验证，云端构建和文件校验不替代此项验收。

## v0.3.3 发布验证

2026-09-20 通过 `v0.3.3` 标签发布，源码提交为 `96f4267`；[Actions 运行 35519101025](https://github.com/ddmoyu/PiComic/actions/runs/35519101025) 成功，[Release](https://github.com/ddmoyu/PiComic/releases/tag/v0.3.3) 已公开并设为 Latest，附简体中文、英文、日文更新日志。

- 本轮包含单列虚拟章节目录、跳过详情页与历史续读、分类分组、紧凑详情页和 JM 重复章节排序号兼容；章节列表已用 5,000 章验证滚动、不可见条目回收及末章点击。
- 发版前完成当前 API 36.1 的 Android 全量回归，报告计数为 177 项。发现的 3 项失败来自更新页旧断言与主题弹窗过渡时序，修正测试后相关用例均复测通过；外网来源探测和 SAF 授权用例按显式参数跳过。本轮未进行旧 Android、真实账号登录或实体手机覆盖安装测试；全部模拟器测试无窗口运行。
- 本地与云端均通过 92 项单元测试和 6 项发布脚本测试。Release lint 本地为 0 errors、48 warnings、1 hint，云端为 0 errors、52 warnings、1 hint。
- `PiComic-0.3.3.apk`：版本名 `0.3.3`、versionCode `3003`、包名 `io.github.ddmoyu.picomic`、最低 API 26、仅 `arm64-v8a`，大小 3,258,901 字节。
- APK SHA-256：`8a302eb325dafc3dd6581ec6d0f71e8a21761a704e7dfc80e52ab40082bb1d8e`。匿名下载后的大小、摘要、包信息、ABI、v2 签名及固定发行证书均与云端验证报告匹配；更新清单、GitHub 资产摘要和 `SHA256SUMS.txt` 一致。
- GitHub 与 GH-Proxy 的匿名 `latest` API 均返回 200，指向 `v0.3.3`；GitHub、GH-Proxy、GHProxy.net 的更新清单全部返回 200 且内容一致。本轮未重复下载镜像 APK。
- 证据保存在忽略目录 `artifacts/github-release/v0.3.3/`；Android 日志为 `artifacts/v0.3.3-android-tests.log`、`artifacts/v0.3.3-android-retest.log`、`artifacts/v0.3.3-settings-retest.log`；云端报告和 mapping 位于 `verification-v0.3.3` Actions artifact。

## v0.3.4 发布验证

2026-09-22 通过 `v0.3.4` 标签发布，源码提交为 `5244842`；[Actions 运行 35701989943](https://github.com/ddmoyu/PiComic/actions/runs/35701989943) 成功，[Release](https://github.com/ddmoyu/PiComic/releases/tag/v0.3.4) 已公开并设为 Latest，附简体中文、英文、日文更新日志。

- 本轮将确认后的 HTML 方案移植到 Android：紧凑作品列表和分类、详情顶栏刷新、统一标签文案、设置分组及 Phosphor 图标；保留现有功能和导航。也包含忘记密码入口改用系统默认浏览器。UI 实现范围及验证见 [28 号文档](28-当前UI视觉实现与验证.md)。
- 发版前 92 项单元测试、6 项发布脚本测试通过；32 项 Android 定向 UI 回归最终通过，另完成竖屏和横屏各 18 张截图检查，以及 HTML 的 54 组宽度/页面组合检查。使用无窗口模拟器和安全合成内容，没有在办公桌面显示漫画页面。
- 云端重新执行 92 项单元测试和 6 项发布脚本测试，均通过；Release lint 本地为 0 errors、49 warnings、1 hint，云端为 0 errors、53 warnings、1 hint。
- `PiComic-0.3.4.apk`：版本名 `0.3.4`、versionCode `3004`、包名 `io.github.ddmoyu.picomic`、最低 API 26、仅 `arm64-v8a`，大小 3,274,865 字节。
- 正式发布 APK SHA-256：`efe9c0cd9e2e5b9f7040a5ecc34881bab7decb4dbadfa5f6624a6a9f4186a13f`。匿名下载后独立检查包信息、ABI、v2 签名、固定发行证书及 16 KB zipalign，均通过；大小和摘要与更新清单、GitHub 资产摘要、`SHA256SUMS.txt` 及云端报告一致。本地预发布构建与云端 APK 分开留存，不混用摘要。
- GitHub 与 GH-Proxy 的匿名 `latest` API 均返回 200，指向 `v0.3.4`；GitHub、GH-Proxy、GHProxy.net 的更新清单均返回 200，versionCode 为 3004 且内容一致。本轮未重复下载镜像 APK。
- 在 API 36.1 的一次性无窗口 x86_64 模拟器中，通过 ARM64 转译安装实际公开的 v0.3.3 APK，再以 `adb install -r` 覆盖安装实际公开的 v0.3.4 APK。安装成功，启动后通过界面确认深色主题和“跳过详情页”设置保留，崩溃缓冲区为空；已安装 `base.apk` 的 SHA-256 与公开 APK 完全一致。该检查不代表实体手机、真实登录凭据或已填充书架数据库的迁移验收。
- 公开资产与验证记录位于忽略目录 `artifacts/github-release/v0.3.4/`，包括 `public-verification.json`、`public-routes.json`、`upgrade-verification.json` 和升级界面日志；本地预发布验证位于 `artifacts/release-v0.3.4-local/`。云端报告和 mapping 位于 `verification-v0.3.4` Actions artifact。

## v0.3.5 发布验证

2026-09-22 通过 `v0.3.5` 标签发布，源码提交为 `eaf2763`；[Actions 运行 35705352999](https://github.com/ddmoyu/PiComic/actions/runs/35705352999) 成功，[Release](https://github.com/ddmoyu/PiComic/releases/tag/v0.3.5) 已公开并设为 Latest，附简体中文、英文、日文更新日志。

- 修复哔咔、JM、绅士漫画已保存凭据却反复要求登录的问题：优先恢复会话，明确过期后自动登录一次并重试读取，多个请求共享恢复；详见 [29 号文档](29-账号会话自动恢复与验证.md)。
- 104 项单元测试、49 项不同 Android 回归用例最终通过；云端重新执行 104 项单元测试和 6 项发布脚本测试，均通过。Release lint 本地为 0 errors、49 warnings、1 hint，云端为 0 errors、53 warnings、1 hint。
- `PiComic-0.3.5.apk`：版本名 `0.3.5`、versionCode `3005`、包名 `io.github.ddmoyu.picomic`、最低 API 26、仅 `arm64-v8a`，大小 3,274,865 字节。
- 正式发布 APK SHA-256：`326c48327b1cac655444ad810c38017f7756df67f48820253bc389aa05e0ed2e`。匿名下载并独立验证包信息、ABI、v2 签名、固定发行证书和 16 KB zipalign；摘要与 GitHub 资产、更新清单、`SHA256SUMS.txt` 及云端报告一致。R8 mapping 确认包含本次新增会话恢复组件。
- 当前出口匿名访问 GitHub `latest` API 遇到 403 限流；应用默认备用线路 GH-Proxy 返回 200，指向 v0.3.5。GitHub、GH-Proxy、GHProxy.net 的更新清单均返回 200，内容完全一致且 versionCode 为 3005；经认证的 GitHub `latest` 同样确认 v0.3.5。本轮未重复下载镜像 APK。
- 在 API 36.1 无窗口模拟器中，安装实际公开的 v0.3.4，再以实际公开的 v0.3.5 覆盖升级成功；深色主题和跳过详情页设置保留，启动未记录崩溃，已安装 APK 摘要与公开资产一致。使用 x86_64 模拟器的 ARM64 转译，没有测试实体手机或用户真实平台账号。
- 证据保存在忽略目录 `artifacts/github-release/v0.3.5/`；云端报告及 mapping 位于 `verification-v0.3.5` Actions artifact。模拟器验证完成后关闭，全部过程无窗口运行。

## v0.3.6 发布验证

2026-09-22 通过 `v0.3.6` 标签发布，源码提交为 `16857c4`；[Actions 运行 35714715073](https://github.com/ddmoyu/PiComic/actions/runs/35714715073) 成功，[Release](https://github.com/ddmoyu/PiComic/releases/tag/v0.3.6) 已公开并设为 Latest，包含三语言更新说明。

- 来源顺序统一为 Hitomi、禁漫天堂、绅士漫画、nhentai、E-Hentai / ExHentai、picacg，JM 固定第二个。探索、分类、搜索、账号管理和当前 HTML 展示同步；首次使用默认 Hitomi。显示顺序独立于枚举身份，已有选择按来源名称恢复。
- 本地和云端均通过 104 项单元测试，云端 6 项发布脚本测试通过。7 项 Android 检查最终通过，覆盖来源点击/滑动、搜索返回、分类归属、登录入口、书架旋转和安全截图采集。首轮书架旋转在模拟器关闭系统动画时等待未结束，中止后恢复默认动画倍率，原用例复测通过，未改动书架产品代码。
- 调整前后台采集当前界面，调整后以安全合成数据重新采集原生页面 18 张。记录位于 `artifacts/source-order/`，含截图、检查日志及 `verification.json`；原型生成沿用明确标注的历史对照基线，不把旧图标为本轮新图。没有在办公桌面打开模拟器或显示漫画内容。
- Release lint 本地为 0 errors、49 warnings、1 hint，云端为 0 errors、53 warnings、1 hint。
- `PiComic-0.3.6.apk`：版本名 `0.3.6`、versionCode `3006`、最低 API 26、仅 `arm64-v8a`，大小 3,274,865 字节；SHA-256 为 `0062667ba66204ae34de9b6b8b21584ce94920cd633c2563b1aab50e0e90346a`。
- 匿名下载后的包信息、v2 签名、固定发行证书及 16 KB zipalign 检查通过；摘要与 GitHub 资产、更新清单、校验和文件及云端报告一致。GitHub / GH-Proxy 最新版本 API 和 GitHub / GH-Proxy / GHProxy.net 更新清单均返回 200，指向 v0.3.6，清单内容一致。
- 在无窗口 API 36.1 模拟器中，由实际公开的 v0.3.5 覆盖安装实际公开的 v0.3.6 成功，原选中 picacg、深色主题和跳过详情页设置保留，崩溃缓冲区为空，已安装 APK 摘要匹配公开资产。未进行实体手机验证；完成后关闭模拟器。
- 公开资产和读回证据位于 `artifacts/github-release/v0.3.6/`，云端报告与 mapping 位于 `verification-v0.3.6` Actions artifact。

## v0.3.7 多架构发布验证

- 本地签名构建输出 ARM64、ARMv7、x86_64 三个独立完整 APK，均为 versionCode 3007、minSdk 26、同一发行证书；每包均通过 v2 签名、实际 native lib ABI、大小/摘要及 16 KB zipalign 检查。
- 107 项 JVM 单元测试、8 项发布脚本测试、16 项 Android 更新模块测试通过，覆盖 ABI 优先级、32 位 ARM 选择、无匹配架构、最低系统限制、旧清单兼容、多包清单解析、下载恢复、镜像与签名检查。真实 APK 混入旧版本或重复架构时，准备脚本均拒绝且不生成资产。
- Release lint 为 0 errors、49 warnings、1 hint。无窗口 API 36.1 模拟器由公开 v0.3.6 覆盖安装本地 v0.3.7 ARM64 包，再切换同签名 x86_64 包，均安装并启动成功，原深色主题和跳过详情页设置保留，崩溃缓冲区为空。
- ARMv7 完成构建、签名、ABI、清单匹配与静态校验，本轮没有可运行 ARMv7 的实体设备。ARM64 运行检查使用模拟器转译；x86_64 使用原生运行。发布后的公开文件另行读回验证，不混用本地与云端摘要。
- 本地证据位于 `artifacts/release-v0.3.7-local/`、`artifacts/v0.3.7-local-build.log`、`artifacts/v0.3.7-android-tests.log` 及 `artifacts/v0.3.7-local-upgrade-*.log`。

### 公开发布与实际旧版更新

2026-09-22 通过 `v0.3.7` 标签发布，源码提交为 `2ac2ff6`；[Actions 运行 35716702721](https://github.com/ddmoyu/PiComic/actions/runs/35716702721) 成功，[Release](https://github.com/ddmoyu/PiComic/releases/tag/v0.3.7) 已公开并设为 Latest，包含简体中文、英文、日文更新说明。

| 公开 APK | 字节数 | SHA-256 |
|---|---:|---|
| `PiComic-0.3.7-arm64-v8a.apk` | 3,274,865 | `796512dae49f16e6adb5cd285d84784fabb8279dfc851c903acafd91ede6f5d1` |
| `PiComic-0.3.7-armeabi-v7a.apk` | 3,272,023 | `67afe8af3c512a7b3ac5eba8006c16f3cbb640cd0b421f47233aec3e42136131` |
| `PiComic-0.3.7-x86_64.apk` | 3,275,526 | `43f8149da2227613d978eec9392b9eecbfa0981d47cb1a7cc470ae41c0313833` |

- 云端 107 项 JVM 测试和 8 项发布脚本测试通过；Release lint 为 0 errors、53 warnings、1 hint。匿名下载全部五项资产，三个 APK 的包名、版本、minSdk、native lib ABI / ELF 指令集、v2 签名、发行证书、摘要和 16 KB zipalign 均通过，与清单和云端报告一致。
- 当前桌面出口匿名 GitHub `latest` API 返回 403 限流；GH-Proxy `latest` 返回 200 并指向 v0.3.7；GitHub、GH-Proxy、GHProxy.net 清单均为 200 且内容一致。经认证的 GitHub `latest` 同样确认 v0.3.7。
- 未修改的公开 v0.3.6 在 API 36.1 无窗口模拟器中完成应用内检查、下载、校验及系统安装。设备 ABI 顺序为 `x86_64,arm64-v8a`，原安装包为 ARM64，新版自动选择 x86_64；安装后从设备读回 `base.apk`，摘要与公开 x86_64 APK 完全一致。系统 Play Protect 扫描通过后完成安装，未关闭该保护。
- 升级后深色主题和跳过详情页设置保留，启动崩溃缓冲区为空；新版重新检查显示“暂无更高版本”。此链路通过 App 和系统安装器完成，未用 `adb install` 替代应用内升级。
- 首次模拟器直连 API 返回 HTTP 301，目标为 `github.com/repos/...`，旧客户端按既有规则拒绝跳转；独立 Android 网络探针复现。临时将模拟器系统代理指向办公电脑现有代理后，真实更新链路通过。测试结束恢复原代理设置并关闭模拟器；该结果不表示直连网络跳转已在产品中修复。
- 公开资产、CI 报告、旧版与新版界面 XML、安装结果和摘要证据保存在 `artifacts/github-release/v0.3.7/`，主要记录为 `public-verification.json`、`public-routes.json`、`upgrade-verification.json`。未进行 ARMv7 或其他架构实体手机验证。

## v0.3.8 发布验证

2026-09-24 通过 `v0.3.8` 标签发布，源码提交为 `520d40b`；[Actions 运行 35944379199](https://github.com/ddmoyu/PiComic/actions/runs/35944379199) 成功，[Release](https://github.com/ddmoyu/PiComic/releases/tag/v0.3.8) 已公开并设为 Latest，包含简体中文、英文、日文更新说明。

本版增加哔咔零输入一键注册、完整资料复制、加密保存与登录恢复，完善原生登录页密码显隐、清空及自动填充，修正服务端限流提示。功能及验证边界见 [一键注册规格](30-哔咔一键注册与自动登录.md)。

| 公开 APK | 字节数 | SHA-256 |
|---|---:|---|
| `PiComic-0.3.8-arm64-v8a.apk` | 3,292,989 | `5b85a4d7a9515869dcb9b2cf21e580f7d51b21d4960ac21837c9247aad239732` |
| `PiComic-0.3.8-armeabi-v7a.apk` | 3,290,147 | `30f8490a428c5d7f5361699879062d20dd9feeb7016a084390a4c5b999d18d31` |
| `PiComic-0.3.8-x86_64.apk` | 3,293,650 | `d10c4aea789047036bee2bfc2cec6ce5ed66d390be2f2f7eb09b5985b18f643e` |

- 本地与云端均通过 117 项 JVM 测试和 8 项发布脚本测试；发版前 17 项 Android 注册、登录及接口测试通过。云端 Release lint 为 0 errors、54 warnings、1 hint。
- 匿名下载全部五项公开资产，逐包核对版本 `0.3.8 (3008)`、包名、minSdk 26、非调试构建、native lib ABI 与 ELF 指令集、固定发行证书、v2 签名和 16 KB zipalign，全部通过；大小与摘要匹配 GitHub 资产、更新清单、校验和文件及云端报告。
- GitHub 与 GH-Proxy 的匿名 `latest` API、GitHub / GH-Proxy / GHProxy.net 的更新清单均返回 200，并与 v0.3.8 一致。
- 在独立 API 36.1 无窗口 x86_64 模拟器中，公开 v0.3.7 通过应用内检查、下载、校验及 Android 系统安装器升级到 v0.3.8。Play Protect 扫描通过后安装，未关闭该保护；实际安装 `base.apk` 的摘要与公开 x86_64 APK 完全一致。
- 升级后深色主题和跳过详情页设置保留，正式优化包的一键注册、密码显隐入口可见，崩溃缓冲区为空；新版再次检查显示“暂无更高版本”。未调用真实平台注册接口，未验证实体 ARM64 / ARMv7 手机。
- 测试使用中性空账号页面和设置页；模拟器全程无窗口，来源页面联网在操作前阻止，更新页使用现有代理。完成后恢复代理并关闭模拟器。原有测试模拟器的数据保留。
- 公开资产、CI 报告、签名核验、界面 XML、安装结果与摘要证据保存在 `artifacts/github-release/v0.3.8/`，主要记录为 `public-verification.json`、`public-routes.json`、`elf-verification.json` 和 `upgrade-verification.json`。

## v0.3.9 发布验证

2026-09-24 通过 `v0.3.9` 标签发布，源码提交为 `668d0db`；[Actions 运行 35976820781](https://github.com/ddmoyu/PiComic/actions/runs/35976820781) 成功，[Release](https://github.com/ddmoyu/PiComic/releases/tag/v0.3.9) 已公开并设为 Latest，包含简体中文、英文、日文更新说明。

本版修正账号密码登录的保存时机：默认启用“记住账号密码”时，点击登录就加密保存本次输入；无论认证成功或失败，下次打开或重启后自动填充。填写记录与已验证会话分开保存，失败登录保留原会话和会话恢复密码；“忘记密码”与“清除本地账号”会删除填写记录。影响哔咔、JM 与绅士漫画。

| 公开 APK | 字节数 | SHA-256 |
|---|---:|---|
| `PiComic-0.3.9-arm64-v8a.apk` | 3,292,989 | `65727b3ac9844be1eb4e4dad8a2b1141093547da84b3157b439ca3db58de4c5a` |
| `PiComic-0.3.9-armeabi-v7a.apk` | 3,290,147 | `2f909a7f4a1385341cdd9869163ecbbe3daadf638a0bae17d31a0ce535591f3e` |
| `PiComic-0.3.9-x86_64.apk` | 3,293,650 | `2d0de39c45d22cd3cfa637a3c66db37dc6dbe93bdf45677f8fbe2d7a402eaceb` |

- 发布工作流中的 JVM 测试、发布脚本测试、Release lint、三架构正式签名构建、资产生成及上传后读回全部成功。本地 120 项 JVM 测试和 8 项发布脚本测试通过；云端 Release lint 为 0 errors、54 warnings、1 hint。
- 匿名下载五项公开资产，逐项检查大小和 SHA-256；更新清单、校验和与 APK 内容一致。三包版本为 `0.3.9 (3009)`、minSdk 26，使用固定发行签名，分别只包含目标 ABI；签名、v2 和 16 KB zipalign 检查通过。公开摘要与清单和发布资产相符。
- API 36.1 无窗口 x86_64 模拟器通过 v0.3.8 应用内检查、下载与 Android 系统安装器升级到 v0.3.9；Play Protect 扫描通过。升级后深色主题和“跳过详情页”保留；从系统读回的 `base.apk` 与公开 x86_64 包的 SHA-256 完全一致。启动日志无 PiComic 崩溃记录。
- 发版前 5 项 Android 登录页测试通过，涵盖三来源失败后重启回填和取消后的保留；另使用独立 API 36.1 无窗口模拟器验证公开升级。未在实体 ARM64 或 ARMv7 设备验证。
- 公开资产、CI 校验报告、匿名读回摘要和安装升级证据保存在 `artifacts/github-release/v0.3.9/`，包括 `public-verification.json` 与 `upgrade-verification.json`。
