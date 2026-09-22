# 11 · 应用更新与 GitHub 发布

设计合同：2026-09-18；发布记录更新：2026-09-22。Android 更新模块已实现，验证记录见 [22 号文档](22-更新模块与发布验证.md)。公开仓库、专用发行签名和标签发布工作流已投入使用，当前已发布 v0.3.5，见 [24 号文档](24-GitHub-Actions发布与镜像更新.md)。

## 1. 已确认与首版范围

**已确认：PiComic 通过 GitHub Releases 发布更新，App 提供“检查更新”。** 这与漫画来源的平台账号无关，不要求用户登录 GitHub。

首版基线：公开 Release 仓库、稳定版、手动检查、可选启动检查、完整 APK 下载和系统安装确认。入口在“设置 → 更新”。后台定时轮询、测试版订阅、差分更新、强制更新不纳入首版。

应用包名为 `io.github.ddmoyu.picomic`；发布仓库为已公开的 `ddmoyu/PiComic`，默认作为匿名更新渠道。正式签名使用专用发行证书。

## 2. 页面与流程

1. 顶部用紧凑信息卡显示已安装版本和 GitHub Releases 稳定版渠道；最后检查时间位于检查按钮下方。不重复展示 Logo、应用介绍或项目首页入口。
2. 点击“检查更新”：请求最新稳定 Release；区分已是最新、发现新版本、暂无可用发布、网络失败、限流、当前设备不兼容。
3. 有更新时展示目标版本、发布日期、APK 大小和更新说明。“查看 GitHub Release”打开已验证的对应版本发布页；用户可以返回并继续阅读。
4. 点击“下载更新”后展示进度，允许暂停、继续、取消。更新包独立于漫画下载队列管理，不能清理漫画下载或阅读历史。
5. 下载完成后校验文件，校验通过才显示“安装更新”；失败时保留当前版本，提示重新下载。
6. 点击安装：必要时打开 Android“允许安装未知应用”的授权页，回来后重新检查授权，再进入系统安装确认。用户取消安装时保留可用安装包，不将其标记为已安装。
7. 系统确认完成后，以安装结果和下次启动读取的真实 `longVersionCode` 判断是否升级；唤起安装器不等于安装成功。

普通页面不展示 SHA-256、API 路径等实现字段；校验失败给出直接可操作的提示。检查失败不能显示“已经是最新版本”。

## 3. GitHub Release 合同

### 3.1 仓库与发布资产

将 `releaseOwner`、`releaseRepo` 和稳定渠道作为构建配置。首版从公开仓库读取，不在 APK 中内置 PAT/GITHUB_TOKEN，也不发送漫画平台 Token/Cookie。

每个稳定版本发布：

- `tag_name`：`v<versionName>`，例如 `v1.1.0`。
- Release 标题：`PiComic <versionName>`；正文为简洁、完整的用户可见更新说明。
- 一个包含 `arm64-v8a` 的独立完整签名 APK，不把 split APK 当作独立包下载。
- `picomic-update.json`：该版本机器可读清单，与 APK 一起作为 Release asset 上传。

版本名、包名、versionCode、minSdk、ABI、文件大小与 SHA-256 均从最终签名 APK/构建输出生成，不能手工填写一份与 APK 不一致的清单。ABIs 按本项目实际构建和测试结果确定。

### 3.2 检查请求

```text
GET https://api.github.com/repos/{owner}/{repo}/releases/latest
Accept: application/vnd.github+json
User-Agent: PiComic/<installed-version>
If-None-Match: <cached-etag>    # 存在缓存时
```

