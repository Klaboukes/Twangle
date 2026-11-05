"""Builds the Twangle website (Phase 54) and checks it.

    python -I website/build.py [output folder]      (default: website/build)

Each page in website/pages/<language>/ is the inside of one page's <main>, with a short
header comment (title, description, nav). This script wraps each in the shared frame
(head, top bar, footer) in its language, copies website/static/ beside them, and checks
the result:
  - nothing is loaded from another host: no src, srcset, poster or data, no <link> and no
    CSS url() or @import pointing elsewhere, and no <script> at all (plain <a> links to
    other sites are fine; they only open when the visitor taps them);
  - every link inside the site leads to a page or file that is there, and to an id on it;
  - every page exists in every language.
It exits with 1 and names each problem if a check fails. Only the Python standard library.
English is at the site's root, every other language in its own folder (nl/).
"""

import html
import os
import re
import shutil
import sys
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urldefrag

HERE = Path(__file__).resolve().parent
PAGES = HERE / "pages"
STATIC = HERE / "static"
# The name on its string, plucked by CSS (scripts/wordmark.py writes it), in every page's top bar.
# The top bar's icon and name (Phase 79): scripts/launcher-icon.py and scripts/wordmark.py draw them.
TWANGLE_NAME = "".join((HERE / f).read_text(encoding="utf-8").split("-->\n", 1)[1].strip()
                       for f in ("twangle-icon.svg", "twangle-name.svg"))

# English first: it is the root. Each language's folder under the site's root.
LANGUAGES = {"en": "", "nl": "nl/"}

# Paths on klaboukes.github.io served by other repositories, so they are not in the
# output but are real: the privacy policy (Klaboukes/twangle-privacy).
OTHER_REPOS = ("twangle-privacy/",)

PLAY_URL = "https://play.google.com/store/apps/details?id=io.github.klaboukes.twangle"
EMAIL = "twangle.tuner@proton.me"

# The owner's page for a coffee, empty until their Ko-fi page exists (Buy Me a Coffee's payouts
# need a KvK number, 2026-10-28). While it is empty, the footer's link and the home page's
# data-coffee line stay out, so no link is ever broken (as Help stays out until help.html exists).
COFFEE_URL = ""

# The words of the shared frame, per language. Page text lives in the pages themselves.
WORDS = {
    "en": {
        "language_name": "English",
        "home": "Twangle home",
        "nav": "Main",
        "tools": "Tools",
        "help": "Help",
        "privacy": "Privacy",
        "privacy_page": "twangle-privacy/",
        "made_by": "Made by one person. Free forever.",
        "coffee": "Buy me a coffee",
        "trademark": "Google Play and the Google Play logo are trademarks of Google LLC.",
        "skip": "Skip to the page",
    },
    "nl": {
        "language_name": "Nederlands",
        "home": "Twangle, de startpagina",
        "nav": "Hoofdmenu",
        "tools": "Functies",
        "help": "Hulp",
        "privacy": "Privacy",
        "privacy_page": "twangle-privacy/nl.html",
        "made_by": "Gemaakt door één persoon. Voor altijd gratis.",
        "coffee": "Trakteer me op een koffie",
        "trademark": "Google Play en het Google Play-logo zijn handelsmerken van Google LLC.",
        "skip": "Naar de pagina",
    },
}

FRAME = """<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>{title}</title>
<meta name="description" content="{description}">
<link rel="icon" type="image/png" href="{root}img/icon.png">
<link rel="stylesheet" href="{root}site.css">
</head>
<body>
<a class="skip" href="#main">{skip}</a>
<header><div class="wrap">
  <a class="brand" href="{home_href}" aria-label="{home}">{twangle_name}</a>
  <nav aria-label="{nav_label}">{nav}</nav>
  <a class="lang" href="{other_href}" hreflang="{other_lang}" lang="{other_lang}">{other_name}</a>
</div></header>

<main id="main"><div class="wrap">
{body}
</div></main>

<footer><div class="wrap">
  <span>{made_by}{coffee}</span>
  {footer_links}<a href="{other_href}" hreflang="{other_lang}" lang="{other_lang}">{other_name}</a><a href="mailto:{email}">{email}</a>
  <small>{trademark}</small>
</div></footer>
</body>
</html>
"""


class Problem(Exception):
    pass


