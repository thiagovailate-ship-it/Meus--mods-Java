#!/usr/bin/env python3
"""Compare /wind dump storm-only samples against the closed-form Python mirror.

Usage: python3 tools/check_field.py path/to/dynamicwinds-dump.csv
The first column group retains v_total from the complete mod; the mirror checks
storm_speed and its radial/tangential/vertical components plus pressure deficit.
"""
import csv
import math
import sys

import preview_storm as mirror

RHO = 1.15
OMEGA = 7.2921e-5
LATITUDE_DEG = 18.0
TRANSLATION_SHARE = 0.6
TOLERANCE = 1.0e-6
REQUIRED = {
    "world_time", "x", "z", "y", "v_total", "v_tangential", "v_radial", "wind_vertical",
    "deficit_hpa", "storm_speed", "storm_kind", "storm_id", "spawn_tick",
    "spawn_x", "spawn_z", "heading", "turn_rate", "north", "intensity_mul",
    "surface_y", "energy",
}


def smoothstep(a, b, x):
    if b <= a:
        return 1.0 if x >= b else 0.0
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3.0 - 2.0 * t)


def storm_intensity(kind, age):
    duration = mirror.DURATION[kind]
    if age < 0 or age >= duration:
        return 0.0
    t = age / float(duration)
    if t < 0.15:
        return smoothstep(0.0, 0.15, t) * 0.55
    if t < 0.45:
        return 0.55 + smoothstep(0.15, 0.45, t) * 0.45
    if t < 0.75:
        return 1.0 - 0.10 * math.exp(-((t - 0.60) / 0.05) ** 2)
    return 1.0 - smoothstep(0.75, 1.0, t)


def storm_state(row, time):
    kind = int(row["storm_kind"])
    age = time - int(row["spawn_tick"])
    intensity = storm_intensity(kind, age)
    multiplier = float(row["intensity_mul"])
    vmax = mirror.VMAX[kind] * intensity * multiplier
    rmax = mirror.RMAX[kind] * (1.0 + 0.55 * (1.0 - intensity))
    turn = float(row["turn_rate"])
    heading0 = float(row["heading"])
    heading = heading0 + turn * age
    speed = mirror.SPEED[kind]
    if abs(turn) < 1.0e-9:
        cx = float(row["spawn_x"]) + speed * age * math.cos(heading0)
        cz = float(row["spawn_z"]) + speed * age * math.sin(heading0)
    else:
        cx = float(row["spawn_x"]) + speed / turn * (math.sin(heading) - math.sin(heading0))
        cz = float(row["spawn_z"]) + speed / turn * (math.cos(heading0) - math.cos(heading))
    return age, intensity, vmax, rmax, cx, cz, heading


def modeled_values(row, time):
    kind = int(row["storm_kind"])
    if kind < 0:
        return 0.0, 0.0, 0.0, 0.0, 0.0
    age, intensity, vmax, rmax, cx, cz, heading = storm_state(row, time)
    if intensity <= 0.0:
        return 0.0, 0.0, 0.0, 0.0, 0.0
    x, y, z = float(row["x"]), float(row["y"]), float(row["z"])
    dx, dz = x - cx, z - cz
    radius = math.sqrt(dx * dx + dz * dz)
    outer = mirror.ROUT[kind]
    if radius > outer:
        return 0.0, 0.0, 0.0, 0.0, 0.0
    r = max(radius, 0.05)
    alpha = mirror.ALPHA[kind]
    vt = vmax * (r / rmax) if r < rmax else vmax * (rmax / r) ** alpha
    north = row["north"].strip().lower() in ("true", "1")
    spin = 1.0 if north else -1.0
    tx, tz = spin * dz / r, -spin * dx / r
    inflow_deg = 6.0 + 20.0 * smoothstep(0.5 * rmax, outer, r)
    inflow = math.tan(math.radians(inflow_deg))
    hx = vt * (tx - inflow * dx / r) + speed_for_translation(kind, heading) * TRANSLATION_SHARE * math.cos(heading)
    hz = vt * (tz - inflow * dz / r) + speed_for_translation(kind, heading) * TRANSLATION_SHARE * math.sin(heading)
    top = mirror.TOP[kind]
    h = y - int(row["surface_y"])
    if h < 0:
        height_factor = 0.35
    elif h < top:
        height_factor = 0.85 + 0.15 * (h / top)
    else:
        height_factor = math.exp(-(h - top) / (0.35 * top))
    wall = math.exp(-((r - rmax) / (0.30 * rmax)) ** 2)
    eye = math.exp(-(r / (0.45 * rmax)) ** 2)
    vy = 0.38 * vt * wall - 0.10 * vmax * eye
    scale = height_factor * max(0.0, min(1.0, float(row["energy"])))
    hx, hy, hz = hx * scale, vy * scale, hz * scale
    radial = (hx * dx + hz * dz) / r if radius > 0.0 else hx
    tangential = -hx * dz / r + hz * dx / r if radius > 0.0 else hz
    speed = math.sqrt(hx * hx + hy * hy + hz * hz)
    deficit = deficit_pa(radius, vmax, rmax, alpha, coriolis(north), outer) / 100.0
    return speed, tangential, radial, hy, deficit


