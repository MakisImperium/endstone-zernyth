"""Synthetic checks for CI file commands, including Windows UTF-8 behavior."""

import hashlib
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("zernyth_ci", ROOT / "scripts/zernyth_ci.py")
CI = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CI)


class CiOutputTests(unittest.TestCase):
    def test_paths_are_utf8_with_spaces(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            tools = root / "installed tools"
            tools.mkdir()
            output = root / "github-path"
            CI.add_tool_paths(str(tools), output)
            self.assertEqual(output.read_bytes(), (str(tools) + "\n").encode("utf-8"))

    def test_missing_directory_keeps_output_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "github-path"
            output.write_bytes(b"existing\n")
            with self.assertRaises(ValueError):
                CI.add_tool_paths(str(root) + os.pathsep + str(root / "missing"), output)
            self.assertEqual(output.read_bytes(), b"existing\n")

    def test_digest_matches_exact_file_bytes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            wheel = root / "test.whl"
            wheel.write_bytes(b"synthetic wheel bytes")
            output = root / "github-output"
            CI.write_wheel_digest(wheel, output)
            self.assertEqual(output.read_bytes(),
                             ("sha256=" + hashlib.sha256(wheel.read_bytes()).hexdigest() + "\n").encode())


if __name__ == "__main__":
    unittest.main()