def read_page(path):
    """A page source: a leading comment of 'key: value' lines, then the body."""
    text = path.read_text(encoding="utf-8")
    match = re.match(r"\s*<!--(.*?)-->\s*", text, re.S)
    if not match:
        raise Problem(f"{path}: no header comment (title, description)")
    meta = {}
    for line in match.group(1).strip().splitlines():
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip()
    for key in ("title", "description"):
        if not meta.get(key):
            raise Problem(f"{path}: the header comment has no {key}")
    return meta, text[match.end():].rstrip() + "\n"


def href(root, folder, name, here=None):
    """A relative link to a page in a language's folder, from a page in folder `here`;
    a folder's index.html is linked as the folder itself."""
    page = "" if name == "index.html" else name
    link = page if folder == here else root + folder + page
    return link or "./"


def nav_links(lang, root, current, exists):
    """The top bar's links. Help appears once its page exists, so no link is ever broken."""
    w = WORDS[lang]
    items = [("tools", href(root, LANGUAGES[lang], "index.html", LANGUAGES[lang]) + "#tools")]
    if exists("help.html"):
        items.append(("help", href(root, LANGUAGES[lang], "help.html", LANGUAGES[lang])))
    items.append(("privacy", root + w["privacy_page"]))
    out = []
    for key, link in items:
        current_attr = ' aria-current="page"' if key == current else ""
        out.append(f'<a href="{link}"{current_attr}>{w[key]}</a>')
    return "".join(out)


CARD = re.compile(r'<div data-page="([^"]+)">(.*?)</div>', re.S)


def tool_cards(body, exists):
    """A tool card on the home page names its page (<div data-page="tuner.html">): it becomes a link
    once that page exists, and stays a plain box until then, so no link is ever broken."""
    def card(match):
        page, inside = match.groups()
        return f'<a href="{page}">{inside}</a>' if exists(page) else f"<div>{inside}</div>"
    return CARD.sub(card, body)


COFFEE_LINE = re.compile(r"[ \t]*<(\w+)[^>]* data-coffee>.*?</\1>\n?", re.S)


def coffee_line(body):
    """A block marked data-coffee (the home page's line about buying a coffee, with {coffee} for the
    address; the mark is the element's last attribute) stays out until COFFEE_URL names the owner's
    page, so no link is ever broken. Once it does, the mark goes and {coffee} becomes the address."""
    if not COFFEE_URL:
        return COFFEE_LINE.sub("", body)
    return COFFEE_LINE.sub(lambda m: m.group(0).replace(" data-coffee", "", 1), body).replace("{coffee}", COFFEE_URL)


def build(out):
    if out.exists():
        shutil.rmtree(out)
    shutil.copytree(STATIC, out)
    (out / ".nojekyll").write_text("", encoding="utf-8")

    names = {lang: sorted(p.name for p in (PAGES / lang).glob("*.html")) for lang in LANGUAGES}
    problems = []
    every = set().union(*names.values())
    for lang, have in names.items():
        for missing in sorted(every - set(have)):
            problems.append(f"pages/{lang}/{missing}: missing (the page exists in another language)")
    if problems:
        raise Problem("\n".join(problems))

    for lang, folder in LANGUAGES.items():
        other = "nl" if lang == "en" else "en"
        depth = folder.count("/")
        root = "../" * depth
        for name in names[lang]:
            meta, body = read_page(PAGES / lang / name)
            w = WORDS[lang]
            footer = []
            if (PAGES / lang / "help.html").exists():
                footer.append(f'<a href="{href(root, folder, "help.html", folder)}">{w["help"]}</a>')
            footer.append(f'<a href="{root}{w["privacy_page"]}">{w["privacy"]}</a>')
            page = FRAME.format(
                lang=lang,
                title=html.escape(meta["title"]),
                description=html.escape(meta["description"]),
                root=root,
                skip=w["skip"],
                home=w["home"],
                home_href=href(root, folder, "index.html", folder),
                twangle_name=TWANGLE_NAME,
                nav_label=w["nav"],
                nav=nav_links(lang, root, meta.get("nav"), lambda n: (PAGES / lang / n).exists()),
                other_href=href(root, LANGUAGES[other], name),
                other_lang=other,
                other_name=WORDS[other]["language_name"],
                body=coffee_line(tool_cards(body, lambda n: (PAGES / lang / n).exists()))
                .replace("{play}", PLAY_URL).replace("{root}", root),
                made_by=w["made_by"],
                coffee=' <a href="{}">{}</a>'.format(COFFEE_URL, w["coffee"]) if COFFEE_URL else "",
                footer_links="".join(footer),
                email=EMAIL,
                trademark=w["trademark"],
            )
            target = out / folder / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(page, encoding="utf-8", newline="\n")  # the same on every PC


