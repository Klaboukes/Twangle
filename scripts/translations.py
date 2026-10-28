#!/usr/bin/env python3
"""Twangle's translation record: which English wording each translation was made from.

English and the day languages (twangle.dayLanguages in gradle.properties, en,nl) are written by day;
every other language of twangle.languages is translated at night by the /translate skill. The record,
translations/<language>.txt, lists per translated text a short hash of the English it was made from,
so a reworded English text shows up as stale, not only a new one (docs/architecture/languages.md,
*Day and night*). LocalizationTest computes the same hash; its strict mode
(-Ptwangle.strictTranslations=true) holds every language to the record.

    python -I scripts/translations.py status [language ...]
        Per language: the texts missing and the texts stale (reworded in English since they were
        translated, or not in the record yet). Exit code 1 when anything is left to do.
    python -I scripts/translations.py status --json [language ...]
        The same, with each text's English, its translator note, the Dutch and the language's current
        version as XML, for the translators (the /translate skill).
    python -I scripts/translations.py mark <language ...|all> [--only <module>:<name> ...]
        Records that the language's translations match today's English: every text the language has,
        or only the named ones. Entries of texts English no longer has are dropped.
"""

import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RECORD_DIR = ROOT / "translations"
WHITESPACE = re.compile(r"[ \t\r\n]+")


def gradle_property(name):
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith(name + "="):
            return [x.strip() for x in line.split("=", 1)[1].split(",") if x.strip()]
    return []


LANGUAGES = gradle_property("twangle.languages")
DAY_LANGUAGES = gradle_property("twangle.dayLanguages")
TRANSLATIONS = LANGUAGES[1:]


def modules():
    """The module paths (app, feature/tuner, ...) that have English texts."""
    found = []
    for pattern in ("app", "wear", "feature/*", "core/*"):
        for module in sorted(ROOT.glob(pattern)):
            if (module / "src/main/res/values/strings.xml").is_file():
                found.append(module.relative_to(ROOT).as_posix())
    return found


def strings_file(module, language=None):
    folder = "values" if language is None else f"values-{language}"
    return ROOT / module / "src/main/res" / folder / "strings.xml"


def normalized(element):
    return WHITESPACE.sub(" ", "".join(element.itertext())).strip(" \t\r\n")


def english_hash(element):
    """Hash of a <string>'s text, or of a <plurals>' items as quantity=text lines (LocalizationTest)."""
    if element.tag == "plurals":
        text = "\n".join(f"{item.get('quantity')}={normalized(item)}" for item in element if item.tag == "item")
    else:
        text = normalized(element)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()[:8]


def texts(path):
    """Name -> (element, note) of each translatable <string> and <plurals>; note = comments right above it."""
    if not path.is_file():
        return {}
    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
    root = ET.parse(path, parser).getroot()
    result, notes, section = {}, [], ""
    for e in root:
        if e.tag is ET.Comment:
            notes.append(e.text.strip())
            continue
        if e.tag in ("string", "plurals") and e.get("translatable") != "false":
            note = " ".join(notes) or (f"(section: {section})" if section else "")
            result[e.get("name")] = (e, note)
        if notes:
            section = " ".join(notes)
        notes = []
    return result


def raw_xml(path, name):
    """The element as written in the file (escapes and all), for the translators."""
    if not path.is_file():
        return None
    text = path.read_text(encoding="utf-8")
    match = re.search(
        rf'<(string|plurals)\s+name="{re.escape(name)}"[^>]*?(/>|>.*?</\1>)', text, re.DOTALL
    )
    return match.group(0) if match else None


def record_file(language):
    return RECORD_DIR / f"{language}.txt"


def read_record(language):
    path = record_file(language)
    entries = {}
    if path.is_file():
        for line in path.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                module, name, digest = line.split()
                entries[(module, name)] = digest
    return entries


def write_record(language, entries):
    lines = [
        f"# The English each {language} translation was made from: <module> <name> <hash of the English>.",
        "# Written by scripts/translations.py mark (the /translate skill); checked by LocalizationTest.",
    ]
    lines += [f"{module} {name} {digest}" for (module, name), digest in sorted(entries.items())]
    RECORD_DIR.mkdir(exist_ok=True)
    record_file(language).write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def status(languages):
    """Language -> list of {module, name, state, ...} for every missing or stale text."""
    english = {m: texts(strings_file(m)) for m in modules()}
    result = {}
    for language in languages:
        record = read_record(language)
        todo = []
        for module, items in english.items():
            translated = texts(strings_file(module, language))
            for name, (element, note) in items.items():
                if name not in translated:
                    state = "missing"
                elif record.get((module, name)) != english_hash(element):
                    state = "stale"
                else:
                    continue
                todo.append({
                    "module": module,
                    "name": name,
                    "state": state,
                    "note": note,
                    "english": raw_xml(strings_file(module), name),
                    "dutch": raw_xml(strings_file(module, "nl"), name),
                    "current": raw_xml(strings_file(module, language), name),
                    "file": strings_file(module, language).relative_to(ROOT).as_posix(),
                })
        result[language] = todo
    return result


def mark(languages, only):
    english = {m: texts(strings_file(m)) for m in modules()}
    for language in languages:
        record = {k: v for k, v in read_record(language).items() if k[0] in english and k[1] in english[k[0]]}
        for module, items in english.items():
            translated = texts(strings_file(module, language))
            for name, (element, _) in items.items():
                if only and f"{module}:{name}" not in only:
                    continue
                if name in translated:
                    record[(module, name)] = english_hash(element)
                else:
                    record.pop((module, name), None)
        write_record(language, record)
        print(f"{language}: {len(record)} texts recorded")


def pick(args):
    if args == ["all"]:
        return TRANSLATIONS
    unknown = [a for a in args if a not in TRANSLATIONS]
    if unknown:
        sys.exit(f"not a translated language in twangle.languages: {', '.join(unknown)}")
    return args or TRANSLATIONS


def main(argv):
    sys.stdout.reconfigure(encoding="utf-8")  # the Windows console's code page lacks most scripts
    if not argv or argv[0] not in ("status", "mark"):
        sys.exit(__doc__)
    command, args = argv[0], argv[1:]
    if command == "status":
        as_json = "--json" in args
        result = status(pick([a for a in args if a != "--json"]))
        if as_json:
            print(json.dumps(result, ensure_ascii=False, indent=1))
        else:
            for language, todo in result.items():
                missing = sum(t["state"] == "missing" for t in todo)
                stale = len(todo) - missing
                print(f"{language}: {'up to date' if not todo else f'{missing} missing, {stale} stale'}")
        return 1 if any(result.values()) else 0
    only = set()
    if "--only" in args:
        i = args.index("--only")
        only, args = set(args[i + 1:]), args[:i]
    if not args:
        sys.exit("mark needs a language, or all")
    mark(pick(args), only)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
