#!/usr/bin/env python3
"""ToolKit repository-version planner and compatibility manifest validator.

Standard-library only. No Git operations here modify the repository.
"""

import argparse
import json
import re
import subprocess
from pathlib import Path

SEMVER = re.compile(r"^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$")
MODULE_NAME = re.compile(r"^[a-z][a-z0-9-]*$")
INCLUDE = re.compile(r'\binclude\s*\(\s*["\']:([^"\']+)["\']\s*\)')
PROJECT_DEP = re.compile(
    r'\b(?:api|implementation|compileOnly|runtimeOnly)\s*\(\s*'
    r'project\s*\(\s*["\']:([^"\']+)["\']\s*\)\s*\)'
)
BUILD_TOOL_DEP = re.compile(
    r'\bksp\s*\(\s*project\s*\(\s*["\']:([^"\']+)["\']\s*\)\s*\)'
)
BREAKING = re.compile(r"(?m)^(?:[a-z][\w-]*(?:\([^)]+\))?!:|BREAKING[ -]CHANGE\s*:)")
FEATURE = re.compile(r"(?m)^feat(?:\([^)]+\))?:")
REPOSITORY = "evoleq/toolkit"


class ReleaseError(ValueError):
    pass


def run_git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    return result.stdout.strip()


def validate(root: Path) -> dict:
    """Validate module declarations against the settings and Gradle dependencies."""
    manifest = json.loads((root / "compatibility/modules.json").read_text())
    if manifest.get("schema") != 1 or manifest.get("versionPolicy") != "repository":
        raise ReleaseError("Unsupported compatibility schema or version policy")
    modules = manifest.get("modules")
    if not isinstance(modules, dict) or not modules:
        raise ReleaseError("modules must be a non-empty object")
    if any(not MODULE_NAME.fullmatch(m) for m in modules):
        raise ReleaseError("Invalid module name")
    settings = (root / "settings.gradle.kts").read_text()
    declared = set(INCLUDE.findall(settings))
    if declared != set(modules):
        raise ReleaseError(f"settings/modules mismatch: settings={sorted(declared)}, manifest={sorted(modules)}")
    for module, definition in modules.items():
        requires = definition.get("requires") if isinstance(definition, dict) else None
        if not isinstance(requires, dict) or any(value != "same-release" for value in requires.values()):
            raise ReleaseError(f"{module}: requires must map module names to 'same-release'")
        publication = definition.get("publication", "kmp")
        if publication not in ("kmp", "jvm", "none"):
            raise ReleaseError(f"{module}: invalid publication kind {publication!r}")
        build_requires = definition.get("buildRequires", {})
        if not isinstance(build_requires, dict) or any(value != "same-release" for value in build_requires.values()):
            raise ReleaseError(f"{module}: buildRequires must map module names to 'same-release'")
        if module in (requires.keys() | build_requires.keys()) or not (requires.keys() | build_requires.keys()) <= modules.keys():
            raise ReleaseError(f"{module}: invalid, unknown or self dependency")
        build = (root / module / "build.gradle.kts").read_text()
        actual = set(PROJECT_DEP.findall(build))
        if actual != set(requires):
            raise ReleaseError(f"{module}: Gradle requires={sorted(actual)}, manifest requires={sorted(requires)}")
        actual_build = set(BUILD_TOOL_DEP.findall(build))
        if actual_build != set(build_requires):
            raise ReleaseError(f"{module}: Gradle buildRequires={sorted(actual_build)}, manifest buildRequires={sorted(build_requires)}")
    visited, visiting = set(), set()

    def visit(module: str) -> None:
        if module in visiting:
            raise ReleaseError(f"Dependency cycle involving {module}")
        if module in visited:
            return
        visiting.add(module)
        for dependency in (modules[module]["requires"].keys() |
                           modules[module].get("buildRequires", {}).keys()):
            visit(dependency)
        visiting.remove(module)
        visited.add(module)

    for module in modules:
        visit(module)
    return manifest


