#!/usr/bin/env python3
"""Validate signed Maven Central staging and assemble a Sonatype Portal bundle.

The bundle is an OFFLINE artifact. Upload is an explicit, separately authorized
workflow step; this program never sends data to a remote service.
"""
import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path
from xml.etree import ElementTree

from central_publication_tasks import PUBLICATIONS

GROUP = "org.evoleq.toolkit"
GROUP_PATH = Path("org/evoleq/toolkit")
SEMVER = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\Z")
SUFFIX = {
    "kotlinMultiplatform": "", "jvm": "-jvm", "js": "-js",
    "wasmJs": "-wasm-js", "android": "-android",
    "linuxX64": "-linuxx64", "iosX64": "-iosx64",
    "iosArm64": "-iosarm64", "iosSimulatorArm64": "-iossimulatorarm64",
    "macosX64": "-macosx64", "macosArm64": "-macosarm64",
    "mingwX64": "-mingwx64",
}
PUBLISHABLE = ("kmp", "jvm")
PAYLOAD_EXT = {".pom", ".module", ".jar", ".aar", ".klib", ".zip"}


class BundleError(ValueError):
    pass


def required_artifacts(manifest: dict, scope: str) -> list[str]:
    targets = PUBLICATIONS["linux"] if scope == "linux" else tuple(
        target for host in ("linux", "macos", "windows") for target in PUBLICATIONS[host]
    )
    result = []
    for module, spec in sorted(manifest["modules"].items()):
        kind = spec["publication"]
        if kind == "none":
            continue
        if kind not in PUBLISHABLE:
            raise BundleError("Unexpected publication kind: " + str(kind))
        if kind == "jvm":
            result.append(module)
        else:
            result.extend(module + SUFFIX[target] for target in targets)
    return result


def required_text(xml: ElementTree.Element, path: str, pom: Path) -> str:
    # Gradle Maven POMs use a default namespace; {*} handles both forms.
    value = xml.findtext(path)
    if not value or not value.strip():
        raise BundleError("Missing required POM field " + path + ": " + str(pom))
    return value.strip()


def inspect_pom(pom: Path, artifact: str, version: str, modules: set[str]) -> None:
    try:
        xml = ElementTree.parse(pom).getroot()
    except ElementTree.ParseError as exc:
        raise BundleError("Malformed POM " + str(pom)) from exc
    for field, expected in (
        ("{*}groupId", GROUP),
        ("{*}artifactId", artifact),
        ("{*}version", version),
    ):
        actual = required_text(xml, field, pom)
        if actual != expected:
            raise BundleError("Wrong POM " + field + " in " + str(pom) + ": " + actual)
    for path in (
        "{*}name", "{*}description", "{*}url",
        "{*}licenses/{*}license/{*}name",
        "{*}licenses/{*}license/{*}url",
        "{*}developers/{*}developer/{*}name",
        "{*}scm/{*}url",
    ):
        required_text(xml, path, pom)
    if not required_text(xml, "{*}licenses/{*}license/{*}url", pom).startswith("https://"):
        raise BundleError("License URL must use HTTPS: " + str(pom))
    for dependency in xml.findall(".//{*}dependency"):
        dep_artifact = dependency.findtext("{*}artifactId") or ""
        internal = any(dep_artifact == module or dep_artifact.startswith(module + "-")
                       for module in modules)
        if internal:
            if dependency.findtext("{*}groupId") != GROUP:
                raise BundleError("Internal dependency has wrong group: " + str(pom))
            if dependency.findtext("{*}version") != version:
                raise BundleError("Internal dependency has wrong version: " + str(pom))


