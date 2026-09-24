# 更新记录

## 0.3.8

2026-09-24 · Android 8.0+ · arm64-v8a / armeabi-v7a / x86_64 · versionCode 3008

### 简体中文

- 哔咔新增一键注册，全程无需填写：自动生成账号、密码、昵称和三组密保资料，生日取注册当天往前 20 年。保留手动账号登录。
- 注册资料面板支持显示/隐藏密码和一键复制完整资料，也可在登录页重新查看；资料加密保存在本机，注册后自动登录，后续复用或恢复已保存会话。
- 注册请求中断后保留原资料，继续时先确认原账号，避免重复创建；注册成功但登录被限流时仍保留账号密码。
- 哔咔、JM、绅士漫画登录页支持密码显隐、账号与密码一键清空，默认记住成功登录的账号密码并自动填充；回填不会覆盖正在编辑的内容。
- 修复哔咔将部分服务端限流误报为账号密码错误的问题，改为显示请求过于频繁。

### English

- Added one-tap Picacg registration with no form entry. Generate the account, password, nickname and three recovery question/answer pairs automatically, with a birthday 20 years before the registration date. Manual sign-in remains available.
- View registration details, show or hide the password, and copy the complete record with one tap. Store details encrypted on the device, sign in after registration and restore the saved session on later use.
- Keep the original details when registration is interrupted and check the same account before continuing. Retain credentials when registration succeeds but sign-in is rate limited.
- Added password visibility and clear controls to Picacg, JM and Wnacg sign-in forms. Successful credentials are remembered and filled automatically by default without overwriting active edits.
- Correctly report Picacg's additional rate-limit response instead of treating it as an incorrect password.

### 日本語

- 哔咔に入力不要のワンタップ登録を追加しました。アカウント、パスワード、ニックネーム、3組の秘密の質問と回答を自動生成し、生年月日は登録日の20年前に設定します。手動ログインも引き続き利用できます。
- 登録情報の確認、パスワードの表示切替、全情報の一括コピーに対応しました。情報は端末内に暗号化して保存し、登録後は自動ログイン、次回以降は保存済みセッションを復元します。
- 登録が中断しても同じ情報を保持し、再開時に元のアカウントを確認します。登録成功後のログインが回数制限にかかっても認証情報は失われません。
- 哔咔・JM・Wnacg のログイン画面にパスワード表示切替と入力消去を追加しました。ログイン成功時の情報を既定で記憶し、編集中の入力を上書きせずに自動入力します。
- 哔咔の一部の回数制限をパスワード誤りとして表示する問題を修正しました。

## 0.3.7

2026-09-22 · Android 8.0+ · arm64-v8a / armeabi-v7a / x86_64 · versionCode 3007

### 简体中文

- GitHub Actions 同时构建并发布 ARM64、ARMv7（32 位 ARM）和 x86_64 三种独立安装包，文件名明确标注架构。
- 应用内更新根据设备支持的架构自动选择对应安装包，ARM64 设备优先使用 ARM64；三个包使用相同版本号和发行签名，兼容现有更新协议。
- 发布时逐包检查实际架构、版本、签名和文件摘要，三种架构齐全且上传读回校验通过后才公开版本。

### English

- GitHub Actions now builds and publishes standalone ARM64, ARMv7 (32-bit ARM) and x86_64 APKs, with the architecture included in each filename.
- In-app updates select the compatible APK in device ABI preference order. All three packages share the same version and release signature, using the existing update protocol.
- Every package is checked for ABI, version, signature and checksum. Releases become public only after all three APKs are uploaded and verified by download.

### 日本語

- GitHub Actions で ARM64、ARMv7（32ビット ARM）、x86_64 の独立した APK をビルド・公開し、ファイル名にアーキテクチャを明記します。
- アプリ内更新では端末の対応 ABI の優先順で APK を選択します。3種類とも同じバージョンと署名を使用し、既存の更新プロトコルに対応します。
- 各 APK の ABI・バージョン・署名・ハッシュを検証し、3種類すべてのアップロードと再ダウンロード検証が成功してから公開します。

## 0.3.6

2026-09-22 · Android 8.0+ · arm64-v8a · versionCode 3006

### 简体中文

- 调整来源顺序为 Hitomi、禁漫天堂、绅士漫画、nhentai、E-Hentai / ExHentai、picacg，可匿名浏览的来源优先，禁漫天堂固定第二个。
- 探索、分类、搜索和账号管理使用相同顺序，左右滑动与 Tab 点击保持一致；首次使用默认 Hitomi，已有用户保留上次选择的来源。

### English

