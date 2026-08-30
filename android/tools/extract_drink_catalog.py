"""Extracts the starter drink catalog from DrinkDatabase.swift into JSON.

The catalog is 591 entries and feeds a permille number, so it is generated,
never transcribed. Run from the repository root:

    python android/tools/extract_drink_catalog.py

Rerun this whenever DrinkDatabase.swift changes; the assets JSON is a build
artefact of the Swift source, not a second source of truth.
"""

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SOURCE = os.path.join(ROOT, "Alcoholtracker", "Services", "DrinkDatabase.swift")
TARGET = os.path.join(ROOT, "android", "app", "src", "main", "assets", "drink_catalog.json")

# Swift enum case -> the DrinkCategory raw value both apps store.
FIELDS = ("name", "category", "volume", "abv", "calories", "iconName")


def calls(source):
    """Yields every balanced DrinkTemplate(...) call. A regex misses the calls
    that wrap across lines, so the parens are matched by hand."""
    i = 0
    marker = "DrinkTemplate("
    while True:
        start = source.find(marker, i)
        if start < 0:
            return
        cursor = start + len(marker)
        depth = 1
        while depth:
            if source[cursor] == "(":
                depth += 1
            elif source[cursor] == ")":
                depth -= 1
            cursor += 1
        yield source[start + len(marker):cursor - 1]
        i = cursor


def parse(inner):
    parts = re.split(r",\s*(?=\w+:)", inner.strip())
    args = {}
    for part in parts:
        label, _, value = part.partition(":")
        args[label.strip()] = value.strip()
    if tuple(args) != FIELDS:
        raise SystemExit("unexpected argument list: %s" % (tuple(args),))
    return {
        "name": json.loads(args["name"]),
        "category": args["category"].lstrip("."),
        "volumeML": float(args["volume"]),
        "abv": float(args["abv"]),
        "calories": int(args["calories"]),
        "iconName": json.loads(args["iconName"]),
    }


def main():
    source = open(SOURCE, encoding="utf-8").read()

    order = re.search(r"static let defaults: \[DrinkTemplate\] =([^\n]+(?:\n\s*\+[^\n]+)*)", source)
    if not order:
        raise SystemExit("could not find the defaults concatenation")
    arrays = [name.strip() for name in order.group(1).split("+")]

    bounds = {}
    for match in re.finditer(r"static let (\w+): \[DrinkTemplate\] = \[", source):
        bounds[match.group(1)] = match.end()

    missing = [name for name in arrays if name not in bounds]
    if missing:
        raise SystemExit("defaults references arrays that do not exist: %s" % missing)

    starts = sorted(bounds.values())
    catalog = []
    for name in arrays:
        start = bounds[name]
        after = [s for s in starts if s > start]
        end = after[0] if after else len(source)
        catalog.extend(parse(inner) for inner in calls(source[start:end]))

    total = source.count("DrinkTemplate(")
    if len(catalog) != total:
        raise SystemExit("dropped entries: parsed %d of %d" % (len(catalog), total))

    # The seeder is version-gated exactly like seedIfNeeded, so the version has
    # to travel with the entries instead of being copied into Kotlin by hand.
    version = re.search(r"catalogVersion = (\d+)", source)
    if not version:
        raise SystemExit("could not find catalogVersion")

    names = [entry["name"] for entry in catalog]
    duplicates = sorted({n for n in names if names.count(n) > 1})

    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w", encoding="utf-8") as out:
        json.dump({"version": int(version.group(1)), "drinks": catalog},
                  out, ensure_ascii=False, indent=1)
        out.write("\n")

    print("wrote %d entries to %s" % (len(catalog), TARGET))
    if duplicates:
        print("note: %d duplicate names carried over from iOS: %s"
              % (len(duplicates), ", ".join(duplicates[:8])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
