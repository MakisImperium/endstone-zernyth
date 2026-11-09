"""Compare local Zernyth batch harness outputs without claiming statistical significance.

Each input is an independent JVM process. Percentiles remain percentiles of batch
means, not individual operation tails. This script performs no builds or downloads.
"""
import argparse
import json
import math
from pathlib import Path
import statistics


def configuration(result):
    arguments = [value for value in result["jvmArguments"]
                 if not value.startswith("-Dzernyth.benchmark.output=")]
    return {key: result[key] for key in ("java", "vendor", "os", "warmupRounds", "measurementRounds")} | {
        "jvmArguments": arguments, "operationScale": result.get("operationScale", 1)}


def compare(before_paths, after_paths):
    if len(before_paths) < 3 or len(after_paths) < 3:
        raise ValueError("At least three independent process outputs per side are required")
    before = [json.loads(Path(path).read_text(encoding="utf-8")) for path in before_paths]
    after = [json.loads(Path(path).read_text(encoding="utf-8")) for path in after_paths]
    config = configuration(before[0])
    if any(configuration(row) != config for row in before + after):
        raise ValueError("JDK, OS, JVM arguments or measurement settings differ")
    names = [row["name"] for row in before[0]["metrics"]]
    if len(set(names)) != len(names):
        raise ValueError("Duplicate metric names")
    for result in before + after:
        if [row["name"] for row in result["metrics"]] != names:
            raise ValueError("Metric sets/order differ")
    metrics = []
    for index, name in enumerate(names):
        old, new = [row["metrics"][index] for row in before], [row["metrics"][index] for row in after]
        if len({row["operationsPerSample"] for row in old + new}) != 1:
            raise ValueError(f"Operation counts differ for {name}")
        old_medians, new_medians = [row["medianNs"] for row in old], [row["medianNs"] for row in new]
        if any(not math.isfinite(value) or value <= 0 for value in old_medians + new_medians):
            raise ValueError("Nonfinite or nonpositive measurements cannot be compared")
        old_median, new_median = statistics.median(old_medians), statistics.median(new_medians)
        values = {"name": name, "operationsPerSample": old[0]["operationsPerSample"],
                  "beforeMedianNs": old_median, "afterMedianNs": new_median,
                  "changePercent": (new_median / old_median - 1) * 100,
                  "beforeForkMediansNs": old_medians, "afterForkMediansNs": new_medians}
        for phase, rows in (("before", old), ("after", new)):
            values[phase + "P95BatchMeansNs"] = [row["p95BatchMeanNs"] for row in rows]
            allocations = [row.get("javaAllocatedBytesPerOperation") for row in rows]
            values[phase + "MedianAllocatedBytesPerOperation"] = (
                statistics.median(allocations) if all(value is not None for value in allocations) else None)
        metrics.append(values)
    return {"configuration": config, "before": list(map(str, before_paths)),
            "after": list(map(str, after_paths)), "metrics": metrics,
            "limitations": "Local batch means; no independent-operation P95/P99, confidence interval, BDS or competitor result."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", nargs="+", required=True, type=Path)
    parser.add_argument("--after", nargs="+", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = compare(args.before, args.after)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    for row in result["metrics"]:
        print(f"{row['name']}: {row['beforeMedianNs']:.2f} -> {row['afterMedianNs']:.2f} ns "
              f"({row['changePercent']:+.1f}%)")


if __name__ == "__main__":
    main()