- Reordered sources to Hitomi, JM, Wnacg, nhentai, E-Hentai / ExHentai and picacg, putting guest browsing first and JM second.
- Discovery, categories, search and account management share the same order. Swipes and tabs stay in sync; new installations start with Hitomi while existing selections are preserved.

### 日本語

- 配信元を Hitomi、JM、Wnacg、nhentai、E-Hentai / ExHentai、picacg の順に変更しました。匿名で閲覧できる配信元を優先し、JM は2番目に配置します。
- 探索・分類・検索・アカウント管理で順序を統一し、スワイプとタブの選択を同期します。初回は Hitomi を選択し、既存ユーザーの前回の選択は保持します。

## 0.3.5

2026-09-22 · Android 8.0+ · arm64-v8a · versionCode 3005

### 简体中文

- 修复已保存账号密码却反复提示登录的问题。哔咔、禁漫天堂和绅士漫画优先复用已保存的会话；会话明确失效后自动登录一次，保存新会话并重试原读取请求。
- 启动、阅读和下载共享会话恢复，多个请求同时过期时只登录一次。临时网络故障不清除会话，密码错误或需要人工验证时停止自动尝试。
- 修复切换可信 JM / 绅士漫画线路后无法恢复账号的问题；原 Cookie 不会发送到新域名，使用已保存密码在当前线路重新验证。

### English

- Fixed repeated login prompts despite saved account credentials. Picacg, JM and Wnacg now reuse stored sessions first, sign in once when a session has expired, save the verified replacement and retry the original read.
- Startup, reading and downloads share session recovery, preventing duplicate sign-ins when concurrent requests expire. Temporary network failures preserve sessions; incorrect credentials or challenges stop automatic attempts.
- Restore saved accounts after switching trusted JM / Wnacg routes without forwarding old cookies to another origin.

### 日本語

- アカウント情報を保存しているのにログインを繰り返し求められる問題を修正しました。Picacg、JM、Wnacg は保存済みセッションを優先し、期限切れが確認された場合は1回だけ再ログインして、新しいセッションを保存し、元の読み取りを再試行します。
- 起動・読書・ダウンロードでセッション復元を共有し、同時に期限切れになっても重複ログインしません。一時的な通信障害ではセッションを保持し、パスワード誤りや追加認証が必要な場合は自動試行を停止します。
- 信頼済みの JM / Wnacg 接続先を切り替えた後も保存済みアカウントを復元します。以前の Cookie は別の接続先へ送信しません。

## 0.3.4

2026-09-22 · Android 8.0+ · arm64-v8a · versionCode 3004

### 简体中文

- 优化探索、书架和下载列表的封面、文字层级与间距；探索列表去掉与来源 Tab 重复的平台名称，保留作品 ID 和语言信息。
- 全应用统一采用 Phosphor 图标，收藏选中时显示实心心形；统一浅色与深色配色，提高文字与图标的清晰度。
- 分类保持紧凑的浅底圆角标签；整理设置页分组、摘要和间距，保留原有入口与顺序。
- 详情页刷新移到“作品详情”顶栏右侧，滚动目录后仍可使用；“分类 / 标签”简化为“标签”，章节继续以一章一行的虚拟列表显示。
- 调整搜索结果封面圆角和文字层级，提高阅读工具栏对比度；保留现有阅读与操作方式，不增加探索续读或推荐入口。
- 各平台“忘记密码”改为使用系统默认浏览器打开，移除内置找回密码网页；保留当前登录页，未安装或无法打开浏览器时显示提示。

### English

- Refined covers, typography and spacing in discovery, library and download lists. Discovery rows no longer repeat the source already selected in the tab, while retaining the title ID and language.
- Unified app icons with Phosphor, including a filled heart for saved favorites. Refined light and dark colors for clearer text and icons.
- Kept category chips compact with subtle rounded backgrounds. Improved settings groups, summaries and spacing while retaining existing entries and their order.
- Moved Refresh to the upper-right corner of the details toolbar so it stays available when scrolling chapters. Shortened “Categories / Tags” to “Tags” and retained the virtualized, one-chapter-per-row list.
- Refined search-result covers and typography and improved reader toolbar contrast. Existing reading controls remain unchanged, without adding resume or recommendation entries to discovery.
- “Forgot password” now opens in the system's default browser instead of an embedded WebView. Keep the login screen in place and show a message if no browser is available or launching it fails.

### 日本語