API 版本头在实现时固定为 GitHub 支持并经过验证的版本。稳定版过滤 `draft == false`、`prerelease == false`，核对仓库身份。`latest` 是发行方的稳定发布入口，不能当作任意标签的最大 Android versionCode；客户端仍要比对清单版本，绝不降级。公开资源可以匿名读取。[GitHub Releases API](https://docs.github.com/en/rest/releases/releases#get-the-latest-release)

从这个 Release 的 `assets` 找唯一、已上传的 `picomic-update.json`。下载使用该对象的 `browser_download_url`，先验证官方地址，网络失败或限流时才通过内置 HTTPS 镜像传输；不接受任意镜像地址。清单里的 APK 名称必须匹配同一 Release 的唯一资产；随后使用匹配资产返回的下载 URL。不能在两次 `latest` 请求中把旧清单与新 APK 混用。

### 3.3 更新清单字段

清单为 UTF-8 JSON，设计最大 64 KiB，首版 `schemaVersion = 1`。下表是规范，不是已发布数据。

| 字段 | 类型 | 校验 |
|---|---|---|
| schemaVersion | integer | 不支持的版本显示协议不兼容 |
| tag | string | 必须等于所属 Release 的 tag_name |
| versionName | string | 用于展示，与 APK 一致 |
| versionCode | integer | 正整数；按 Android longVersionCode 数值比较 |
| packageName | string | 必须等于当前 App 的正式 applicationId |
| channel | string | 首版只接受 stable |
| artifacts | array | 至少一个独立可安装 APK |
| artifacts[].assetName | string | 唯一匹配同一 Release 中 APK 资产名 |
| artifacts[].abis | string[] | 明确包含的 ABI 集合，按设备支持顺序选取 |
| artifacts[].minSdk | integer | 当前 Android API 必须达到此值 |
| artifacts[].sizeBytes | integer | 正数，匹配 Release 元数据与最终文件大小 |
| artifacts[].sha256 | string | 64 位十六进制摘要，来自最终签名文件 |

Release 正文作为更新说明的唯一来源，不再要求清单中复制另一份；正文按安全 Markdown/文本显示，禁止执行内嵌 HTML/脚本。GitHub 自动生成的 Source code zip/tar.gz 不属于安装包。

版本比较用整数 versionCode，不能按字符串比较 `1.9` 和 `1.10`，也不能单靠发布时间判断。相同/更小版本不升级；若服务端意外把旧版标为 Latest，保留本机版本并显示“暂无更高版本”。新包不兼容当前设备时显示原因及发布页入口，不诱导安装。

## 4. 网络、缓存和失败恢复

更新客户端采用应用的 NetworkProfile：默认系统网络/VPN，可用用户选择的 HTTP 代理。更新请求与漫画请求共用路由策略，但使用独立客户端凭据作用域，不附加平台 Cookie、签名、Token。

按线路缓存 ETag，同时保存 Release ID、完整验证过的元数据及检查时间；304 且存在对应缓存时复用，无缓存的异常 304 重新无条件请求一次。GitHub 建议使用条件请求；不能承诺所有匿名 304 都不占配额。[GitHub 条件请求](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api#use-conditional-requests-if-appropriate)

相同检查操作 single-flight。读取 403/429 的 Retry-After、X-RateLimit-Remaining/Reset，区分限流和权限异常，按 API/文件下载与具体线路分别冷却，冷却期内不重复请求该线路；允许继续尝试其他可用线路。公开匿名 API 可能因共享代理出口受限；不以向用户索取 PAT 作为默认解决办法。[GitHub 速率限制](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)

| 异常 | 行为 |
|---|---|
| 超时/代理断连/TLS 错误 | 启用备用线路时按顺序尝试镜像，全部失败后提示重试；不改变用户代理或强制直连 |
| 404 | 提示“发布地址不可用或尚无版本”，不当作已是最新；诊断中记录脱敏原因 |
| 没有稳定版/缺清单/缺 APK | 暂无可用更新包，不能下载源码压缩包充数 |
| JSON 错误/清单字段不符 | 提示发布信息异常；不继续安装 |
| ABI/minSdk 不兼容 | 显示不兼容；保留当前 App |
| 下载中断/磁盘不足 | 暂停或失败，允许恢复/清理后重试 |
| 校验失败/签名不符 | 禁止进入安装；删除错误更新包，允许重新下载 |

GitHub 资产下载可能跳转到其 CDN/短期签名地址；使用经过验证的 HTTPS 跳转策略，禁止降级 HTTP、任意私网主机或凭据跨域传播。恢复下载时重新取得原 Release/资产的有效地址，不能把短期 CDN URL 当永久地址。

## 5. 下载与安装

更新任务保存 `releaseId + assetId + versionCode + expectedHash`，写入应用私有更新目录 `.part` 文件。前台展示任务状态；进程恢复时重新核对资产身份和已有文件。取消只删除此更新任务的数据。

支持 Range 时，续传要求 206、Content-Range 与偏移/总长度一致，并使用可用的强 ETag/Last-Modified 校验器；服务器返回 200 或资产已变更则从零重下，不能把完整文件追加到旧分片。安装前核验大小和 SHA-256，确认包名、versionCode、最低系统和签名身份符合发布合同；GitHub 资产 digest 可作为补充核对，缺失时仍使用清单 SHA-256。

签名信任来自已安装 App 的正式签名身份/发行时预置的信任配置，不能信任远端清单自行声明的新签名。仅解析 APK 签名元数据不等于完整验证；最终 APK 签名有效性和覆盖安装兼容性由 Android 包安装流程验证。首版保留同一正式签名；签名轮换需要单独设计 lineage 和系统兼容矩阵。

实现建议采用 `PackageInstaller.Session`，声明所需安装请求权限并处理 `STATUS_PENDING_USER_ACTION`、取消、失败与成功；API 26+ 检查 `canRequestPackageInstalls()`，未授权时只在用户点击安装后引导设置，不反复弹窗或静默安装。[PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller)、[安装请求权限](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())

若实现采用安装 Intent，则通过 FileProvider `content://` URI 和临时读取权限交给系统，不暴露应用私有文件路径或 `file://` URI。[Android 文件共享](https://developer.android.com/training/secure-file-sharing/share-file)

调起安装器后仍显示“等待系统处理”，不能提前修改当前版本。安装被取消允许重试，安装后清理旧更新文件并保持书架、历史和账号数据。

## 6. 发布操作规范

1. 完成版本功能、单元/UI 检查、lint 和 Release 构建；从上一正式版本进行覆盖安装验证，确认数据迁移。
2. 递增 versionCode，填写完整用户可见更新说明，创建对应版本标签。保管并备份正式签名密钥，禁止把密钥/PAT写入仓库或 APK。
3. 生成最终签名 APK、更新清单、摘要与本地发布验证记录。
4. 创建 Draft Release；上传全部资产后逐个读回，检查包名、版本、签名、ABI、size/hash 及说明一致性。
5. 核对完成后发布稳定 Release，设为 Latest。GitHub Actions 仅由 `vX.Y.Z` 标签触发以上流程，普通提交、PR 和本地构建不发布。
6. 公开发布后使用匿名客户端重新检查并下载，真机完成“旧版 → 新版”升级，确认权限页、安装结果和数据保留。

资产准备完整再发布，可避免客户端看见缺包 Release。若使用不可变发布策略，同一已发布版本不替换 APK；发现错误以更高 versionCode 发布修复，必要时撤下问题 Release 的 Latest 标记，不能让已更新用户自动降级。[GitHub 发布管理](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)

## 7. 模块与验收

建议 `feature/update` 管理页面和安装交互，`data/update` 管理 Release API/清单/下载；`UpdateRepository` 负责 single-flight、版本判断、任务恢复。界面只接收 `UpdateState`，不直接拼 GitHub URL。

P0 验收：最新/更高/更低版本、草稿/预发布过滤、缺资产、API 限流/304、版本清单不符、ABI/minSdk、下载暂停/取消/续传、网络切换、损坏 APK、错误包名/签名、安装权限拒绝/撤销、安装取消/失败/成功、进程恢复及升级后数据保留。

APK 下载校验、后台恢复和系统安装入口的实现及后续验证见[更新模块与发布验证](22-更新模块与发布验证.md)。

## 启动时检查更新

设置开关默认关闭；用户开启后，前台冷启动且距上次自动尝试超过 24 小时才检查，持久化时间避免失败后反复请求。有新版本只展示非阻塞提示，不自动下载或安装，不打断阅读；手动检查不受此每日间隔限制，但遵守 GitHub 限流/Retry-After 和在途请求去重。无网络安静记录失败，不能标记最新。