def inspect(staging: Path, version: str, manifest: dict, scope: str) -> list[Path]:
    if not SEMVER.fullmatch(version):
        raise BundleError("Maven Central requires stable numeric SemVer without v")
    base = staging / GROUP_PATH
    if not base.is_dir():
        raise BundleError("Missing central group staging directory: " + str(base))
    expected = required_artifacts(manifest, scope)
    modules = set(manifest["modules"])
    payloads = []
    errors = []
    for artifact in expected:
        directory = base / artifact / version
        pom = directory / (artifact + "-" + version + ".pom")
        if not pom.is_file():
            errors.append("Missing artifact publication: " + str(pom))
            continue
        inspect_pom(pom, artifact, version, modules)
        jar_doc = directory / (artifact + "-" + version + "-javadoc.jar")
        if not jar_doc.is_file():
            errors.append("Missing Javadoc archive: " + str(jar_doc))
        # Source archives are mandatory for Maven JVM JARs and KMP roots.
        primary_jar = directory / (artifact + "-" + version + ".jar")
        if primary_jar.is_file():
            src = directory / (artifact + "-" + version + "-sources.jar")
            if not src.is_file():
                errors.append("Missing source archive: " + str(src))
        for child in sorted(directory.iterdir()):
            if child.is_symlink():
                errors.append("Symlink not permitted in release: " + str(child))
            if child.is_file() and child.suffix in PAYLOAD_EXT:
                signature = child.with_name(child.name + ".asc")
                if not signature.is_file() or signature.stat().st_size == 0:
                    errors.append("Missing detached PGP signature: " + str(signature))
                payloads.append(child)
    if errors:
        raise BundleError("\n".join(errors))
    # Reject a stale or accidental second version/artifact in the staging area.
    expected_set = set(expected)
    for artifact_dir in base.iterdir():
        if not artifact_dir.is_dir() or artifact_dir.name not in expected_set:
            raise BundleError("Unexpected staged artifact: " + str(artifact_dir))
        for version_dir in artifact_dir.iterdir():
            if version_dir.is_dir() and version_dir.name != version:
                raise BundleError("Mixed release versions: " + str(version_dir))
    return payloads


def verify_pgp(payloads: list[Path]) -> None:
    """Cryptographically verify every detached signature using a trusted keyring."""
    for payload in payloads:
        signature = payload.with_name(payload.name + ".asc")
        result = subprocess.run(
            ["gpg", "--batch", "--no-tty", "--verify", str(signature), str(payload)],
            text=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
            check=False,
        )
        if result.returncode != 0:
            raise BundleError("Invalid PGP signature for " + str(payload) +
                              ": " + result.stderr.strip())


def make_bundle(staging: Path, version: str, manifest: dict, output: Path) -> int:
    payloads = inspect(staging, version, manifest, "all")
    for item in payloads:
        data = item.read_bytes()
        for extension, algorithm in (("md5", "md5"), ("sha1", "sha1")):
            digest = hashlib.new(algorithm, data).hexdigest()
            item.with_name(item.name + "." + extension).write_text(digest + "\n")
    # Zip only versioned artifact files; never maven-metadata.xml, build caches,
    # credentials, or unrelated staging roots.
    output.parent.mkdir(parents=True, exist_ok=True)
    allowed = required_artifacts(manifest, "all")
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for artifact in sorted(allowed):
            directory = staging / GROUP_PATH / artifact / version
            for file in sorted(directory.iterdir()):
                if file.is_file():
                    archive.write(file, file.relative_to(staging).as_posix())
    return len(payloads)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["verify", "bundle"])
    parser.add_argument("--staging", type=Path, default=Path("build/central-staging"))
    parser.add_argument("--version", required=True)
    parser.add_argument("--scope", choices=["linux", "all"], default="all")
    parser.add_argument("--output", type=Path, default=Path("build/release/central-bundle.zip"))
    parser.add_argument("--verify-pgp", action="store_true",
                        help="Verify detached signatures with GnuPG before proceeding")
    parser.add_argument("--manifest", type=Path, default=Path("compatibility/modules.json"))
    args = parser.parse_args()
    try:
        manifest = json.loads(args.manifest.read_text())
        if args.verify_pgp:
            verify_pgp(inspect(args.staging, args.version, manifest, args.scope))
        if args.command == "bundle":
            if args.scope != "all":
                raise BundleError("A partial host release must never become an uploadable bundle")
            count = make_bundle(args.staging, args.version, manifest, args.output)
            print("Created fully signed Central bundle with", count, "payloads:", args.output)
        else:
            count = len(inspect(args.staging, args.version, manifest, args.scope))
            print("Verified", count, "signed payloads for", args.scope, args.version)
    except (BundleError, OSError, json.JSONDecodeError) as exc:
        parser.exit(1, "Central staging validation failed: " + str(exc) + "\n")

if __name__ == "__main__":
    main()
