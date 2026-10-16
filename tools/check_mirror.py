#!/usr/bin/env python3
"""Fail when duplicated Java/Python simulation constants drift apart."""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_KIND = os.path.join(ROOT, "src/main/java/com/dynamicwinds/core/StormKind.java")
JAVA_RENDER = os.path.join(ROOT, "src/main/java/com/dynamicwinds/client/storm/StormRenderer.java")
JAVA_AUDIO = os.path.join(ROOT, "src/main/java/com/dynamicwinds/client/audio/StormAudio.java")
PY_STORM = os.path.join(ROOT, "tools/preview_storm.py")
PY_SOUNDS = os.path.join(ROOT, "tools/gen_sounds.py")
PY_TEXTURES = os.path.join(ROOT, "tools/gen_textures.py")
PY_SEAMS = os.path.join(ROOT, "tools/check_seams.py")


def text(path):
    with open(path, encoding="utf-8") as source:
        return source.read()


def numbers(value):
    return [float(token.rstrip("lLfFdD")) for token in re.findall(
        r"(?<![A-Za-z_])[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?[lLfFdD]?", value)]


def java_array(source, name):
    match = re.search(r"\b" + re.escape(name) + r"\s*=\s*\{([^}]*)\}", source, re.S)
    if not match:
        raise ValueError("Java array not found: " + name)
    return numbers(match.group(1))


def python_assignment(source, name):
    match = re.search(r"^" + re.escape(name) + r"\s*=\s*(\[[^\]]*\])\s*$", source, re.M)
    if not match:
        raise ValueError("Python mirror not found: " + name)
    return numbers(match.group(1))


def java_scalar(source, name):
    match = re.search(r"\b" + re.escape(name) + r"\s*=\s*([-+0-9.eE]+)[fFdD]?\s*;", source)
    if not match:
        raise ValueError("Java scalar not found: " + name)
    return float(match.group(1))


def python_scalar(source, name):
    match = re.search(r"^" + re.escape(name) + r"\s*=\s*([-+0-9.eE]+)\s*$", source, re.M)
    if not match:
        raise ValueError("Python mirror scalar not found: " + name)
    return float(match.group(1))


def function_numbers(source, name):
    match = re.search(r"(?:private\s+static\s+)?(?:double|float)\s+" + re.escape(name)
                     + r"\s*\([^)]*\)\s*\{(.*?)\n\s*\}", source, re.S)
    if not match:
        match = re.search(r"^def\s+" + re.escape(name) + r"\s*\([^)]*\):\n((?:[ \t]+.*\n)+)", source, re.M)
    if not match:
        raise ValueError("function not found: " + name)
    return numbers(match.group(1))


def compare(label, actual, expected, errors):
    if actual != expected:
        errors.append("{}: Java={} Python={}".format(label, actual, expected))


def main():
    kind, renderer, audio = text(JAVA_KIND), text(JAVA_RENDER), text(JAVA_AUDIO)
    storm, sounds = text(PY_STORM), text(PY_SOUNDS)
    errors = []

    for name in ("VMAX", "RMAX", "ROUT", "TOP", "ALPHA", "DURATION", "SPEED"):
        compare("StormKind." + name, java_array(kind, name), python_assignment(storm, name), errors)
    for name in ("DECK_FRACTION", "DECK_DROP", "SKIRT_DROP", "SKY_LIMIT", "BAND_ARMS",
                 "BAND_STEPS", "SEG_NEAR", "SEG_MID", "SEG_FAR", "LOD_NEAR", "LOD_MID"):
        compare("StormRenderer." + name, [java_scalar(renderer, name)],
                [python_scalar(storm, name)], errors)
    compare("StormRenderer.alphaDeck coefficient set",
            sorted(function_numbers(renderer, "alphaDeck")),
            sorted(function_numbers(storm, "alpha_deck")), errors)

    audio_match = re.search(r"MIRROR_AUDIO\s*=\s*\{(.*?)\n\}", sounds, re.S)
    if not audio_match:
        raise ValueError("MIRROR_AUDIO dictionary not found in gen_sounds.py")
    mirror_audio = {key: float(value) for key, value in re.findall(
        r"['\"](\w+)['\"]\s*:\s*([-+0-9.eE]+)", audio_match.group(1))}
    for name in ("THUNDER_PERIOD", "THUNDER_RANGE", "RANGE_PER_VOLUME", "CLOSE_DISTANCE",
                 "MID_DISTANCE", "SOUND_SPEED"):
        if name not in mirror_audio:
            raise ValueError("MIRROR_AUDIO missing " + name)
        compare("StormAudio." + name, [java_scalar(audio, name)], [mirror_audio[name]], errors)

    texture_source = text(PY_TEXTURES)
    seam_match = re.search(r"^SEAM_RATIO_LIMIT\s*=\s*([0-9.]+)\s*$", texture_source, re.M)
    if not seam_match:
        raise ValueError("SEAM_RATIO_LIMIT not found in gen_textures.py")
    seam_limit = float(seam_match.group(1))
    if not re.search(r"^LIMITE\s*=\s*SEAM_RATIO_LIMIT\s*$", text(PY_SEAMS), re.M):
        errors.append("check_seams.py must consume gen_textures.SEAM_RATIO_LIMIT")
    if seam_limit != 1.6:
        errors.append("seam threshold expected baseline 1.6, got {}".format(seam_limit))

    if errors:
        print("Mirror parity FAILED:")
        for error in errors:
            print("  - " + error)
        return 1
    print("Mirror parity OK: StormKind, StormRenderer, StormAudio and seam limit.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError) as error:
        print("Mirror parity FAILED: {}".format(error), file=sys.stderr)
        sys.exit(1)