# ---- The checks, on the built output -------------------------------------------------

def is_other_host(url):
    """True for a URL that would fetch from somewhere else: a scheme (https:, http:, ftp:)
    or a protocol-relative //host. data: stays on the page; mailto: only opens mail."""
    url = url.strip()
    if url.startswith("//"):
        return True
    scheme = re.match(r"([a-zA-Z][a-zA-Z0-9+.-]*):", url)
    return bool(scheme) and scheme.group(1).lower() not in ("data",)


CSS_URL = re.compile(r"url\(\s*(['\"]?)(.*?)\1\s*\)", re.I)
CSS_IMPORT = re.compile(r"@import\s+(?:url\(\s*)?(['\"]?)([^'\")\s;]+)", re.I)


def css_loads(css):
    return [m.group(2) for m in CSS_URL.finditer(css)] + [m.group(2) for m in CSS_IMPORT.finditer(css)]


class PageScan(HTMLParser):
    LOAD_ATTRS = ("src", "srcset", "poster", "data", "background")

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.loads = []      # (what, url) fetched when the page opens
        self.links = []      # hrefs of <a>, only followed when tapped
        self.ids = set()
        self.scripts = 0
        self.styles = []     # inline CSS, from <style> and style=""
        self._in_style = False

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            self.ids.add(a["id"])
        if tag == "script":
            self.scripts += 1
        if tag == "style":
            self._in_style = True
        if a.get("style"):
            self.styles.append(a["style"])
        for name in self.LOAD_ATTRS:
            if a.get(name):
                for part in a[name].split(","):
                    url = part.strip().split(" ")[0]
                    if url:
                        self.loads.append((f"<{tag} {name}>", url))
        if tag == "link" and a.get("href"):
            self.loads.append(("<link href>", a["href"]))
        if tag == "a" and a.get("href"):
            self.links.append(a["href"])

    def handle_endtag(self, tag):
        if tag == "style":
            self._in_style = False

    def handle_data(self, data):
        if self._in_style:
            self.styles.append(data)


def check(out):
    problems = []
    scans = {}
    for path in sorted(out.rglob("*")):
        rel = path.relative_to(out).as_posix()
        if path.suffix == ".css":
            for url in css_loads(path.read_text(encoding="utf-8")):
                if is_other_host(url):
                    problems.append(f"{rel}: loads {url} from another site")
        elif path.suffix == ".html":
            scan = PageScan()
            scan.feed(path.read_text(encoding="utf-8"))
            scans[rel] = scan
            if scan.scripts:
                problems.append(f"{rel}: has a <script>; the site works without JavaScript")
            for css in scan.styles:
                for url in css_loads(css):
                    scan.loads.append(("CSS url()", url))
            for what, url in scan.loads:
                if is_other_host(url):
                    problems.append(f"{rel}: {what} loads {url} from another site")

    # Links and loads inside the site must lead somewhere.
    for rel, scan in scans.items():
        base = Path(rel).parent
        for url in scan.links + [u for _, u in scan.loads]:
            if is_other_host(url) or url.startswith("mailto:") or url.startswith("data:"):
                continue
            target, fragment = urldefrag(unquote(url))
            if target:
                resolved = os.path.normpath((base / target).as_posix()).replace("\\", "/")
                if resolved.startswith(".."):
                    problems.append(f"{rel}: {url} leads outside the site")
                    continue
                if any((resolved + "/").startswith(p) or resolved.startswith(p) for p in OTHER_REPOS):
                    continue
                file = out / resolved
                if file.is_dir():
                    file = file / "index.html"
                    resolved = (Path(resolved) / "index.html").as_posix()
                if not file.exists():
                    problems.append(f"{rel}: {url} leads to a page that isn't there")
                    continue
            else:
                resolved = rel
            if fragment and resolved in scans and fragment not in scans[resolved].ids:
                problems.append(f"{rel}: {url} points to #{fragment}, which {resolved} doesn't have")
    if problems:
        raise Problem("\n".join(dict.fromkeys(problems)))  # each problem once
    return len(scans)


def main():
    out = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else HERE / "build"
    try:
        build(out)
        pages = check(out)
    except Problem as problem:
        print("The website is not ready:", file=sys.stderr)
        print(problem, file=sys.stderr)
        sys.exit(1)
    print(f"Built {pages} pages in {out}; nothing loads from another site, every link leads somewhere.")


if __name__ == "__main__":
    main()
