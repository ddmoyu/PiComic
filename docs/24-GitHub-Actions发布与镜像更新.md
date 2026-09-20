# GitHub Actions 发布与镜像更新

## 已确认的发布方式

- 源码和安装包仓库：公开的 `ddmoyu/PiComic`。
- `.github/workflows/release.yml` 仅监听推送标签 `vX.Y.Z`，没有分支提交、PR、定时或手动打包触发器。标签还会执行严格格式校验：不接受前导零、预发布或 build 后缀。
- 一个包含 `arm64-v8a` 的完整 Release APK，最低 Android 8；启用 R8，关闭调试，使用固定专用发行证书。
- 发布文件为 `PiComic-X.Y.Z.apk`、`picomic-update.json`、`SHA256SUMS.txt`；构建校验报告及 R8 mapping 保留在 Actions artifact 14 天，不加入安装包。

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
