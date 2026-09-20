# 更新记录

## 未发布

### 简体中文

- 作品列表右侧单独显示“共 N 张图片”，适用于发现、分类结果、收藏、历史和下载列表；仅显示来源已提供的有效总数，缺失或为零时隐藏。补上 EH 列表页数读取，不增加逐本详情请求，也不把搜索结果总数或单章下载进度当作整本图片数。
- 强化平台登录反馈：手动登录或导入凭据验证成功后，弹窗显示平台与账号，并收起键盘、返回页首。登录页增加醒目的账号状态卡片，账号管理页显示“已登录”标识；自动验证旧会话不重复弹窗，失败或过期不会误报成功。
- 分类结果页增加各平台原生排序：JM 的最新、总/月/周/日排行、最多图片和最多喜欢；哔咔的新旧、喜欢数和浏览数；nhentai 的最新与各时段热门；Hitomi 的收录/发布时间、日/周/月/年热门和随机。切换排序保留分类并回到第一页，翻页沿用所选排序，返回页面保留选择和位置。EH、绅士漫画沿用官网分类默认顺序。
- 哔咔、JM、EH、绅士漫画和 nhentai 登录页新增“忘记密码”，在应用内网页打开平台找回或账号入口，支持网页后退、重新加载与返回登录；哔咔网页未提供找回选项时提示使用官方客户端。
- 调整作品详情布局：ID、作者和分类标签分组展示，下载与阅读按钮左右等宽排列，章节采用双列按钮；保留收藏、续读和已下载提示，不显示分享与评论入口。
- 发现、分类结果、收藏、下载和阅读历史统一为左封面、右信息的列表，标题最多显示两行，并展示已有的作者、分类和来源信息；分类入口仍保留标签布局，下载控制与历史删除操作继续可用。
- 更新页面的操作按钮固定在底部，长更新日志不再影响下载操作；按钮内显示下载进度，支持原位暂停、断点继续和取消更新。
- 从阅读返回详情时复用临时缓存，保留目录位置并更新阅读进度；详情和阅读不再重复获取相同资料，支持手动刷新。
- 图片加载改为低调的浅色细线动画，提示随长图的可见区域移动；错误文字和重试按钮适配阅读背景。点击重试按当前可见图片及后续预加载张数恢复失败任务，保留成功和正在加载的图片，不重试整章或之前的失败页。
- 修复翻页反复取消预加载的问题：持续补齐可见区域之后设定数量的图片，翻页保留已开始的下载，快速跳页只移除过时的排队任务；等待图片时显示加载提示。
- 修复预加载选项和阅读设置弹层需要返回两次的问题，返回一次直接关闭当前弹层。
- 修复 JM 随机切换到 Tencent 图片线路时，详情和阅读页偶发误报“格式改变”的问题；不支持的图片线路改为明确提示切换分流。
- 修复 JM 分类接口中默认目录空 slug 导致整页加载失败的问题，完整读取主目录、子目录和标签分组；同名子目录按所属主目录区分，点击标签分类使用对应搜索请求。
- 补齐 EH、绅士漫画、Hitomi 和 nhentai 的目录类型，并为新增类型接入对应筛选参数；公开固定目录不再被账号会话失效阻挡。哔咔继续读取账号接口返回的完整分类列表。
- 分类页保留来源原有目录，不新增“所有分类”入口；空响应显示重新加载提示，避免页面空白。

### English

- Show a separate total image count on the right of discovery, category results, favorites, history and download rows when supplied by the source. Hide missing or zero counts, read EH list metadata, and avoid extra detail requests or confusing result totals and chapter download progress with the book's image count.
- Made sign-in results clearer with a confirmation showing the platform and account after a successful login or credential import. Dismiss the keyboard and return to the top, keep a prominent account-status card and signed-in badge, and avoid repeated confirmations during session restoration or false success on failures and expiry.
- Added source-specific sorting to category results: all seven JM orders, Pica date/likes/views, nhentai date and popularity periods, and Hitomi added/published dates, daily/weekly/monthly/yearly popularity and random order. Changing order retains the category and restarts pagination; subsequent pages and restored screens keep the selection. EH and Wnacg retain their native category order.
- Added “Forgot password” to Pica, JM, EH, Wnacg and nhentai login pages. Recovery or account pages open inside the app with back, reload and return-to-login controls. Pica users are directed to the official client if web recovery is unavailable.
- Redesigned details with grouped ID, author and category tags, equal-width Download and Read buttons, and two-column chapter buttons. Favorites, reading progress and downloaded indicators remain available, without share or comment actions.
- Discovery, category results, favorites, downloads and reading history now use rows with a cover on the left and details on the right. Titles are limited to two lines, with available author, category and source metadata. Category selection keeps its chip layout, and download controls and history deletion remain available.
- Update actions now stay at the bottom while release notes scroll. Download progress appears inside the button, with pause, resume and cancel controls always within reach.
- Returning from the reader restores cached details and the chapter-list position while updating reading progress. Details and reading share the same temporary cache, with a manual refresh action.
- Added a subtle loading animation that stays within the visible part of long images, with error text and retry controls adapted to the reader background. Retry failed images in the current visible area and configured look-ahead window, preserving successful and ongoing loads without retrying earlier failures or the whole chapter.
- Fixed page turns repeatedly cancelling preloads. Keep the configured number of images ahead of the visible area, finish started downloads and discard only outdated queued work after jumps. Show a loading indicator while images are pending.
- Fixed preload choices and reader settings requiring two back actions. One back action now dismisses the current sheet.
- Fixed intermittent “format changed” errors when JM rotates to its Tencent image route. Unsupported image routes now display a specific message suggesting another image route.
- Fixed JM's empty slug for its default directory, which previously caused the entire category page to fail. Load all parent directories, child directories and tag groups, keep same-named children distinct, and route tag selections to search.
- Completed directory types for EH, Wnacg, Hitomi and nhentai with matching filters. Public fixed directories no longer depend on a valid account session. Pica continues to load the full category list returned by its authenticated API.
- Retained source-provided directories without adding an “All categories” shortcut. Empty category responses now show a retry action.

