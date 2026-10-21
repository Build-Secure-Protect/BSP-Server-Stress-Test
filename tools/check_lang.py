#!/usr/bin/env python3
"""Checks the mod's text: every key the code looks up exists in the language file, and no value breaks Java's
String.format. A missing key would reach a player as the raw key, so it is caught here instead.

  python3 tools/gen_lang.py && python3 tools/check_lang.py
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CODE = ROOT / "src/main/java/com/mrgregles/bsp_server_stress_test"
LANG = ROOT / "src/main/resources/assets/bsp_server_stress_test/lang/en_us.json"
P = "message.bsp_server_stress_test."
KEY = r'"([a-z0-9_]+(?:\.[a-z0-9_]+)+)"'


def main():
    lang = json.loads(LANG.read_text())
    every = "\n".join(p.read_text() for p in CODE.rglob("*.java") if "gametest" not in p.parts)
    needed = set(re.findall(r'Text\.(?:t|raw)\(' + KEY, every))
    # keys chosen by a condition: Text.t(flag ? "a" : "b")
    for a, b in re.findall(r'Text\.(?:t|raw)\([^"]*?\?\s*' + KEY + r'\s*:\s*' + KEY, every):
        needed |= {a, b}
    missing = sorted(k for k in needed if P + k not in lang)
    # keys kept as plain literals and passed on later (an enum's key), and keys built from a fixed start: "general." + id
    literals = set(re.findall(KEY, every))
    prefixes = set(re.findall(r'"([a-z0-9_]+\.(?:[a-z0-9_]+\.)*)"\s*\+', every))
    used = needed | {k[len(P):] for k in lang if k[len(P):] in literals or any(k[len(P):].startswith(x) for x in prefixes)}
    unused = sorted(k[len(P):] for k in lang if k.startswith(P) and k[len(P):] not in used)
    bad = 0
    for k, v in lang.items():
        if re.search(r"(?<!%)%(?![s%])", v):
            print("BAD %   ", k, repr(v), "(write %% for a percent sign)")
            bad += 1
    for k in missing:
        print("MISSING", P + k)
    for k in unused:
        print("unused ", P + k)
    print(f"{len(needed)} keys used, {len(missing)} missing, {len(unused)} unused")
    return 1 if missing or bad else 0


if __name__ == "__main__":
    sys.exit(main())