def speed_for_translation(kind, heading):
    return mirror.SPEED[kind]


def coriolis(north):
    value = 2.0 * OMEGA * math.sin(math.radians(LATITUDE_DEG))
    return value if north else -value


def outer_part(a, b, vmax, rmax, alpha, f):
    if b <= a or a <= 0.0 or rmax <= 0.0 or alpha <= 0.0:
        return 0.0
    v2 = RHO * vmax * vmax / (2.0 * alpha) * ((rmax / a) ** (2.0 * alpha) - (rmax / b) ** (2.0 * alpha))
    v1 = 0.0
    if abs(1.0 - alpha) > 1.0e-9:
        v1 = RHO * f * vmax * rmax ** alpha * (b ** (1.0 - alpha) - a ** (1.0 - alpha)) / (1.0 - alpha)
    return v2 + v1


def deficit_pa(radius, vmax, rmax, alpha, f, outer):
    if radius >= outer or rmax <= 0.0:
        return 0.0
    outside = outer_part(max(radius, rmax), outer, vmax, rmax, alpha, f)
    if radius < rmax:
        return RHO * (rmax * rmax - radius * radius) * 0.5 * (vmax * vmax / (rmax * rmax) + f * vmax / rmax) + outside
    return outside


def compare(path):
    failures = 0
    rows = 0
    with open(path, newline="", encoding="utf-8") as stream:
        reader = csv.DictReader(stream)
        missing = REQUIRED.difference(reader.fieldnames or [])
        if missing:
            raise ValueError("CSV missing columns: " + ", ".join(sorted(missing)))
        for line, row in enumerate(reader, 2):
            rows += 1
            time = int(float(row.get("world_time", "0") or 0))
            actual = [float(row[key]) for key in ("storm_speed", "v_tangential", "v_radial", "wind_vertical", "deficit_hpa")]
            expected = modeled_values(row, time)
            labels = ("storm_speed", "v_tangential", "v_radial", "wind_vertical", "deficit_hpa")
            for label, got, want in zip(labels, actual, expected):
                if not math.isfinite(got) or abs(got - want) > TOLERANCE * max(1.0, abs(want)):
                    failures += 1
                    if failures <= 20:
                        print("line {} {}: CSV={} mirror={} delta={}".format(line, label, got, want, got - want))
    if rows == 0:
        raise ValueError("CSV contains no samples")
    if failures:
        print("FAILED: {} mismatches across {} rows".format(failures, rows))
        return 1
    print("OK: {} rows match storm-field mirror within relative tolerance {}".format(rows, TOLERANCE))
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 tools/check_field.py path/to/dynamicwinds-dump.csv", file=sys.stderr)
        sys.exit(2)
    try:
        sys.exit(compare(sys.argv[1]))
    except (OSError, ValueError, KeyError) as error:
        print("check_field: {}".format(error), file=sys.stderr)
        sys.exit(2)
