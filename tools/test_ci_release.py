import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import MagicMock, patch

spec = importlib.util.spec_from_file_location("ci_release", Path(__file__).with_name("ci-release.py"))
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)


class ReleaseTests(unittest.TestCase):
    def test_publish_reads_back_draft_before_making_it_public(self):
        self.check_publish(readback_failure=False)

    def test_failed_readback_never_publishes_the_draft(self):
        self.check_publish(readback_failure=True)

    def check_publish(self, readback_failure):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            notes = directory / "notes.md"
            notes.write_text("- 本次更新\n", encoding="utf-8")
            files = [directory / name for name in ("PiComic-1.2.3.apk", "picomic-update.json", "SHA256SUMS.txt")]
            github = MagicMock()
            github.prefix = "repos/ddmoyu/PiComic"
            draft = {"id": 7, "draft": True, "prerelease": False, "assets": []}
            final = {**draft, "draft": False, "html_url": "https://github.com/ddmoyu/PiComic/releases/tag/v1.2.3"}
            github.api.side_effect = [{"private": False}, None, None, draft, draft, final]
            if readback_failure:
                github.verify_uploaded.side_effect = ValueError("远端资产不一致")
            with patch.object(ci, "validate_artifacts", return_value=files), patch.object(ci, "GitHub", return_value=github), \
                    patch.object(ci.subprocess, "check_output", return_value="a" * 40), patch.object(ci.subprocess, "run") as upload:
                if readback_failure:
                    with self.assertRaises(ValueError):
                        ci.publish(directory, "v1.2.3", "ddmoyu/PiComic", notes)
                else:
                    ci.publish(directory, "v1.2.3", "ddmoyu/PiComic", notes)
                upload.assert_called_once()
            github.verify_uploaded.assert_called_once_with(draft, files)
            published = [call for call in github.api.call_args_list if len(call.args) > 2 and call.args[1] == "PATCH"]
            self.assertEqual(0 if readback_failure else 1, len(published))
            if published:
                self.assertEqual({"draft": False, "make_latest": "true"}, published[0].args[2])

    def test_existing_published_assets_are_never_overwritten(self):
        github = MagicMock()
        github.prefix = "repos/ddmoyu/PiComic"
        release = {"draft": False, "html_url": "https://github.com/ddmoyu/PiComic/releases/tag/v1.2.3"}
        github.api.side_effect = [{"private": False}, release]
        github.verify_uploaded.side_effect = ValueError("远端资产不一致")
        with patch.object(ci, "validate_artifacts", return_value=[]), patch.object(ci, "GitHub", return_value=github), \
                patch.object(ci.subprocess, "check_output", return_value="a" * 40), patch.object(ci.subprocess, "run") as upload:
            with self.assertRaises(ValueError):
                ci.publish(Path("fixture"), "v1.2.3", "ddmoyu/PiComic", Path("unused.md"))
            upload.assert_not_called()
        self.assertEqual(2, github.api.call_count)

    def test_tag_pattern_and_monotonic_android_versions(self):
        self.assertEqual(("0.3.1", 3001), ci.version_from_tag("v0.3.1"))
        self.assertLess(ci.version_from_tag("v1.9.999")[1], ci.version_from_tag("v1.10.0")[1])
        self.assertLess(ci.version_from_tag("v0.999.999")[1], ci.version_from_tag("v1.0.0")[1])
        for tag in ["main", "1.2.3", "v1.2", "v1.2.3-alpha", "v1.2.3+1", "v01.2.3", "v0.0.0", "v1.1000.0", "v2100.0.0", "v1.2.3\n"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                ci.version_from_tag(tag)

    def test_release_notes_select_only_one_complete_section(self):
        text = "# 更新记录\n\n## 未发布\n\n- 新功能\n- 新修复\n\n## 1.2.3\n\n- 本次发布\n\n## 1.2.2\n\n- 旧功能\n"
        self.assertEqual("- 本次发布\n", ci.release_notes(text, "1.2.3"))
        self.assertEqual("- 新功能\n- 新修复\n", ci.release_notes(text, "1.2.4"))
        with self.assertRaises(ValueError):
            ci.release_notes("# 没有说明", "1.2.3")

    def test_manifest_tag_abi_and_bytes_cannot_diverge(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            apk = directory / "PiComic-1.2.3.apk"
            apk.write_bytes(b"validated-fixture-apk")
            manifest = {"schemaVersion": 1, "tag": "v1.2.3", "versionName": "1.2.3", "versionCode": 1002003,
                        "packageName": "io.github.ddmoyu.picomic", "channel": "stable", "artifacts": [{
                            "assetName": apk.name, "abis": ["arm64-v8a"], "minSdk": 26,
                            "sizeBytes": apk.stat().st_size, "sha256": ci.sha256(apk)}]}
            def save(development=False):
                (directory / "picomic-update.json").write_text(json.dumps(manifest), encoding="utf-8")
                (directory / "verification.json").write_text(json.dumps({"development": development,
                    "manifest": manifest, "signerSha256": "a" * 64}), encoding="utf-8")
            save()
            self.assertEqual(3, len(ci.validate_artifacts(directory, "v1.2.3")))
            with self.assertRaises(ValueError):
                ci.validate_artifacts(directory, "v1.2.4")
            save(True)
            with self.assertRaises(ValueError):
                ci.validate_artifacts(directory, "v1.2.3")
            save()
            apk.write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                ci.validate_artifacts(directory, "v1.2.3")


if __name__ == "__main__":
    unittest.main()