- 探索・本棚・ダウンロード一覧の表紙、文字の強弱、余白を調整しました。探索ではタブと重複する配信元名を省き、作品 ID と言語は引き続き表示します。
- アプリ内のアイコンを Phosphor に統一し、お気に入り登録済みは塗りつぶしのハートで表示します。ライト・ダーク両テーマの文字とアイコンを見やすくしました。
- 分類は淡い背景のコンパクトな角丸タグを維持し、設定画面のグループ、説明文、余白を整理しました。既存の項目と順序は維持します。
- 詳細の更新ボタンを「作品詳細」バーの右上へ移し、章一覧をスクロールしても利用できるようにしました。「分類 / タグ」は「タグ」に短縮し、1章1行の仮想リストを維持します。
- 検索結果の表紙の角丸と文字表示を調整し、読書ツールバーのコントラストを改善しました。操作方法は維持し、探索への続きから読む項目やおすすめ項目は追加していません。
- 各サービスの「パスワードを忘れた」を、内蔵 WebView ではなくシステムの既定ブラウザーで開くように変更しました。ログイン画面を維持し、ブラウザーが利用できない場合は案内を表示します。

## 0.3.3

2026-09-20 · Android 8.0+ · arm64-v8a · versionCode 3003

### 简体中文

- 详情页章节目录改为一章一行的简洁文字列表，去掉两列按钮样式；保留续读与已下载标记，并按可见范围虚拟滚动，支持数千章节。
- 设置 → 阅读新增默认关闭的“跳过详情页”：开启后点击作品直接阅读，新作品从首章第一页开始，有历史记录时恢复章节、页码和滚动位置；退出回到原列表。该偏好随配置备份保存，历史章节已失效时提示并从首章开始。
- 修复 JM 部分多章节作品始终提示“格式改变”的问题：来源可能为不同章节提供重复排序号，现在保留全部真实章节，稳定排列并生成独立位置，确保阅读与离线章节顺序一致。
- 分类页按分组展示短标签：JM 保留来源原始目录与标签分组，不再逐个重复组名前缀；Hitomi、nhentai 区分内容类型和语言，绅士漫画按目录类型分组。统一使用柔和底色，保留完整查询条件与同名分类的区分。
- 精简作品详情页：缩小标题，阅读主按钮与收藏、下载图标合并为一行；信息区改用统一色调的紧凑文字排版，去掉彩色按钮底色，分类标签仍可点击搜索。

### English

- Replaced the two-column chapter buttons on the details screen with plain, single-row entries. Resume and downloaded indicators remain, and lazy rendering supports thousands of chapters.
- Added an optional “Skip details” switch in Settings → Reading, off by default. Open new titles at the first chapter and page, or restore the saved chapter, page and scroll position. Leaving the reader returns to the original list. The preference is included in backups; missing historical chapters fall back to the first chapter with a notice.
- Fixed persistent “format changed” errors for some multi-chapter JM albums. Different chapters may share a source sort value; retain every distinct chapter and assign stable local positions so online and offline reading keep the same order.
- Group category chips under headings with shorter labels. JM retains its native directory and tag groups, Hitomi and nhentai separate content types from languages, and Wnacg groups directory types. Use subtle chip backgrounds while preserving complete query values and distinct routes for duplicate names.
- Simplified comic details with a smaller title and one action row for reading, favorites and downloads. Metadata now uses compact text without colorful button backgrounds, while category tags remain searchable by tapping.

### 日本語

- 詳細画面の章一覧を、2列のボタンから1章1行のシンプルなリストに変更しました。続きから読む・ダウンロード済みの表示を維持し、遅延描画で数千章に対応します。
- 設定 → 読書に、初期状態でオフの「詳細をスキップ」を追加しました。新しい作品は最初の章・ページから、履歴のある作品は保存した章・ページ・スクロール位置から開きます。終了すると元の一覧へ戻ります。設定はバックアップ対象で、保存した章がなくなった場合は案内して最初の章から開きます。
- 一部の複数章作品で JM が常に「形式が変わった」と表示する問題を修正しました。配信元の並び順番号が重複していても各章を保持し、安定した順序と個別の位置を割り当て、オンライン・オフラインの読書順序を揃えます。
- 分類を見出しごとにまとめ、短いラベルで表示します。JM の元のディレクトリ・タググループを維持し、Hitomi と nhentai は作品種別と言語、Wnacg はディレクトリ種別ごとに整理しました。背景色を控えめにし、検索条件や同名分類の区別は維持します。
- 作品詳細のタイトルを小さくし、読書ボタン・お気に入り・ダウンロードを1行にまとめました。情報欄はカラフルなボタン背景をなくして簡潔なテキスト表示に変更し、分類タグからの検索は引き続き利用できます。

## 0.3.2

2026-09-20 · Android 8.0+ · arm64-v8a · versionCode 3002

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