### 日本語

- 発見、分類結果、お気に入り、履歴、ダウンロードの各リスト右側に、配信元から取得できた画像の総数を表示します。未取得や0件の場合は非表示とし、EH の一覧メタデータにも対応しました。追加の詳細リクエストは行わず、検索結果件数や章のダウンロード進捗と区別します。
- ログインや認証情報の取り込みが成功すると、配信元とアカウントを確認ダイアログで表示し、キーボードを閉じて画面先頭へ戻ります。ログイン状態カードと「ログイン済み」表示を追加し、保存済みセッションの自動確認では繰り返し通知せず、失敗や期限切れを成功と表示しません。
- 分類の結果画面に配信元ごとの並び替えを追加しました。JM の7種類、Pica の新旧・いいね・閲覧数、nhentai の新着・期間別人気、Hitomi の追加日・公開日・日/週/月/年の人気・ランダムに対応します。変更時は分類を維持して先頭ページへ戻り、ページ送りと画面復帰でも選択を保持します。EH と Wnacg は公式の分類順を使用します。
- Pica、JM、EH、Wnacg、nhentai のログイン画面に「パスワードを忘れた」を追加しました。アプリ内で再設定またはアカウントページを開き、ページを戻る・再読み込み・ログインに戻る操作ができます。Pica のウェブ版に再設定項目がない場合は公式クライアントを案内します。
- 詳細画面の ID・作者・分類タグをグループ化し、ダウンロードと読書ボタンを同じ幅で横並びに、章ボタンを2列に配置しました。お気に入り、続きを読む、ダウンロード済み表示を維持し、共有・コメントの操作は表示しません。
- 発見、分類の検索結果、お気に入り、ダウンロード、読書履歴を、左に表紙、右に情報を表示するリストに統一しました。タイトルは最大2行とし、取得済みの作者・分類・配信元を表示します。分類の選択画面はチップ形式を維持し、ダウンロード操作や履歴削除も引き続き利用できます。
- 更新画面の操作ボタンを下部に固定しました。長い更新履歴でも操作でき、ボタン内の進捗表示、一時停止、中断位置からの再開、キャンセルに対応します。
- 読書画面から戻ると、詳細の一時キャッシュと目次の位置を復元し、読書進捗を更新します。詳細と読書で同じ情報を再取得せず、手動更新も利用できます。
- 長い画像でも表示範囲内に留まる控えめな読み込みアニメーションを追加し、エラー表示と再試行ボタンを読書背景に合わせました。再試行は現在の表示範囲と設定した先読み枚数内の失敗画像だけを対象とし、読み込み済み・処理中の画像や以前の失敗ページ、章全体を再取得しません。
- ページ移動で先読みが繰り返し中断される問題を修正しました。表示範囲の先に設定枚数を先読みし、開始済みのダウンロードは継続します。離れたページへ移動した場合は不要な待機タスクだけを取り除き、画像の読み込み中はインジケーターを表示します。
- 先読み枚数と読書設定のシートで、戻る操作を二度必要とする問題を修正しました。一度の操作で現在のシートが閉じます。
- JM が Tencent の画像配信先を選んだ際、詳細・読書画面で「形式が変わった」と誤表示する問題を修正しました。未対応の配信先では画像経路の切り替えを案内します。
- JM の既定ディレクトリで slug が空の場合に、分類ページ全体が読み込めなくなる問題を修正しました。親・子ディレクトリとタググループを取得し、同名の子分類を区別して、タグには検索リクエストを使用します。
- EH、Wnacg、Hitomi、nhentai の不足していた分類と対応する絞り込みを追加しました。公開の固定ディレクトリはセッション失効時も表示できます。Pica は引き続き認証済み API の分類一覧を取得します。
- 独自の「すべての分類」ボタンは追加せず、各サービスのディレクトリを表示します。分類が空の場合は再読み込みを案内します。

## 0.3.1

2026-09-20 · Android 8.0+ · arm64-v8a · versionCode 3001

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

- 下载 `PiComic-0.3.1.apk`，适用于 Android 8.0 及以上的 arm64 设备。
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

- Download `PiComic-0.3.1.apk` for arm64 devices running Android 8.0 or later.
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

- Android 8.0 以降の arm64 端末向けに `PiComic-0.3.1.apk` をダウンロードしてください。
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
