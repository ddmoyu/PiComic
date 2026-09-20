"""Validate vX.Y.Z tags and publish verified APK assets through a draft Release."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def version_from_tag(tag: str) -> tuple[str, int]:
    match = re.fullmatch(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", tag)
    if not match:
        raise ValueError("发布标签必须为 vX.Y.Z，且不含前导零或预发布后缀")
    major, minor, patch = map(int, match.groups())
    if major > 2099 or minor > 999 or patch > 999:
        raise ValueError("版本范围要求 major <= 2099，minor/patch <= 999")
    code = major * 1_000_000 + minor * 1_000 + patch
    if not 1 <= code <= 2_099_999_999:
        raise ValueError("无效 Android versionCode")
    return tag[1:], code


def release_notes(changelog: str, version: str) -> str:
    sections = {}
    title, rows = None, []
    for line in changelog.splitlines():
        if line.startswith("## "):
            if title is not None:
                sections[title] = "\n".join(rows).strip()
            title, rows = line[3:].strip(), []
        elif title is not None:
            rows.append(line)
    if title is not None:
        sections[title] = "\n".join(rows).strip()
    notes = sections.get(version) or sections.get("未发布") or ""
    if not notes or not any(line.startswith("- ") for line in notes.splitlines()):
        raise ValueError("CHANGELOG.md 缺少该版本或未发布部分的更新说明")
    return notes + "\n"


def sha256(file: Path) -> str:
    with file.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def validate_artifacts(directory: Path, tag: str) -> list[Path]:
    version, code = version_from_tag(tag)
    manifest_file = directory / "picomic-update.json"
    manifest = json.loads(manifest_file.read_text(encoding="utf-8-sig"))
    if set(manifest) != {"schemaVersion", "tag", "versionName", "versionCode", "packageName", "channel", "artifacts"}:
        raise ValueError("更新清单字段不符合客户端合同")
    if (manifest["schemaVersion"], manifest["tag"], manifest["versionName"], manifest["versionCode"],
            manifest["packageName"], manifest["channel"]) != (1, tag, version, code, "io.github.ddmoyu.picomic", "stable"):
        raise ValueError("标签、APK 版本和更新清单不一致")
    report = json.loads((directory / "verification.json").read_text(encoding="utf-8-sig"))
    if report.get("development") is not False or report.get("manifest") != manifest:
        raise ValueError("缺少最终 APK 的正式构建校验报告")
    if not re.fullmatch(r"[0-9a-f]{64}", report.get("signerSha256", "")):
        raise ValueError("缺少预期签名证书摘要")
    if len(manifest["artifacts"]) != 1:
        raise ValueError("本发布流程只接受一个 arm64 APK")
    artifact = manifest["artifacts"][0]
    if artifact["assetName"] != f"PiComic-{version}.apk" or artifact["abis"] != ["arm64-v8a"]:
        raise ValueError("APK 文件名或 ABI 与发布目标不符")
    apk = directory / artifact["assetName"]
    if apk.stat().st_size != artifact["sizeBytes"] or sha256(apk) != artifact["sha256"]:
        raise ValueError("待上传 APK 的大小或摘要已变化")
    if manifest_file.stat().st_size > 65536:
        raise ValueError("更新清单超过客户端大小限制")
    sums = directory / "SHA256SUMS.txt"
    sums.write_text("".join(f"{sha256(file)}  {file.name}\n" for file in (apk, manifest_file)), encoding="utf-8")
    return [apk, manifest_file, sums]


class GitHub:
    def __init__(self, repository: str):
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_][A-Za-z0-9_.-]{0,99}", repository):
            raise ValueError("仓库名格式无效")
        self.repository = repository
        self.prefix = f"repos/{repository}"

    def api(self, endpoint: str, method: str = "GET", payload=None, missing=False):
        command = ["gh", "api", endpoint, "--method", method, "-H", "Accept: application/vnd.github+json",
                   "-H", "X-GitHub-Api-Version: 2026-03-10"]
        with tempfile.TemporaryDirectory(prefix="picomic-api-") as tmp:
            if payload is not None:
                body = Path(tmp) / "body.json"
                body.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
                command += ["--input", str(body)]
            result = subprocess.run(command, capture_output=True, text=True, encoding="utf-8")
        if result.returncode:
            if missing and "HTTP 404" in result.stderr:
                return None
            raise RuntimeError(result.stderr.strip())
        return json.loads(result.stdout) if result.stdout.strip() else None

    def download(self, asset: dict, destination: Path):
        with destination.open("wb") as output:
            result = subprocess.run(["gh", "api", f"{self.prefix}/releases/assets/{int(asset['id'])}",
                                     "-H", "Accept: application/octet-stream"], stdout=output, stderr=subprocess.PIPE)
        if result.returncode:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))

    def verify_uploaded(self, release: dict, files: list[Path]):
        assets = release["assets"]
        if {item["name"] for item in assets} != {file.name for file in files} or len(assets) != len(files):
            raise ValueError("Release 资产集合不完整或存在重复文件")
        with tempfile.TemporaryDirectory(prefix="picomic-readback-") as tmp:
            for file in files:
                remote = next(item for item in assets if item["name"] == file.name)
                if remote["state"] != "uploaded" or remote["size"] != file.stat().st_size:
                    raise ValueError(f"远端文件大小异常：{file.name}")
                copied = Path(tmp) / file.name
                self.download(remote, copied)
                if copied.stat().st_size != file.stat().st_size or sha256(copied) != sha256(file):
                    raise ValueError(f"远端文件读回校验失败：{file.name}")


def publish(directory: Path, tag: str, repository: str, notes_file: Path):
    version, code = version_from_tag(tag)
    files = validate_artifacts(directory, tag)
    github = GitHub(repository)
    if github.api(github.prefix).get("private") is not False:
        raise ValueError("App 匿名更新要求公开的 Release 仓库")
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    tagged = subprocess.check_output(["git", "rev-parse", f"refs/tags/{tag}^{{commit}}"], text=True).strip()
    if commit != tagged:
        raise ValueError("构建源码与标签指向的提交不一致")
    release = github.api(f"{github.prefix}/releases/tags/{tag}", missing=True)
    if release and not release["draft"]:
        github.verify_uploaded(release, files)
        print(f"已发布且内容一致：{release['html_url']}")
        return
    latest = github.api(f"{github.prefix}/releases/latest", missing=True)
    if latest:
        manifests = [item for item in latest["assets"] if item["name"] == "picomic-update.json"]
        if len(manifests) != 1 or manifests[0]["size"] > 65536:
            raise ValueError("上一稳定 Release 的更新清单缺失或无效，停止自动发布")
        with tempfile.TemporaryDirectory(prefix="picomic-previous-") as tmp:
            previous = Path(tmp) / "picomic-update.json"
            github.download(manifests[0], previous)
            previous_manifest = json.loads(previous.read_text(encoding="utf-8-sig"))
            if code <= previous_manifest["versionCode"]:
                raise ValueError("新版本的 versionCode 必须大于已发布稳定版本")
    notes = notes_file.read_text(encoding="utf-8")
    if release:
        if release.get("target_commitish") != commit or release["prerelease"]:
            raise ValueError("已有草稿的源码提交或发布类型不同，停止覆盖")
        expected = {file.name for file in files}
        if any(item["name"] not in expected for item in release["assets"]):
            raise ValueError("已有草稿包含非本流程资产，停止覆盖")
        # Only retryable drafts may be replaced; published assets are immutable here.
        for asset in release["assets"]:
            github.api(f"{github.prefix}/releases/assets/{asset['id']}", "DELETE")
        github.api(f"{github.prefix}/releases/{release['id']}", "PATCH", {"body": notes, "name": f"PiComic {version}"})
    else:
        release = github.api(f"{github.prefix}/releases", "POST", {
            "tag_name": tag, "target_commitish": commit, "name": f"PiComic {version}",
            "body": notes, "draft": True, "prerelease": False, "make_latest": "false"})
    subprocess.run(["gh", "release", "upload", tag, *map(str, files), "--repo", repository], check=True)
    uploaded = github.api(f"{github.prefix}/releases/{release['id']}")
    if not uploaded["draft"]:
        raise ValueError("上传期间草稿状态被外部更改")
    github.verify_uploaded(uploaded, files)
    result = github.api(f"{github.prefix}/releases/{release['id']}", "PATCH", {"draft": False, "make_latest": "true"})
    if result["draft"] or result["prerelease"]:
        raise ValueError("Release 未成功转为稳定发布")
    print(f"已发布：{result['html_url']}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["prepare", "validate", "publish"])
    parser.add_argument("--tag", required=True)
    parser.add_argument("--directory", type=Path, default=Path("artifacts/release"))
    parser.add_argument("--notes", type=Path, default=Path("artifacts/release-notes.md"))
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", "ddmoyu/PiComic"))
    args = parser.parse_args()
    version, code = version_from_tag(args.tag)
    if args.command == "prepare":
        args.notes.parent.mkdir(parents=True, exist_ok=True)
        args.notes.write_text(release_notes(Path("CHANGELOG.md").read_text(encoding="utf-8"), version), encoding="utf-8")
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
                output.write(f"version={version}\nversion_code={code}\n")
        print(f"已验证发布标签 {args.tag}，versionCode={code}")
    elif args.command == "validate":
        validate_artifacts(args.directory, args.tag)
        print("本地发布资产校验通过")
    else:
        publish(args.directory, args.tag, args.repository, args.notes)


if __name__ == "__main__":
    main()
