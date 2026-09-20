# 源码证据索引

核对日期：2026-09-17。固定提交为此次拉取的 HEAD；不代表平台实时可用性。以下链接对应已读源码，仅索引实现位置，不复制源码。

## 仓库快照

| 仓库 | 提交 | 提交时间 | 顶层许可入口 |
|---|---|---|---|
| [PicaComic](https://github.com/wgh136/PicaComic) | `155cf0c6d7018f9521f8c375b142bafd85e47a4d` | 2024-12-21T17:48:05+08:00 | [LICENSE](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/LICENSE) |
| [Hazuki](https://github.com/LuckyLxi/Hazuki) | `4264814afc5ff1ae5eb4a0bb772286d3e7c5e79d` | 2026-09-11T18:18:19Z | [LICENSE](https://github.com/LuckyLxi/Hazuki/blob/4264814afc5ff1ae5eb4a0bb772286d3e7c5e79d/LICENSE) |
| [jm-mobile](https://github.com/Dedicatus546/jm-mobile) | `25a957b65220b9f66f18e759ddb45e963a27c950` | 2026-09-17T17:26:54+08:00 | [LICENSE](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/LICENSE) |
| [Breeze-plugin-list](https://github.com/deretame/Breeze-plugin-list) | `9737a4e7fe8bc4e33ee691b67f0624ef6faeb23c` | 2026-09-16T21:54:18Z | 未发现顶层 LICENSE，复用前单独确认 |
| [Breeze](https://github.com/deretame/Breeze) | `2b39df0bf277329d535b29abe83869e8240cb934` | 2026-09-14T17:34:20+08:00 | [LICENSE](https://github.com/deretame/Breeze/blob/2b39df0bf277329d535b29abe83869e8240cb934/LICENSE) |
| [venera-configs](https://github.com/venera-app/venera-configs) | `d8a71168a83a6797482a5be9c000989dabf21c08` | 2026-09-06T16:47:24Z | 未发现顶层 LICENSE，复用前单独确认 |
| [Breeze-plugin-JmComic](https://github.com/deretame/Breeze-plugin-JmComic) | `37c8b86a602553ac6e41008c5afa07b9446e7590` | 2026-08-19T22:55:53+08:00 | 未发现顶层 LICENSE，复用前单独确认 |
| [Breeze-plugin-bikaComic](https://github.com/deretame/Breeze-plugin-bikaComic) | `700aa3393d433d1957e16607e0db99a28e44ceee` | 2026-07-07T23:17:44+08:00 | 未发现顶层 LICENSE，复用前单独确认 |
| [Breeze-plugin-ehentai](https://github.com/deretame/Breeze-plugin-ehentai) | `5e3ee17c6ee414afbcb5802d44e1863d88094fca` | 2026-09-14T08:45:53Z | 未发现顶层 LICENSE，复用前单独确认 |
| [Breeze-plugin-nhentai](https://github.com/deretame/Breeze-plugin-nhentai) | `2b9b7ac1f7e01f6452489d656271c004ae4ec8d1` | 2026-09-02T12:05:35+08:00 | 未发现顶层 LICENSE，复用前单独确认 |
| [Breeze-plugin-shenShiManHua](https://github.com/deretame/Breeze-plugin-shenShiManHua) | `8e3b10b398b10bd1f1d796b8198d7c74161b09d5` | 2026-09-04T20:38:33+08:00 | 未发现顶层 LICENSE，复用前单独确认 |

## 实现入口

| 编号 | 文件/行 | 检查入口 |
|---|---|---|
| PICA-UI | [PicaComic/lib/main.dart:161](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/main.dart#L161) | `_generateColorSchemes` |
| PICA-READ | [PicaComic/lib/pages/reader/reading_data.dart:3](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/pages/reader/reading_data.dart#L3) | `abstract class ReadingData` |
| PICA-VIEW | [PicaComic/lib/pages/reader/image_view.dart:27](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/pages/reader/image_view.dart#L27) | `buildComicView` |
| PICA-PICA | [PicaComic/lib/network/picacg_network/methods.dart:145](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/network/picacg_network/methods.dart#L145) | `Future<Res<String>> login` |
| PICA-SIGN | [PicaComic/lib/network/picacg_network/headers.dart:15](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/network/picacg_network/headers.dart#L15) | `createSignature` |
| PICA-EH | [PicaComic/lib/network/eh_network/eh_main_network.dart:36](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/network/eh_network/eh_main_network.dart#L36) | `getCookies` |
| PICA-JM | [PicaComic/lib/network/jm_network/jm_network.dart:70](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/network/jm_network/jm_network.dart#L70) | `class JmNetwork` |
| PICA-IMAGE | [PicaComic/lib/foundation/image_loader/image_recombine.dart:11](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/foundation/image_loader/image_recombine.dart#L11) | `_getSegmentationNum` |
| PICA-HITOMI | [PicaComic/lib/network/hitomi_network/image.dart:8](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/network/hitomi_network/image.dart#L8) | `gg.js` |
| PICA-HT | [PicaComic/lib/network/htmanga_network/htmanga_main_network.dart:18](https://github.com/wgh136/PicaComic/blob/155cf0c6d7018f9521f8c375b142bafd85e47a4d/lib/network/htmanga_network/htmanga_main_network.dart#L18) | `class HtmangaNetwork` |
| HAZ-CATALOG | [Hazuki/lib/services/source/runtime/source_catalog_resolver.dart:10](https://github.com/LuckyLxi/Hazuki/blob/4264814afc5ff1ae5eb4a0bb772286d3e7c5e79d/lib/services/source/runtime/source_catalog_resolver.dart#L10) | `class SourceCatalogResolver` |
| HAZ-CONFIG | [Hazuki/lib/services/source/runtime/source_config_url_resolver.dart:3](https://github.com/LuckyLxi/Hazuki/blob/4264814afc5ff1ae5eb4a0bb772286d3e7c5e79d/lib/services/source/runtime/source_config_url_resolver.dart#L3) | `_jsDelivrBaseUrl` |
| HAZ-ASSEMBLY | [Hazuki/lib/services/source/runtime/source_runtime_assembly.dart:55](https://github.com/LuckyLxi/Hazuki/blob/4264814afc5ff1ae5eb4a0bb772286d3e7c5e79d/lib/services/source/runtime/source_runtime_assembly.dart#L55) | `jm.js` |
| HAZ-SESSION | [Hazuki/lib/services/source/runtime/source_secure_session_storage.dart:13](https://github.com/LuckyLxi/Hazuki/blob/4264814afc5ff1ae5eb4a0bb772286d3e7c5e79d/lib/services/source/runtime/source_secure_session_storage.dart#L13) | `class SourceSecureSessionStorageKeys` |
| HAZ-RELOGIN | [Hazuki/lib/services/source/account/source_relogin_coordinator.dart:69](https://github.com/LuckyLxi/Hazuki/blob/4264814afc5ff1ae5eb4a0bb772286d3e7c5e79d/lib/services/source/account/source_relogin_coordinator.dart#L69) | `runWithReloginRetry` |
| JM-HTTP | [jm-mobile/app/src/main/java/com/par9uet/jm/retrofit/Retrofit.kt:39](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/retrofit/Retrofit.kt#L39) | `private val cookieJar` |
| JM-TOKEN | [jm-mobile/app/src/main/java/com/par9uet/jm/retrofit/interceptor/TokenInterceptor.kt:10](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/retrofit/interceptor/TokenInterceptor.kt#L10) | `class TokenInterceptor` |
| JM-CONSTANT | [jm-mobile/app/src/main/java/com/par9uet/jm/retrofit/Constant.kt:5](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/retrofit/Constant.kt#L5) | `API_TS` |
| JM-URL | [jm-mobile/app/src/main/java/com/par9uet/jm/retrofit/interceptor/BaseUrlInterceptor.kt:10](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/retrofit/interceptor/BaseUrlInterceptor.kt#L10) | `class BaseUrlInterceptor` |
| JM-LOGIN | [jm-mobile/app/src/main/java/com/par9uet/jm/retrofit/service/UserService.kt:22](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/retrofit/service/UserService.kt#L22) | `suspend fun login` |
| JM-USER | [jm-mobile/app/src/main/java/com/par9uet/jm/store/UserManager.kt:58](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/store/UserManager.kt#L58) | `autoLogin` |
| JM-COOKIE | [jm-mobile/app/src/main/java/com/par9uet/jm/storage/CookieStorage.kt:64](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/storage/CookieStorage.kt#L64) | `class CookieStorage` |
| JM-API | [jm-mobile/app/src/main/java/com/par9uet/jm/retrofit/service/ProxyApiService.kt:7](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/retrofit/service/ProxyApiService.kt#L7) | `getApiList` |
| JM-API-VM | [jm-mobile/app/src/main/java/com/par9uet/jm/ui/viewModel/ApiSelectViewModel.kt:26](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/ui/viewModel/ApiSelectViewModel.kt#L26) | `pullApiList` |
| JM-DECODE | [jm-mobile/app/src/main/java/com/par9uet/jm/retrofit/DataDecode.kt:118](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/retrofit/DataDecode.kt#L118) | `scramble_id` |
| JM-IMAGE | [jm-mobile/app/src/main/java/com/par9uet/jm/utils/DecodeComicPic.kt:16](https://github.com/Dedicatus546/jm-mobile/blob/25a957b65220b9f66f18e759ddb45e963a27c950/app/src/main/java/com/par9uet/jm/utils/DecodeComicPic.kt#L16) | `scrambleId` |
| BREEZE-INDEX | [Breeze-plugin-list/plugins_data.json:3](https://github.com/deretame/Breeze-plugin-list/blob/9737a4e7fe8bc4e33ee691b67f0624ef6faeb23c/plugins_data.json#L3) | `Breeze-plugin-JmComic` |
| BREEZE-WEB | [Breeze/lib/page/plugin_settings/method/plugin_settings_web_login.dart:584](https://github.com/deretame/Breeze/blob/2b39df0bf277329d535b29abe83869e8240cb934/lib/page/plugin_settings/method/plugin_settings_web_login.dart#L584) | `buildCookieHeader` |
| VEN-JM | [venera-configs/jm.js:74](https://github.com/venera-app/venera-configs/blob/d8a71168a83a6797482a5be9c000989dabf21c08/jm.js#L74) | `getApiHeaders` |
| VEN-PICA | [venera-configs/picacg.js:24](https://github.com/venera-app/venera-configs/blob/d8a71168a83a6797482a5be9c000989dabf21c08/picacg.js#L24) | `buildHeaders` |
| VEN-NH | [venera-configs/nhentai.js:18](https://github.com/venera-app/venera-configs/blob/d8a71168a83a6797482a5be9c000989dabf21c08/nhentai.js#L18) | `apiBaseUrl` |
| VEN-HITOMI | [venera-configs/hitomi.js:463](https://github.com/venera-app/venera-configs/blob/d8a71168a83a6797482a5be9c000989dabf21c08/hitomi.js#L463) | `gg.js` |
| BZ-JM-CLIENT | [Breeze-plugin-JmComic/src/client.ts:127](https://github.com/deretame/Breeze-plugin-JmComic/blob/37c8b86a602553ac6e41008c5afa07b9446e7590/src/client.ts#L127) | `jwttoken` |
| BZ-JM-CONFIG | [Breeze-plugin-JmComic/src/constants.ts:14](https://github.com/deretame/Breeze-plugin-JmComic/blob/37c8b86a602553ac6e41008c5afa07b9446e7590/src/constants.ts#L14) | `JM_FALLBACK_API_BASE` |
| BZ-PICA-LOGIN | [Breeze-plugin-bikaComic/src/bika-settings.ts:317](https://github.com/deretame/Breeze-plugin-bikaComic/blob/700aa3393d433d1957e16607e0db99a28e44ceee/src/bika-settings.ts#L317) | `export async function loginWithPassword` |
| BZ-PICA-CLIENT | [Breeze-plugin-bikaComic/src/client.ts:273](https://github.com/deretame/Breeze-plugin-bikaComic/blob/700aa3393d433d1957e16607e0db99a28e44ceee/src/client.ts#L273) | `headers.set("signature"` |
| BZ-EH-SESSION | [Breeze-plugin-ehentai/src/services/settings.service.ts:190](https://github.com/deretame/Breeze-plugin-ehentai/blob/5e3ee17c6ee414afbcb5802d44e1863d88094fca/src/services/settings.service.ts#L190) | `tryResolveExhentaiIgneous` |
| BZ-EH-ROUTE | [Breeze-plugin-ehentai/src/services/site-routing.service.ts:77](https://github.com/deretame/Breeze-plugin-ehentai/blob/5e3ee17c6ee414afbcb5802d44e1863d88094fca/src/services/site-routing.service.ts#L77) | `remapGalleryHostForSite` |
| BZ-NH | [Breeze-plugin-nhentai/src/index.ts:49](https://github.com/deretame/Breeze-plugin-nhentai/blob/2b9b7ac1f7e01f6452489d656271c004ae4ec8d1/src/index.ts#L49) | `const API_BASE` |
| BZ-HT | [Breeze-plugin-shenShiManHua/src/index.ts:73](https://github.com/deretame/Breeze-plugin-shenShiManHua/blob/8e3b10b398b10bd1f1d796b8198d7c74161b09d5/src/index.ts#L73) | `FALLBACK_BASE_URL` |

完整机器可读记录：[source-snapshots.json](source-snapshots.json)。实际主机、登录成功、Key 权限、图片显示和 Android 代理路线均尚未实测。
