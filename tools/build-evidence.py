"""Build a permalink index from already downloaded public research checkouts."""
import argparse
import json
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("research_root", type=Path)
args = parser.parse_args()
project = Path(__file__).resolve().parents[1]
owners = {"PicaComic": "wgh136", "Hazuki": "LuckyLxi", "jm-mobile": "Dedicatus546", "Breeze": "deretame", "venera-configs": "venera-app", "Breeze-plugin-list": "deretame"}
rows = [
    ("PICA-UI", "PicaComic", "lib/main.dart", "_generateColorSchemes"),
    ("PICA-READ", "PicaComic", "lib/pages/reader/reading_data.dart", "abstract class ReadingData"),
    ("PICA-VIEW", "PicaComic", "lib/pages/reader/image_view.dart", "buildComicView"),
    ("PICA-PICA", "PicaComic", "lib/network/picacg_network/methods.dart", "Future<Res<String>> login"),
    ("PICA-SIGN", "PicaComic", "lib/network/picacg_network/headers.dart", "createSignature"),
    ("PICA-EH", "PicaComic", "lib/network/eh_network/eh_main_network.dart", "getCookies"),
    ("PICA-JM", "PicaComic", "lib/network/jm_network/jm_network.dart", "class JmNetwork"),
    ("PICA-IMAGE", "PicaComic", "lib/foundation/image_loader/image_recombine.dart", "_getSegmentationNum"),
    ("PICA-HITOMI", "PicaComic", "lib/network/hitomi_network/image.dart", "gg.js"),
    ("PICA-HT", "PicaComic", "lib/network/htmanga_network/htmanga_main_network.dart", "class HtmangaNetwork"),
    ("HAZ-CATALOG", "Hazuki", "lib/services/source/runtime/source_catalog_resolver.dart", "class SourceCatalogResolver"),
    ("HAZ-CONFIG", "Hazuki", "lib/services/source/runtime/source_config_url_resolver.dart", "_jsDelivrBaseUrl"),
    ("HAZ-ASSEMBLY", "Hazuki", "lib/services/source/runtime/source_runtime_assembly.dart", "jm.js"),
    ("HAZ-SESSION", "Hazuki", "lib/services/source/runtime/source_secure_session_storage.dart", "class SourceSecureSessionStorageKeys"),
    ("HAZ-RELOGIN", "Hazuki", "lib/services/source/account/source_relogin_coordinator.dart", "runWithReloginRetry"),
    ("JM-HTTP", "jm-mobile", "app/src/main/java/com/par9uet/jm/retrofit/Retrofit.kt", "private val cookieJar"),
    ("JM-TOKEN", "jm-mobile", "app/src/main/java/com/par9uet/jm/retrofit/interceptor/TokenInterceptor.kt", "class TokenInterceptor"),
    ("JM-CONSTANT", "jm-mobile", "app/src/main/java/com/par9uet/jm/retrofit/Constant.kt", "API_TS"),
    ("JM-URL", "jm-mobile", "app/src/main/java/com/par9uet/jm/retrofit/interceptor/BaseUrlInterceptor.kt", "class BaseUrlInterceptor"),
    ("JM-LOGIN", "jm-mobile", "app/src/main/java/com/par9uet/jm/retrofit/service/UserService.kt", "suspend fun login"),
    ("JM-USER", "jm-mobile", "app/src/main/java/com/par9uet/jm/store/UserManager.kt", "autoLogin"),
    ("JM-COOKIE", "jm-mobile", "app/src/main/java/com/par9uet/jm/storage/CookieStorage.kt", "class CookieStorage"),
    ("JM-API", "jm-mobile", "app/src/main/java/com/par9uet/jm/retrofit/service/ProxyApiService.kt", "getApiList"),
    ("JM-API-VM", "jm-mobile", "app/src/main/java/com/par9uet/jm/ui/viewModel/ApiSelectViewModel.kt", "pullApiList"),
    ("JM-DECODE", "jm-mobile", "app/src/main/java/com/par9uet/jm/retrofit/DataDecode.kt", "scramble_id"),
    ("JM-IMAGE", "jm-mobile", "app/src/main/java/com/par9uet/jm/utils/DecodeComicPic.kt", "scrambleId"),
    ("BREEZE-INDEX", "Breeze-plugin-list", "plugins_data.json", "Breeze-plugin-JmComic"),
    ("BREEZE-WEB", "Breeze", "lib/page/plugin_settings/method/plugin_settings_web_login.dart", "buildCookieHeader"),
    ("VEN-JM", "venera-configs", "jm.js", "getApiHeaders"),
    ("VEN-PICA", "venera-configs", "picacg.js", "buildHeaders"),
    ("VEN-NH", "venera-configs", "nhentai.js", "apiBaseUrl"),
    ("VEN-HITOMI", "venera-configs", "hitomi.js", "gg.js"),
    ("BZ-JM-CLIENT", "Breeze-plugin-JmComic", "src/client.ts", "jwttoken"),
    ("BZ-JM-CONFIG", "Breeze-plugin-JmComic", "src/constants.ts", "JM_FALLBACK_API_BASE"),
    ("BZ-PICA-LOGIN", "Breeze-plugin-bikaComic", "src/bika-settings.ts", "export async function loginWithPassword"),
    ("BZ-PICA-CLIENT", "Breeze-plugin-bikaComic", "src/client.ts", 'headers.set("signature"'),
    ("BZ-EH-SESSION", "Breeze-plugin-ehentai", "src/services/settings.service.ts", "tryResolveExhentaiIgneous"),
    ("BZ-EH-ROUTE", "Breeze-plugin-ehentai", "src/services/site-routing.service.ts", "remapGalleryHostForSite"),
    ("BZ-NH", "Breeze-plugin-nhentai", "src/index.ts", "const API_BASE"),
    ("BZ-HT", "Breeze-plugin-shenShiManHua", "src/index.ts", "FALLBACK_BASE_URL"),
]
repos = {}
evidence = []
for code, repo, file, anchor in rows:
    root = args.research_root / repo
    if repo not in repos:
        def git(*parts):
            return subprocess.check_output(["git", "-C", str(root), *parts], text=True, encoding="utf-8").strip()
        owner = owners.get(repo, "deretame")
        repos[repo] = {"url": f"https://github.com/{owner}/{repo}", "sha": git("rev-parse", "HEAD"), "commitDate": git("log", "-1", "--format=%cI"), "licenseFiles": [p for p in git("ls-files").splitlines() if p.upper().startswith("LICENSE")]}
    lines = (root / file).read_text(encoding="utf-8-sig").splitlines()
    found = next((i + 1 for i, line in enumerate(lines) if anchor in line), None)
    if found is None:
        raise RuntimeError(f"Missing anchor {code}: {anchor}")
    info = repos[repo]
    evidence.append({"id": code, "repo": repo, "file": file, "line": found, "anchor": anchor, "url": f"{info['url']}/blob/{info['sha']}/{file}#L{found}"})
