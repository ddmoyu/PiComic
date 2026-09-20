# 更新记录

## 0.3.0

2026-09-20 · Android 8.0+ · arm64-v8a · versionCode 3000

### 简体中文

- 首个 GitHub 正式版本：整合哔咔、EH/EX、禁漫天堂、Hitomi、绅士漫画和 nhentai，支持浏览、搜索、详情与阅读。
- 提供连续阅读、左右翻页、缩放、音量键翻页和逐作品阅读偏好；支持书架收藏、历史续读、下载暂停/继续及离线阅读。
- 主题默认跟随系统，手动选择浅色或深色后保留选择；阅读背景独立生效，修复从详情进入阅读时闪黑的问题。
- 修复 Loading 进度圈尺寸、位置和旋转异常；移除应用自定义滑动返回，页面仅在系统确认返回后退出。
- 修复 JM 图片线路变化导致登录失败的问题，更新已知图片域名，并将登录验证与图片线路检测分开。
- 新增哔咔、JM、绅士漫画的「记住账号密码」和已保存账号重新登录，使用 Android Keystore 加密保存；可单独忘记密码或清除账号。已有账号需重新登录并勾选记住，才能保存密码。
- 新增密码保护的 `.picomic` 配置备份，使用 AES-256-GCM 与 PBKDF2-HMAC-SHA256，可选择包含登录会话和已记住的账号密码。导入前预览数据和账号覆盖选项，仍兼容旧 JSON 备份；WebDAV 只同步普通用户数据。
- 接入公开 GitHub Releases，网络失败或限流时自动尝试备用线路，可在设置中关闭；安装包继续验证包名、版本、SHA-256 和签名。
- 使用居中的 D7 圆周率 π 图标，支持 Android 自适应形状与单色主题图标。正式安装包由 `vX.Y.Z` 标签触发 GitHub Actions，使用固定发行证书构建并校验后发布。

**安装与账号说明**

- 下载 `PiComic-0.3.0.apk`，适用于 Android 8.0 及以上的 arm64 设备。
- 使用旧测试证书的版本需要先导出备份，再卸载重装；已使用同一正式发行证书的版本可覆盖安装。后续正式版本继续沿用此证书。
- 备份密码长度为 8–128 个字符，请妥善保存；忘记密码无法解密备份。导入的账号需要重新验证会话。
- EH/EX 和 nhentai 使用 Cookie/Token 会话，不提供原始密码记忆。各平台的验证码、账号权限与网络限制仍然适用；本版本不宣称已验证所有平台账号，nhentai 密码登录实测仍受 CAPTCHA 阻挡。

### English

- First official GitHub release: browse, search and read from Pica, EH/EX, JM, Hitomi, Wnacg and nhentai.
- Includes continuous and paged reading, zoom, volume-key navigation, per-title preferences, bookshelf favorites, reading history, resumable downloads and offline reading.
- The theme follows the system by default and remembers a manual light/dark selection. The reader background is independent, fixing the dark flash when opening the reader from details.
- Fixed loading indicator size, placement and rotation. Removed the app's custom swipe-back behavior; pages exit only when the system completes the back action.
- Fixed JM login failures caused by changed image routes, updated known image domains and separated login validation from image-route checks.
- Added encrypted password saving and sign-in with saved credentials for Pica, JM and Wnacg using Android Keystore. Forget a saved password or clear the whole account separately. Existing accounts require one sign-in with the remember option enabled.
- Added password-protected `.picomic` backups using AES-256-GCM and PBKDF2-HMAC-SHA256, with optional login sessions and remembered credentials. Preview data and account replacement choices before import. Legacy JSON import remains supported; WebDAV sync contains ordinary user data only.
- Connected public GitHub Releases with optional automatic fallback routes for network failures and rate limits. APK package, version, SHA-256 and signing certificate checks remain enforced.
- Added the centered D7 π icon with adaptive and monochrome Android variants. Only `vX.Y.Z` tags trigger GitHub Actions to build, verify and publish APKs with the fixed release certificate.

**Installation and accounts**

