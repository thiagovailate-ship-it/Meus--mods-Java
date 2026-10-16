#!/usr/bin/env python3
"""Self-test the CSV schema, closed-form path, and mismatch exit status."""
import contextlib
import csv
import io
import os
import tempfile

import check_field


def row_for(kind, time):
    row = {
        "world_time": str(time), "x": "0", "z": "0", "y": "74", "v_total": "0",
        "v_tangential": "0", "v_radial": "0", "wind_vertical": "0",
        "deficit_hpa": "0", "storm_speed": "0", "storm_kind": str(kind),
        "storm_id": "1", "spawn_tick": "0", "spawn_x": "0", "spawn_z": "0",
        "heading": "0.2", "turn_rate": "0", "north": "true", "intensity_mul": "1",
        "surface_y": "64", "energy": "0.8",
    }
    if kind >= 0:
        values = check_field.modeled_values(row, time)
        row["storm_speed"], row["v_tangential"], row["v_radial"], row["wind_vertical"], row["deficit_hpa"] = map(str, values)
    return row


def run_test():
    time = 12000
    rows = [row_for(3, time), row_for(-1, time)]
    with tempfile.TemporaryDirectory() as directory:
        path = os.path.join(directory, "field.csv")
        with open(path, "w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=sorted(check_field.REQUIRED))
            writer.writeheader()
            writer.writerows(rows)
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            assert check_field.compare(path) == 0
        rows[0]["storm_speed"] = str(float(rows[0]["storm_speed"]) + 1.0)
        with open(path, "w", newline="", encoding="utf-8") as stream:
            writer = csv.DictWriter(stream, fieldnames=sorted(check_field.REQUIRED))
            writer.writeheader()
            writer.writerows(rows)
        with contextlib.redirect_stdout(output):
            assert check_field.compare(path) == 1


if __name__ == "__main__":
    run_test()
    print("check_field self-test OK")
