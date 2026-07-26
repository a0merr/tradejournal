#!/usr/bin/env python3
"""Turn Maven's surefire/failsafe XML into a markdown table for the CI run summary.

Run from `backend/`. Prints to stdout; the workflow appends it to $GITHUB_STEP_SUMMARY.
Surefire holds the unit tests, failsafe the Testcontainers integration tests, and they are
reported separately because they are different claims: one says the logic is right, the other
says it is right against a real PostgreSQL.
"""

from __future__ import annotations

import glob
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field


@dataclass
class Tally:
    tests: int = 0
    failures: int = 0
    errors: int = 0
    skipped: int = 0
    time: float = 0.0
    classes: dict[str, int] = field(default_factory=dict)

    def add(self, suite: ET.Element) -> None:
        name = suite.get("name", "?").rsplit(".", 1)[-1]
        count = int(suite.get("tests", 0))
        self.tests += count
        self.failures += int(suite.get("failures", 0))
        self.errors += int(suite.get("errors", 0))
        self.skipped += int(suite.get("skipped", 0))
        self.time += float(suite.get("time", 0) or 0)
        self.classes[name] = self.classes.get(name, 0) + count


def collect(directory: str) -> Tally:
    tally = Tally()
    for path in sorted(glob.glob(f"target/{directory}/TEST-*.xml")):
        try:
            tally.add(ET.parse(path).getroot())
        except ET.ParseError:
            print(f"<!-- unparseable report: {path} -->")
    return tally


def main() -> int:
    unit = collect("surefire-reports")
    integration = collect("failsafe-reports")
    total = unit.tests + integration.tests

    if total == 0:
        print("## Tests\n\nNo test reports found.")
        return 0

    print("## Tests\n")
    print(f"**{total} tests** — {unit.tests} unit, {integration.tests} integration "
          f"(Testcontainers / real PostgreSQL 16)\n")
    print("| Suite | Tests | Failures | Errors | Skipped | Time |")
    print("|---|---:|---:|---:|---:|---:|")
    for label, tally in (("unit", unit), ("integration", integration)):
        print(f"| {label} | {tally.tests} | {tally.failures} | {tally.errors} "
              f"| {tally.skipped} | {tally.time:.1f}s |")
    print(f"| **total** | **{total}** | **{unit.failures + integration.failures}** "
          f"| **{unit.errors + integration.errors}** "
          f"| **{unit.skipped + integration.skipped}** "
          f"| **{unit.time + integration.time:.1f}s** |\n")

    print("<details><summary>Per class</summary>\n")
    print("| Class | Tests |")
    print("|---|---:|")
    for name, count in sorted({**unit.classes, **integration.classes}.items()):
        print(f"| `{name}` | {count} |")
    print("\n</details>")

    # Always 0: the build step already failed the run if a test failed, and a second red step
    # for "could not write a summary" only obscures which thing actually broke.
    return 0


if __name__ == "__main__":
    sys.exit(main())
