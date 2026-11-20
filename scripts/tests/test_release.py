"""Hermetic regression tests for the release planner."""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from release import ReleaseError, make_record, next_version, plan, release_impact, validate

MANIFEST = {
    "schema": 1, "versionPolicy": "repository", "modules": {
        "toolkit-core": {"requires": {}},
        "toolkit-store": {"requires": {"toolkit-core": "same-release"}},
        "toolkit-actions": {"requires": {"toolkit-core": "same-release", "toolkit-store": "same-release"}},
    },
}


def git(root, *args):
    return subprocess.run(["git", "-C", str(root), *args], check=True,
                          capture_output=True, text=True).stdout.strip()


class ReleaseTests(unittest.TestCase):
    def test_semver(self):
        examples = [
            (None, "", ("v0.1.0", "initial")),
            ("v0.1.0", "fix(core): fix", ("v0.1.1", "patch")),
            ("v0.1.0", "feat(core): add", ("v0.2.0", "feature")),
            ("v0.1.2", "feat!: break", ("v0.2.0", "breaking")),
            ("v1.2.3", "fix(core): fix\n\nBREAKING CHANGE: contract", ("v2.0.0", "breaking")),
            ("v1.2.3", "docs: update", ("v1.2.4", "patch")),
        ]
        for prev, message, expected in examples:
            with self.subTest(prev=prev, message=message):
                self.assertEqual(next_version(prev, message), expected)

    def test_scope_and_reverse_dependencies(self):
        self.assertEqual(release_impact(["toolkit-core/src/commonMain/kotlin/A.kt"], MANIFEST),
                         (["toolkit-core"], ["toolkit-actions", "toolkit-core", "toolkit-store"]))
        self.assertEqual(release_impact(["docs/example.md", "toolkit-core/src/commonTest/kotlin/A.kt"], MANIFEST),
                         ([], []))
        self.assertEqual(release_impact(["gradle.properties"], MANIFEST),
                         (sorted(MANIFEST["modules"]), sorted(MANIFEST["modules"])))

    def test_manifest_detects_graph_drift_and_cycles(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "compatibility").mkdir()
            (root / "settings.gradle.kts").write_text('\n'.join(f'include(":{m}")' for m in MANIFEST["modules"]))
            for module, info in MANIFEST["modules"].items():
                (root / module).mkdir()
                (root / module / "build.gradle.kts").write_text('\n'.join(
                    f'api(project(":{dep}"))' for dep in info["requires"]))
            path = root / "compatibility/modules.json"
            path.write_text(json.dumps(MANIFEST))
            validate(root)
            (root / "toolkit-store/build.gradle.kts").write_text("")
            with self.assertRaisesRegex(ReleaseError, "Gradle requires"):
                validate(root)
            (root / "toolkit-store/build.gradle.kts").write_text('api(project(":toolkit-core"))')
            cyclic = json.loads(path.read_text())
            cyclic["modules"]["toolkit-core"]["requires"] = {"toolkit-store": "same-release"}
            (root / "toolkit-core/build.gradle.kts").write_text('api(project(":toolkit-store"))')
            path.write_text(json.dumps(cyclic))
            with self.assertRaisesRegex(ReleaseError, "cycle"):
                validate(root)

    def test_jvm_changes_and_test_only_fixtures(self):
        manifest = json.loads(json.dumps(MANIFEST))
        manifest["modules"]["toolkit-transform"] = {
            "requires": {"toolkit-core": "same-release"}, "publication": "jvm",
        }
        manifest["modules"]["toolkit-build-fixture"] = {
            "requires": {"toolkit-transform": "same-release"}, "publication": "none",
        }
        self.assertEqual(
            release_impact(["toolkit-transform/src/main/kotlin/Transform.kt"], manifest),
            (["toolkit-transform"], ["toolkit-transform"]),
        )
        self.assertEqual(
            release_impact(["toolkit-build-fixture/src/main/kotlin/Fixture.kt"], manifest),
            ([], []),
        )
        self.assertEqual(
            release_impact(["toolkit-core/src/commonMain/kotlin/A.kt"], manifest),
            (["toolkit-core"],
             ["toolkit-actions", "toolkit-core", "toolkit-store", "toolkit-transform"]),
        )

    def test_build_tool_dependency_and_publication_policy(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "compatibility").mkdir()
            manifest = json.loads(json.dumps(MANIFEST))
            manifest["modules"]["toolkit-ksp"] = {
                "requires": {}, "publication": "jvm",
            }
            manifest["modules"]["toolkit-fixture"] = {
                "requires": {}, "buildRequires": {"toolkit-ksp": "same-release"},
                "publication": "none",
            }
            (root / "settings.gradle.kts").write_text(
                "\n".join(f'include(":{m}")' for m in manifest["modules"])
            )
            for module, info in manifest["modules"].items():
                (root / module).mkdir()
                regular = [f'api(project(":{dep}"))' for dep in info["requires"]]
                build = [f'ksp(project(":{dep}"))'
                         for dep in info.get("buildRequires", {})]
                (root / module / "build.gradle.kts").write_text(
                    "\n".join(regular + build)
                )
            path = root / "compatibility/modules.json"
            path.write_text(json.dumps(manifest))
            validate(root)
            (root / "toolkit-fixture/build.gradle.kts").write_text("")
            with self.assertRaisesRegex(ReleaseError, "buildRequires"):
                validate(root)

    def test_plan_with_real_git_history(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            git(root, "init", "-q")
            git(root, "config", "user.name", "Tests")
            git(root, "config", "user.email", "tests@example.invalid")
            (root / "compatibility").mkdir()
            (root / "compatibility/modules.json").write_text(json.dumps(MANIFEST))
            (root / "settings.gradle.kts").write_text('\n'.join(f'include(":{m}")' for m in MANIFEST["modules"]))
            for module, spec in MANIFEST["modules"].items():
                (root / module / "src/commonMain/kotlin").mkdir(parents=True)
                (root / module / "build.gradle.kts").write_text('\n'.join(
                    f'api(project(":{dep}"))' for dep in spec["requires"]))
            git(root, "add", ".")
            git(root, "commit", "-qm", "feat: initial")
            initial = plan(root)
            self.assertEqual(initial["nextTag"], "v0.1.0")
            git(root, "tag", "v0.1.0")
            self.assertFalse(plan(root)["release"])
            (root / "README.md").write_text("Documentation")
            git(root, "add", ".")
            git(root, "commit", "-qm", "docs: add README")
            self.assertFalse(plan(root)["release"])
            (root / "toolkit-core/src/commonMain/kotlin/Hello.kt").write_text("class Hello")
            git(root, "add", ".")
            git(root, "commit", "-qm", "feat(core): new API")
            proposal = plan(root)
            self.assertEqual(proposal["nextTag"], "v0.2.0")
            self.assertEqual(proposal["affectedModules"], ["toolkit-actions", "toolkit-core", "toolkit-store"])
            record = make_record(proposal, MANIFEST, "123")
            self.assertEqual(record["modules"]["toolkit-store"]["requires"], {"toolkit-core": "0.2.0"})
            self.assertEqual(record["verification"]["status"], "ci-success")
            self.assertEqual(record["commit"], proposal["commit"])
            with self.assertRaisesRegex(ReleaseError, "numeric"):
                make_record(proposal, MANIFEST, "invalid")


if __name__ == "__main__":
    unittest.main()