def release_impact(paths: list[str], manifest: dict) -> tuple[list[str], list[str]]:
    """Return changed and transitively affected modules (tests/docs excluded)."""
    modules = manifest["modules"]
    publishable = {name for name, spec in modules.items()
                   if spec.get("publication", "kmp") != "none"}
    global_changes = {"settings.gradle.kts", "build.gradle.kts", "gradle.properties", "compatibility/modules.json"}
    if any(p in global_changes or p.startswith(("gradle/", "build-logic/")) for p in paths):
        return sorted(publishable), sorted(publishable)
    changed = set()
    for path in paths:
        parts = path.split("/")
        if parts[0] not in publishable:
            continue
        if len(parts) == 2 and parts[1] == "build.gradle.kts":
            changed.add(parts[0])
        elif len(parts) >= 4 and parts[1] == "src" and (
            parts[2].endswith("Main") or parts[2] == "main"
        ):
            changed.add(parts[0])
    affected = set(changed)
    while True:
        expanded = affected | {
            name for name, details in modules.items()
            if name in publishable and (
                (set(details["requires"]) | set(details.get("buildRequires", {}))) & affected
            )
        }
        if expanded == affected:
            break
        affected = expanded
    return sorted(changed), sorted(affected)


def next_version(previous: str | None, messages: str) -> tuple[str, str]:
    if previous is None:
        return "v0.1.0", "initial"
    match = SEMVER.fullmatch(previous)
    if not match:
        raise ReleaseError(f"Invalid tag: {previous}")
    major, minor, patch = map(int, match.groups())
    if BREAKING.search(messages):
        if major == 0:
            minor, patch = minor + 1, 0
        else:
            major, minor, patch = major + 1, 0, 0
        cause = "breaking"
    elif FEATURE.search(messages):
        minor, patch = minor + 1, 0
        cause = "feature"
    else:
        patch += 1
        cause = "patch"
    return f"v{major}.{minor}.{patch}", cause


def plan(root: Path) -> dict:
    manifest = validate(root)
    commit = run_git(root, "rev-parse", "HEAD")
    tags = [tag for tag in run_git(root, "tag", "--merged", "HEAD", "--list", "v*").splitlines() if SEMVER.fullmatch(tag)]
    previous = max(tags, key=lambda tag: tuple(map(int, SEMVER.fullmatch(tag).groups()))) if tags else None
    if previous:
        paths = run_git(root, "diff", "--name-only", previous, "HEAD").splitlines()
        messages = run_git(root, "log", "--format=%s%n%b", f"{previous}..HEAD")
    else:
        paths = run_git(root, "ls-files").splitlines()
        messages = run_git(root, "log", "--format=%s%n%b", "HEAD")
    changed, affected = release_impact(paths, manifest)
    release = bool(changed)
    tag, cause = next_version(previous, messages) if release else (None, "no-package-changes")
    return {
        "schema": 1, "repository": REPOSITORY, "commit": commit,
        "previousTag": previous, "release": release,
        "nextTag": tag, "version": tag[1:] if tag else None,
        "reason": cause, "changedModules": changed, "affectedModules": affected,
        "changedPaths": sorted(paths),
    }


def make_record(proposal: dict, manifest: dict, ci_run: str) -> dict:
    if not proposal["release"] or not proposal["nextTag"]:
        raise ReleaseError("Cannot record a skipped release")
    if not ci_run.isdecimal():
        raise ReleaseError("CI run must be a numeric GitHub Actions run ID")
    version = proposal["version"]
    return {
        "schema": 1, "repository": REPOSITORY, "tag": proposal["nextTag"],
        "commit": proposal["commit"], "versionPolicy": "repository",
        "previousTag": proposal["previousTag"],
        "modules": {
            module: {
                "version": version,
                "publication": spec.get("publication", "kmp"),
                "requires": {dep: version for dep in spec["requires"]},
                "buildRequires": {
                    dep: version for dep in spec.get("buildRequires", {})
                },
            } for module, spec in sorted(manifest["modules"].items())
        },
        "verification": {
            "status": "ci-success",
            "workflow": "CI",
            "url": f"https://github.com/{REPOSITORY}/actions/runs/{ci_run}",
            "scope": "Repository CI jobs; not a formal ABI, behavioral or wire-compatibility proof",
        },
    }


def write_json(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("validate", "plan", "record"))
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path)
    parser.add_argument("--plan", type=Path, dest="plan_file")
    parser.add_argument("--ci-run", default="")
    args = parser.parse_args()
    try:
        manifest = validate(args.root)
        if args.command == "validate":
            print("Compatibility manifest verified")
            return
        if args.command == "plan":
            result = plan(args.root)
        else:
            if not args.plan_file:
                parser.error("record requires --plan")
            result = make_record(json.loads(args.plan_file.read_text()), manifest, args.ci_run)
        if args.output:
            write_json(args.output, result)
        print(json.dumps(result, indent=2, sort_keys=True))
    except (ReleaseError, FileNotFoundError, json.JSONDecodeError) as exc:
        parser.exit(1, f"Release validation failed: {exc}\n")


if __name__ == "__main__":
    main()
