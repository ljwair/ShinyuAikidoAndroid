#!/usr/bin/env python3
import html as html_lib
import json
import re
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

PAGE_URL = "https://www.shinyuembody.org/aiki-theme"
OUTPUT = Path("app-content/aiki-theme.json")

BOILERPLATE = (
    "cookie", "privacy", "contact", "copyright", "shinyu body", "enhancing life",
    "whatsapp", "join the weekly", "read more", "menu", "home", "aikido schedule"
)


def fetch_page() -> str:
    request = urllib.request.Request(
        PAGE_URL,
        headers={
            "User-Agent": "Mozilla/5.0 ShinyuAikidoThemeSync/1.0",
            "Accept": "text/html,application/xhtml+xml",
            "Cache-Control": "no-cache",
        },
    )
    with urllib.request.urlopen(request, timeout=25) as response:
        if response.status < 200 or response.status >= 300:
            raise RuntimeError(f"HTTP {response.status}")
        data = response.read(4_000_001)
        if len(data) > 4_000_000:
            raise RuntimeError("theme page unexpectedly large")
        return data.decode("utf-8", errors="replace")


def decode_hostinger(raw: str) -> str:
    text = raw
    for _ in range(4):
        before = text
        text = html_lib.unescape(text)
        text = text.replace(r"\u003C", "<").replace(r"\u003E", ">")
        text = text.replace(r'\"', '"').replace(r"\/", "/").replace(r"\n", "\n")
        if text == before:
            break
    return text


def clean_text(fragment: str) -> str:
    fragment = re.sub(r"(?is)<(script|style)\b[^>]*>.*?</\1>", " ", fragment)
    fragment = re.sub(r"(?is)<!--.*?-->", " ", fragment)
    fragment = re.sub(r"(?i)<br\s*/?>", "\n", fragment)
    fragment = re.sub(r"(?i)</(?:p|div|li|h[1-6]|section|article|blockquote)>", "\n", fragment)
    fragment = re.sub(r"(?is)<[^>]+>", " ", fragment)
    fragment = html_lib.unescape(fragment).replace("\xa0", " ")
    lines = []
    for line in fragment.splitlines():
        line = re.sub(r"\s+", " ", line).strip()
        if line:
            lines.append(line)
    return "\n".join(lines)


def extract_blocks(source: str):
    blocks = []
    for match in re.finditer(r"(?is)<(h[1-6]|p|blockquote)\b[^>]*>(.*?)</\1>", source):
        kind = match.group(1).lower()
        text = clean_text(match.group(2)).replace("\n", " ").strip()
        if text:
            blocks.append((kind, text))
    return blocks


def useful(text: str, minimum: int = 3, maximum: int = 520) -> bool:
    low = text.lower()
    return minimum <= len(text) <= maximum and not any(token in low for token in BOILERPLATE)


def derive_from_theme_id(source: str):
    match = re.search(r'id=["\']aiki-theme-([a-z0-9-]+)["\']', source, re.I)
    if not match:
        return None
    words = match.group(1).split("-")
    return " ".join(word.capitalize() for word in words)


def parse_theme(raw: str):
    source = decode_hostinger(raw)
    blocks = extract_blocks(source)
    if not blocks:
        raise RuntimeError("no headings or paragraphs found on theme page")

    marker = next(
        (i for i, (kind, text) in enumerate(blocks)
         if kind.startswith("h") and ("aiki theme" in text.lower() or "theme of the week" in text.lower())),
        None,
    )
    start = (marker + 1) if marker is not None else 0
    tail = blocks[start:]

    title_index = next(
        (i for i, (kind, text) in enumerate(tail)
         if kind.startswith("h") and useful(text, 3, 120)
         and "aiki theme" not in text.lower()
         and "theme of the week" not in text.lower()
         and "exercise" not in text.lower()
         and "practice" not in text.lower()),
        None,
    )

    if title_index is None:
        title = derive_from_theme_id(source)
        if not title:
            raise RuntimeError("could not identify current theme title")
        title_index = -1
    else:
        title = tail[title_index][1]

    after_title = tail[title_index + 1:] if title_index >= 0 else tail
    paragraph_candidates = [
        text for kind, text in after_title
        if kind in ("p", "blockquote") and useful(text, 20, 520)
    ]

    focus = next(
        (text for text in paragraph_candidates
         if not any(x in text.lower() for x in ("practice", "exercise", "try this", "reflection", "join us", "whatsapp"))),
        None,
    )
    if not focus:
        raise RuntimeError("could not identify theme focus text")

    practice = None
    for i, (kind, text) in enumerate(after_title):
        low = text.lower()
        if kind.startswith("h") and any(x in low for x in ("practice", "exercise", "reflection", "try this")):
            practice = next(
                (candidate for candidate_kind, candidate in after_title[i + 1:]
                 if candidate_kind in ("p", "blockquote") and useful(candidate, 12, 520)),
                None,
            )
            if practice:
                break

    if not practice:
        practice = next((text for text in paragraph_candidates if text != focus), None)
    if not practice:
        practice = "Explore this theme during your next practice."

    return {
        "title": title[:120],
        "focus": focus[:420],
        "practicePrompt": practice[:420],
        "pageUrl": PAGE_URL,
        "updated": datetime.now(timezone.utc).date().isoformat(),
    }


def main():
    theme = parse_theme(fetch_page())
    if not theme["title"] or not theme["focus"] or not theme["practicePrompt"]:
        raise RuntimeError("refusing to write incomplete theme data")

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(theme, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(theme, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Aiki theme sync failed: {exc}", file=sys.stderr)
        sys.exit(1)
