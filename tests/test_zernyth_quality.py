"""Bounded offline tests for the Zernyth-only formatting guard."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace

SOURCE = Path(__file__).resolve().parents[1] / "scripts/zernyth_quality.py"
SPEC = importlib.util.spec_from_file_location("zernyth_quality", SOURCE)
QUALITY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(QUALITY)


class QualityScopeTest(unittest.TestCase):
    def test_workspace_file_is_accepted(self):
        self.assertEqual(QUALITY.checked_path("scripts/zernyth_quality.py"), SOURCE)

    def test_parent_traversal_and_missing_file_are_rejected(self):
        for path in ("../outside.java", "does-not-exist.java", "scripts/../scripts/zernyth_quality.py"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                QUALITY.checked_path(path)

    def test_baseline_file_is_rejected_before_formatter(self):
        import json
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "scope.json"
            manifest.write_text(json.dumps({"baseline": "HEAD", "files": ["LICENSE"]}))
            with patch.object(QUALITY, "MANIFEST", manifest), patch.object(QUALITY.subprocess, "run") as run:
                run.return_value.returncode = 0
                with self.assertRaises(ValueError):
                    QUALITY.format_sources("unused-formatter", False)
                self.assertEqual(run.call_count, 2)

    def test_missing_baseline_is_rejected_before_formatter(self):
        with patch.object(QUALITY.subprocess, "run") as run:
            run.return_value.returncode = 128
            with self.assertRaises(ValueError):
                QUALITY.format_sources("unused-formatter", False)
            self.assertEqual(run.call_count, 1)

    def test_symlink_is_rejected_without_following_it(self):
        # Mock the platform-independent predicate; Windows may disallow creation of symlinks.
        with patch.object(Path, "is_symlink", return_value=True):
            with self.assertRaises(ValueError):
                QUALITY.checked_path("scripts/zernyth_quality.py")

    def test_java_write_is_rejected_before_invoking_any_formatter(self):
        import json
        with tempfile.TemporaryDirectory() as directory:
            manifest = Path(directory) / "scope.json"
            manifest.write_text(json.dumps({"baseline": "HEAD", "files": [
                "java/api/src/main/java/dev/zernyth/api/Server.java"]}))
            with patch.object(QUALITY, "MANIFEST", manifest), patch.object(QUALITY.subprocess, "run") as run:
                run.side_effect = [SimpleNamespace(returncode=0), SimpleNamespace(returncode=1)]
                with self.assertRaisesRegex(ValueError, "IntelliJ"):
                    QUALITY.format_sources("unused-formatter", True)
                self.assertEqual(run.call_count, 2)

    def test_missing_jdk_does_not_trigger_installation(self):
        with patch.dict(QUALITY.os.environ, {}, clear=True), patch.object(QUALITY.subprocess, "run") as run:
            with self.assertRaises(ValueError):
                QUALITY.check_java_sources([], None)
            run.assert_not_called()


if __name__ == "__main__":
    unittest.main()
