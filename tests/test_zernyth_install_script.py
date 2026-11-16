"""Runs packaging/linux/zernyth-install.sh against a local fake release API. Linux only."""

import functools
import hashlib
import http.server
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import threading
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "packaging/linux/zernyth-install.sh"
INSTALL_OK = '#!/bin/sh\nset -eu\nmkdir -p "$(dirname "$0")/.venv"\n'
INSTALL_FAIL = "#!/bin/sh\nexit 1\n"
START = '#!/bin/sh\necho "START $(basename "$(dirname "$0")") $*"\n'


@unittest.skipUnless(sys.platform == "linux", "the installer targets Ubuntu")
class InstallScriptTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.www = self.root / "www"
        (self.www / "releases/tags").mkdir(parents=True)
        (self.www / "dl").mkdir()
        handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=str(self.www))
        handler.log_message = lambda *args: None
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.base = f"http://127.0.0.1:{self.server.server_address[1]}"
        for version, install in (("1.0.0", INSTALL_OK), ("1.1.0", INSTALL_OK), ("1.2.0", INSTALL_FAIL)):
            self.release(version, install)
        self.latest("1.0.0")
        self.env = dict(os.environ, ZERNYTH_HOME=str(self.root / "home"), ZERNYTH_API=self.base)

    def tearDown(self):
        self.server.shutdown()
        self.temp.cleanup()

    def release(self, version, install):
        name = f"zernyth-engine-{version}-ubuntu24-x86_64-cp312"
        buffer = io.BytesIO()
        with tarfile.open(fileobj=buffer, mode="w:gz") as archive:
            for file, text in (("install.sh", install), ("start.sh", START)):
                data = text.encode()
                info = tarfile.TarInfo(f"{name}/{file}")
                info.size, info.mode = len(data), 0o755
                archive.addfile(info, io.BytesIO(data))
        (self.www / "dl" / f"{name}.tar.gz").write_bytes(buffer.getvalue())
        digest = hashlib.sha256(buffer.getvalue()).hexdigest()
        (self.www / "dl" / f"SHA256SUMS-{version}").write_text(f"{digest}  {name}.tar.gz\n")
        (self.www / "releases/tags" / f"v{version}").write_text(json.dumps({
            "tag_name": f"v{version}", "draft": False, "prerelease": False, "assets": [
                {"name": f"{name}.tar.gz", "browser_download_url": f"{self.base}/dl/{name}.tar.gz"},
                {"name": "SHA256SUMS", "browser_download_url": f"{self.base}/dl/SHA256SUMS-{version}"}]}))

    def latest(self, version):
        (self.www / "releases/latest").write_text((self.www / "releases/tags" / f"v{version}").read_text())

    def run_script(self, *args):
        return subprocess.run(["bash", str(SCRIPT), *args], env=self.env, capture_output=True, text=True, timeout=60)

    def status(self):
        return self.run_script("status").stdout

    def test_install_update_rollback_and_failed_update(self):
        self.assertEqual(self.run_script("install").returncode, 0)
        self.assertIn("Active: 1.0.0", self.status())
        self.latest("1.1.0")
        self.assertEqual(self.run_script("update").returncode, 0)
        self.assertIn("Active: 1.1.0  Previous: 1.0.0", self.status())
        self.assertIn("up to date", self.run_script("update").stdout)
        self.assertEqual(self.run_script("rollback").returncode, 0)
        self.assertIn("Active: 1.0.0  Previous: 1.1.0", self.status())
        self.latest("1.2.0")
        failed = self.run_script("update")
        self.assertNotEqual(failed.returncode, 0)
        self.assertIn("active version is unchanged", failed.stderr)
        self.assertIn("Active: 1.0.0", self.status())
        self.assertNotIn("1.2.0", self.run_script("versions").stdout)

    def test_checksum_mismatch_installs_nothing(self):
        sums = self.www / "dl/SHA256SUMS-1.0.0"
        sums.write_text("0" * 64 + sums.read_text()[64:])
        result = self.run_script("install")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("checksum mismatch", result.stderr)
        self.assertEqual(self.run_script("versions").stdout, "")

    def test_start_remembers_server_and_java(self):
        self.run_script("install")
        for folder in ("bds", "jdk"):
            (self.root / folder).mkdir()
        self.assertNotEqual(self.run_script("start").returncode, 0)
        first = self.run_script("start", "--server", str(self.root / "bds"), "--java-home", str(self.root / "jdk"), "--run")
        self.assertIn(f"--server {self.root / 'bds'} --java-home {self.root / 'jdk'} --run", first.stdout)
        again = self.run_script("start")
        self.assertIn(f"--server {self.root / 'bds'}", again.stdout)
        self.assertNotIn("--run", again.stdout)


if __name__ == "__main__":
    unittest.main()
