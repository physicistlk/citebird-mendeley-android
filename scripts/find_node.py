#!/usr/bin/env python3
"""Find a Compose/UI node in a uiautomator dump and print its tap center.

Matching is case-insensitive. An exact match on text or content-desc wins;
otherwise the first node containing the needle (in tree order) is used.
Usage: find_node.py <dump.xml> <needle>
"""
import sys
import xml.etree.ElementTree as ET


def walk(node):
    yield node
    for child in node:
        yield from walk(child)


def center(node):
    bounds = node.get("bounds", "")
    try:
        left_top, right_bottom = bounds.split("][")
        x1, y1 = map(int, left_top.strip("[").split(","))
        x2, y2 = map(int, right_bottom.strip("]").split(","))
    except ValueError:
        return None
    return (x1 + x2) // 2, (y1 + y2) // 2


def main():
    path, needle = sys.argv[1], sys.argv[2].lower()
    root = ET.parse(path).getroot()

    exact, partial = None, None
    for node in walk(root):
        fields = [node.get("text", ""), node.get("content-desc", "")]
        lowered = [f.lower() for f in fields if f]
        if not lowered:
            continue
        if needle in lowered:
            if exact is None:
                exact = node
        elif partial is None and any(needle in f for f in lowered):
            partial = node

    # Compare against None: an ElementTree node with no children is falsy.
    node = exact if exact is not None else partial
    point = center(node) if node is not None else None
    if point is None:
        print("NOT_FOUND", file=sys.stderr)
        return 1
    print(*point)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
