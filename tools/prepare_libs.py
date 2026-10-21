#!/usr/bin/env python3
"""Copies the pack's mod jars that the working setups are tested against into libs/ (owner decision D41).

libs/ is never committed (.gitignore) and the built mod never depends on these jars: dev runs and GameTests load them
(build.gradle, runtimeOnly), so a setup is proven against the real mod. A jar's own bundled libraries (META-INF/jarjar)
are taken out and written beside it, so ForgeGradle can turn each one into dev names on its own.

  python3 tools/prepare_libs.py [mods folder]

The default mods folder is ../BSP-Servers/BSP-Spawn (Current)/mods. Rerun after the pack updates a family's mod.
"""
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIBS = ROOT / "libs"
DEFAULT_MODS = ROOT.parent / "BSP-Servers" / "BSP-Spawn (Current)" / "mods"

# (file name pattern in the mods folder, name in libs/): each family's mods and the mods they need to load.
FAMILIES = [
    (r"create-1\.20\.1-(?P<v>[\d.]+)\.jar", "create"),
    (r"Mekanism-1\.20\.1-(?P<v>[\d.]+)\.jar", "mekanism"),
    (r"refinedstorage-(?P<v>[\d.]+)\.jar", "refinedstorage"),
    (r"appliedenergistics2-forge-(?P<v>[\d.]+)\.jar", "ae2"),
    (r"guideme-(?P<v>[\d.]+)\.jar", "guideme"),
    (r"cofh_core-1\.20\.1-(?P<v>[\d.]+)\.jar", "cofh_core"),
    (r"thermal_foundation-1\.20\.1-(?P<v>[\d.]+)\.jar", "thermal_foundation"),
    (r"thermal_expansion-1\.20\.1-(?P<v>[\d.]+)\.jar", "thermal_expansion"),
    (r"Powah-(?P<v>[\d.]+)\.jar", "powah"),
    (r"architectury-(?P<v>[\d.]+)-forge\.jar", "architectury"),
    (r"cloth-config-(?P<v>[\d.]+)-forge\.jar", "cloth_config"),
    (r"EnderIO-1\.20\.1-(?P<v>[\d.]+)-beta-all\.jar", "enderio"),
    (r"industrial-foregoing-1\.20\.1-(?P<v>[\d.]+)\.jar", "industrialforegoing"),
    (r"titanium-1\.20\.1-(?P<v>[\d.]+)\.jar", "titanium"),
    (r"Botania-1\.20\.1-(?P<v>[\d.]+)-FORGE\.jar", "botania"),
    (r"curios-forge-(?P<v>[\d.]+)\+1\.20\.1\.jar", "curios"),
    (r"Patchouli-1\.20\.1-(?P<v>[\d.]+)-FORGE\.jar", "patchouli"),
]
# Bundled mods by file name, and their name in libs/. Two families bundling the same one: the newest is kept.
# Bundled plain libraries (Guava and the like) are left out: the game already has them.
NESTED = [
    (r"flywheel-forge-1\.20\.1-(?P<v>[\d.]+)\.jar", "flywheel"),
    (r"Ponder-Forge-1\.20\.1-(?P<v>[\d.]+)\.jar", "ponder"),
    (r"Registrate-MC1\.20-(?P<v>[\d.]+)\.jar", "registrate"),
    (r"mixinextras-forge-(?P<v>[\d.]+)\.jar", "mixinextras"),
    (r"thermal_core-1\.20\.1-(?P<v>[\d.]+)\.jar", "thermal_core"),
    (r"GraphLib3-(?P<v>[\d.]+)\.jar", "graphlib"),
]


def version_key(v):
    return [int(x) for x in v.split(".") if x.isdigit()]


def keep_newest(name, version, data):
    """Writes libs/<name>-<version>.jar unless a newer one of that name is there; removes older ones."""
    for old in LIBS.glob(f"{name}-*.jar"):
        m = re.fullmatch(re.escape(name) + r"-(?P<v>[\d.]+)\.jar", old.name)
        if not m:
            continue
        if version_key(m.group("v")) > version_key(version):
            return None
        old.unlink()
    target = LIBS / f"{name}-{version}.jar"
    target.write_bytes(data)
    return target


def main():
    mods = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_MODS
    if not mods.is_dir():
        sys.exit(f"No mods folder at {mods}")
    LIBS.mkdir(exist_ok=True)
    for old in LIBS.glob("*.jar"):
        old.unlink()
    for pattern, name in FAMILIES:
        found = [f for f in mods.iterdir() if re.fullmatch(pattern, f.name)]
        if len(found) != 1:
            sys.exit(f"Expected one jar matching {pattern} in {mods}, found {len(found)}")
        jar = found[0]
        version = re.fullmatch(pattern, jar.name).group("v")
        slim = LIBS / f"{name}-{version}.jar"
        with zipfile.ZipFile(jar) as src, zipfile.ZipFile(slim, "w", zipfile.ZIP_DEFLATED) as out:
            for item in src.infolist():
                if item.filename.startswith("META-INF/jarjar/"):
                    base = item.filename.rsplit("/", 1)[-1]
                    for npattern, nname in NESTED:
                        m = re.fullmatch(npattern, base)
                        if m and keep_newest(nname, m.group("v"), src.read(item)):
                            print(f"libs/{nname}-{m.group('v')}.jar (from {jar.name})")
                    continue
                out.writestr(item, src.read(item))
        print(f"libs/{slim.name}")


if __name__ == "__main__":
    main()
