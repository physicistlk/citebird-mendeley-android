#!/usr/bin/env python3
"""Builds a small multi-page sample PDF for device testing (no dependencies)."""
import os

PAGES = 6
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "debug", "assets", "sample.pdf")


def esc(text: str) -> str:
    return text.replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")


def page_content(index: int) -> bytes:
    lines = [
        "BT",
        "/F1 24 Tf",
        "72 770 Td",
        f"(Sample Paper - Page {index + 1}) Tj",
        "/F1 12 Tf",
        "0 -30 Td",
        "(A. Researcher, B. Scientist - Journal of Tablet Reading, 2026) Tj",
        "/F1 13 Tf",
        "0 -46 Td",
        "(Abstract) Tj",
        "/F1 10 Tf",
        "0 -20 Td",
        "(This document is bundled with the debug build so the tablet UI can be) Tj",
        "0 -14 Td",
        "(exercised end to end without touching the system file picker.) Tj",
        "/F1 13 Tf",
        "0 -40 Td",
        "(1. Introduction) Tj",
        "/F1 10 Tf",
    ]
    for row in range(18):
        lines.append("0 -16 Td")
        lines.append(
            f"(Line {row + 1}: the quick brown fox jumps over the lazy dog, page {index + 1}.) Tj"
        )
    lines += [
        "/F1 48 Tf",
        "0 -70 Td",
        f"({index + 1}) Tj",
        "ET",
    ]
    return ("\n".join(lines) + "\n").encode("latin-1")


contents = [page_content(i) for i in range(PAGES)]

objects = {}
objects[1] = b"<< /Type /Catalog /Pages 2 0 R >>"
kids = " ".join(f"{4 + 2 * i} 0 R" for i in range(PAGES))
objects[2] = f"<< /Type /Pages /Kids [{kids}] /Count {PAGES} >>".encode()
objects[3] = b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

for i, content in enumerate(contents):
    page_num = 4 + 2 * i
    content_num = page_num + 1
    objects[page_num] = (
        f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
        f"/Resources << /Font << /F1 3 0 R >> >> /Contents {content_num} 0 R >>"
    ).encode()
    objects[content_num] = (
        b"<< /Length " + str(len(content)).encode() + b" >>\nstream\n" + content + b"endstream"
    )

out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
offsets = {}
for num in sorted(objects):
    offsets[num] = len(out)
    out += f"{num} 0 obj\n".encode() + objects[num] + b"\nendobj\n"

xref_at = len(out)
count = max(objects) + 1
out += f"xref\n0 {count}\n".encode()
out += b"0000000000 65535 f \n"
for num in range(1, count):
    out += f"{offsets[num]:010d} 00000 n \n".encode()
out += (
    f"trailer\n<< /Size {count} /Root 1 0 R >>\nstartxref\n{xref_at}\n%%EOF\n"
).encode()

os.makedirs(os.path.dirname(os.path.abspath(OUT)), exist_ok=True)
with open(OUT, "wb") as fh:
    fh.write(out)
print(f"wrote {os.path.abspath(OUT)} ({len(out)} bytes, {PAGES} pages)")