- Download `PiComic-0.3.0.apk` for arm64 devices running Android 8.0 or later.
- Builds signed with the old test certificate require a backup, uninstall and fresh installation. Builds using the same production certificate can be updated in place. Future official releases will retain this certificate.
- Keep your backup password (8–128 characters): a forgotten password cannot decrypt the backup. Imported account sessions require validation.
- EH/EX and nhentai use Cookie/Token sessions rather than saved raw passwords. Platform CAPTCHAs, permissions and network restrictions still apply. Not all platform accounts have been verified; the nhentai password-login test is still blocked by CAPTCHA.

### 日本語

- GitHub 初の正式リリース。Pica、EH/EX、JM、Hitomi、Wnacg、nhentai の閲覧・検索・作品詳細・読書に対応しました。
- 連続スクロールとページ送り、拡大、音量キー操作、作品ごとの読書設定、お気に入り、読書履歴、ダウンロードの一時停止・再開、オフライン読書を利用できます。
- テーマは初期状態でシステムに従い、手動で選んだライト／ダーク設定を保持します。リーダー背景を独立させ、詳細画面から移動するときに一瞬暗くなる問題を修正しました。
- 読み込み表示のサイズ・位置・回転を修正しました。アプリ独自のスワイプ戻りを削除し、システムの戻る操作が確定した時点で画面を閉じます。
- 画像配信先の変更による JM のログイン失敗を修正しました。既知の画像ドメインを更新し、ログイン確認と画像配信先の確認を分離しました。
- Pica、JM、Wnacg にアカウントとパスワードの記憶、および保存済み情報での再ログインを追加しました。Android Keystore で暗号化して保存し、パスワードのみの削除とアカウント全体の削除を選べます。既存アカウントは記憶オプションを有効にして一度ログインし直してください。
- AES-256-GCM と PBKDF2-HMAC-SHA256 による、パスワード付き `.picomic` バックアップを追加しました。ログインセッションや記憶した認証情報を任意で含められ、取り込み前にデータとアカウントの上書きを確認できます。旧 JSON の取り込みも継続し、WebDAV は通常のユーザーデータだけを同期します。
- 公開 GitHub Releases と連携しました。通信失敗やレート制限時には代替経路を自動で試し、設定で無効化できます。APK のパッケージ名・バージョン・SHA-256・署名の検証も行います。
- 中央配置の D7 π アイコンを採用し、Android のアダプティブアイコンと単色アイコンに対応しました。`vX.Y.Z` タグを作成した場合のみ GitHub Actions が動作し、固定の正式証明書でビルド・検証・公開します。

**インストールとアカウントについて**

- Android 8.0 以降の arm64 端末向けに `PiComic-0.3.0.apk` をダウンロードしてください。
- 旧テスト証明書のビルドから移行する場合は、バックアップ後にアンインストールして入れ直してください。同じ正式証明書のビルドは上書き更新できます。今後の正式版も同じ証明書を使用します。
- バックアップのパスワードは 8～128 文字です。忘れると復号できないため、大切に保管してください。取り込んだアカウントのセッションは再検証が必要です。
- EH/EX と nhentai は Cookie/Token セッションを使用し、元のパスワードは記憶しません。各サービスの CAPTCHA、権限、通信制限は引き続き適用されます。すべてのサービスのアカウントを検証済みではなく、nhentai のパスワードログイン試験は CAPTCHA により未完了です。

## 0.3.0-alpha

- 接入哔咔、EH/EX、禁漫天堂、Hitomi、绅士漫画和 nhentai 的搜索、分类、详情、章节与阅读。
- 增加平台账号管理、网页登录与会话恢复，区分登录失效、权限不足和图片额度限制。
- 增加来源线路配置、内容筛选、哔咔头像资料、哔咔打卡和禁漫签到。
- 支持连续阅读、左右翻页、长图缩放、音量键、自动翻页、亮度和逐作品阅读偏好。
- 收藏支持撤销；阅读历史支持续读、逐项删除和清空。
- 增加持久下载队列、暂停继续、目录选择、文件校验和离线阅读。
- 增加缓存容量管理、用户数据导入导出、冲突选择及 WebDAV 手动同步。
- 增加运行日志查看与导出，账号凭据不进入日志和用户备份。
- 增加 GitHub 稳定版更新检查、断点下载、安装包校验与系统安装入口。
- 改善阅读全屏、旋转恢复、图片加载状态和旧版数据迁移。

此历史开发版本尚未配置公开更新渠道和正式签名。
