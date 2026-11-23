#!/usr/bin/env python3
"""Structural sanity for the GitHub workflows.

A malformed workflow does not fail loudly. The run is created, no jobs start, and
GitHub reports a bare "failure" with no job list and no logs — which is exactly what
happened when two `- name:` keys ended up on one line while editing release.yml. The
tag was pushed, a release run was created, and it died before running anything.

PyYAML is not available in every environment this runs in, so this checks the
specific structures that matter rather than pretending to be a parser.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
problems = []

for path in sorted((ROOT / ".github/workflows").glob("*.yml")):
    text = path.read_text(encoding="utf-8")
    lines = text.split("\n")
    name = path.name

    for i, ln in enumerate(lines, 1):
        # Tabs are never valid YAML indentation.
        if "\t" in ln:
            problems.append(f"{name}:{i}: tab character in YAML")
        # Two step keys on one line can only be a bad edit.
        if ln.count("- name:") > 1:
            problems.append(f"{name}:{i}: two step names on one line")
        # Step names must be aligned with their siblings.
        m = re.match(r"^(\s*)- name:", ln)
        if m and len(m.group(1)) != 6:
            problems.append(
                f"{name}:{i}: step name indented {len(m.group(1))} spaces, expected 6"
            )

    # Every step that is ours (has a `run:`) must declare a command.
    body = re.sub(r"^\s*#.*$", "", text, flags=re.M)
    steps = re.findall(r"- name:[^\n]*\n(.*?)(?=\n\s*- name:|\n\s*#|\Z)", body, re.S)
    for st in steps:
        if "run:" not in st and "uses:" not in st:
            problems.append(f"{name}: a step declares neither run: nor uses:")

    # A workflow with no jobs cannot do anything.
    if not re.search(r"^jobs:", text, re.M):
        problems.append(f"{name}: no jobs: section")

    # The two duplicate-step-name case that broke a release: identical names in one file.
    names = re.findall(r"^\s*- name:\s*(.+)$", text, re.M)
    seen = {}
    for n in names:
        n = n.strip()
        seen[n] = seen.get(n, 0) + 1
    for n, c in seen.items():
        if c > 1:
            problems.append(f"{name}: step name {n!r} appears {c} times")

if problems:
    print(f"FAIL: {len(problems)} workflow problem(s)")
    for p in problems:
        print(f"  - {p}")
    sys.exit(1)
print("PASS: both workflows are structurally sound")
