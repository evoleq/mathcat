#!/usr/bin/env python3
"""Validate locally published JitPack-style root and JVM Maven artifacts."""
import argparse
import json
from pathlib import Path
from xml.etree import ElementTree

def verify(maven_local: Path, version: str) -> None:
    manifest = json.loads((Path(__file__).resolve().parent.parent /
                           "compatibility/modules.json").read_text())
    modules = {
        name: spec.get("publication", "kmp")
        for name, spec in manifest["modules"].items()
        if spec.get("publication", "kmp") != "none"
    }
    base = maven_local / "com/github/evoleq/mathcat"
    errors = []
    for module in sorted(modules):
        for suffix in (("", "-jvm") if modules[module] == "kmp" else ("",)):
            artifact = module + suffix
            directory = base / artifact / version
            pom = directory / f"{artifact}-{version}.pom"
            if not pom.is_file():
                errors.append(f"Missing publication: {pom}")
                continue
            if suffix == "-jvm" or modules[module] == "jvm":
                jar = directory / f"{artifact}-{version}.jar"
                if not jar.is_file():
                    errors.append(f"Missing JVM jar: {jar}")
            try:
                xml = ElementTree.parse(pom).getroot()
                for dep in xml.iter():
                    if dep.tag.rsplit("}", 1)[-1] != "dependency":
                        continue
                    fields = {n.tag.rsplit("}", 1)[-1]: (n.text or "")
                              for n in dep}
                    artifact_id = fields.get("artifactId", "")
                    internal = (artifact_id[:-4] if artifact_id.endswith("-jvm")
                                else artifact_id)
                    if internal in modules:
                        if fields.get("groupId") != "com.github.evoleq.mathcat":
                            errors.append(f"Wrong internal group in {pom}: {fields}")
                        if fields.get("version") != version:
                            errors.append(f"Wrong internal version in {pom}: {fields}")
            except ElementTree.ParseError as exc:
                errors.append(f"Malformed POM {pom}: {exc}")
    if errors:
        raise SystemExit("\n".join(errors))
    print(f"Verified {len(modules)} root and JVM publications: {version}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("--maven-local", type=Path,
                        default=Path.home() / ".m2/repository")
    args = parser.parse_args()
    verify(args.maven_local, args.version)