output = project / "docs/evidence"
(output / "source-snapshots.json").write_text(json.dumps({"inspectedOn": "2026-09-17", "verification": "static source inspection only", "repositories": repos, "evidence": evidence}, ensure_ascii=False, indent=2), encoding="utf-8")
md = ["# 源码证据索引", "", "核对日期：2026-09-17。固定提交为此次拉取的 HEAD；不代表平台实时可用性。以下链接对应已读源码，仅索引实现位置，不复制源码。", "", "## 仓库快照", "", "| 仓库 | 提交 | 提交时间 | 顶层许可入口 |", "|---|---|---|---|"]
for name, r in repos.items():
    license_link = "、".join(f"[{p}]({r['url']}/blob/{r['sha']}/{p})" for p in r["licenseFiles"]) or "未发现顶层 LICENSE，复用前单独确认"
    md.append(f"| [{name}]({r['url']}) | `{r['sha']}` | {r['commitDate']} | {license_link} |")
md += ["", "## 实现入口", "", "| 编号 | 文件/行 | 检查入口 |", "|---|---|---|"]
for row in evidence:
    md.append(f"| {row['id']} | [{row['repo']}/{row['file']}:{row['line']}]({row['url']}) | `{row['anchor']}` |")
md += ["", "完整机器可读记录：[source-snapshots.json](source-snapshots.json)。实际主机、登录成功、Key 权限、图片显示和 Android 代理路线均尚未实测。", ""]
(output / "source-index.md").write_text("\n".join(md), encoding="utf-8")
print(f"Indexed {len(repos)} repositories and {len(evidence)} source locations.")
