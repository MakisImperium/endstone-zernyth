"""Synthetic data only: comparison guards, not server performance tests."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "scripts/zernyth_compare_benchmarks.py"
SPEC = importlib.util.spec_from_file_location("compare_benchmarks", SOURCE)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class BenchmarkComparisonTest(unittest.TestCase):
    def fixture(self, directory, prefix, value, version="25"):
        paths = []
        for index in range(3):
            path = Path(directory) / f"{prefix}-{index}.json"
            path.write_text(json.dumps({"java": version, "vendor": "test", "os": "test",
                "warmupRounds": 3, "measurementRounds": 15, "jvmArguments": ["-Xmx256m"],
                "metrics": [{"name": "probe", "operationsPerSample": 100, "medianNs": value,
                             "p95BatchMeanNs": value * 2}]}))
            paths.append(path)
        return paths

    def test_median_and_missing_allocation(self):
        with tempfile.TemporaryDirectory() as directory:
            old = self.fixture(directory, "old", 10)
            new = self.fixture(directory, "new", 5)
            row = MODULE.compare(old, new)["metrics"][0]
            self.assertEqual(row["changePercent"], -50)
            self.assertIsNone(row["afterMedianAllocatedBytesPerOperation"])

    def test_configuration_mismatch(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                MODULE.compare(self.fixture(directory, "old", 10), self.fixture(directory, "new", 5, "26"))

    def test_insufficient_process_count(self):
        with self.assertRaises(ValueError):
            MODULE.compare([], [])

    def test_nonfinite_measurements_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ValueError):
                MODULE.compare(self.fixture(directory, "old", 10), self.fixture(directory, "new", float("nan")))


if __name__ == "__main__":
    unittest.main()
