#!/usr/bin/env python3
"""Host-specific publication tasks for one *complete* Kotlin Multiplatform release.

All inputs come from the compatibility manifest; no duplicate host publication.
The root KMP metadata, JVM, JS, Wasm, Android and Linux are built on Linux.
Native Apple and Windows artifacts are produced on their own supported hosts.
"""
import argparse
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PUBLICATIONS = {
    "linux": ("kotlinMultiplatform", "jvm", "js", "wasmJs", "android", "linuxX64"),
    "macos": ("iosX64", "iosArm64", "iosSimulatorArm64", "macosX64", "macosArm64"),
    "windows": ("mingwX64",),
}

def release_tasks(host: str, manifest: dict) -> list[str]:
    if host not in PUBLICATIONS:
        raise ValueError("Unknown release build host: " + host)
    result = []
    for name, spec in sorted(manifest["modules"].items()):
        kind = spec["publication"]
        if kind == "none":
            continue
        publications = PUBLICATIONS[host] if kind == "kmp" else (("maven",) if host == "linux" else ())
        for publication in publications:
            result.append(":" + name + ":publish" + publication[0].upper() +
                          publication[1:] + "PublicationToCentralStagingRepository")
    return result

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("host", choices=tuple(PUBLICATIONS))
    args = parser.parse_args()
    manifest = json.loads((ROOT / "compatibility/modules.json").read_text())
    print(" ".join(release_tasks(args.host, manifest)))
