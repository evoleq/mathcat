"""Regression checks for the main Gradle version catalog and its aliases.

The actual builds and publication smoke check remain the platform-level tests.
"""

import re
import tomllib
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "gradle/libs.versions.toml"


class VersionCatalogTests(unittest.TestCase):
    def test_catalog_versions_and_references(self):
        catalog = tomllib.loads(CATALOG.read_text())
        versions = catalog["versions"]
        for kind in ("plugins", "libraries"):
            for alias, entry in catalog[kind].items():
                with self.subTest(kind=kind, alias=alias):
                    self.assertIn(entry["version"]["ref"], versions)
                    self.assertIn("id" if kind == "plugins" else "module", entry)
        self.assertEqual(
            catalog["plugins"]["kotlin-jvm"]["version"]["ref"],
            catalog["plugins"]["kotlin-multiplatform"]["version"]["ref"],
        )
        self.assertEqual(
            catalog["plugins"]["kotlin-serialization"]["version"]["ref"],
            catalog["plugins"]["kotlin-multiplatform"]["version"]["ref"],
        )

    def test_module_scripts_use_declared_aliases_and_no_inline_external_versions(self):
        catalog = tomllib.loads(CATALOG.read_text())
        libraries = {name.replace("-", ".") for name in catalog["libraries"]}
        plugins = {name.replace("-", ".") for name in catalog["plugins"]}
        used_libraries, used_plugins = set(), set()

        for build in [ROOT / "build.gradle.kts", *ROOT.glob("toolkit-*/build.gradle.kts")]:
            content = build.read_text()
            with self.subTest(file=build.relative_to(ROOT).as_posix()):
                self.assertNotRegex(
                    content,
                    r'(?m)^\s*(?:api|implementation|testImplementation)\s*\(\s*"[^"]+:[^"]+:[^"]+"',
                )
                self.assertNotRegex(
                    content,
                    r'(?m)^\s*(?:kotlin|id)\s*\([^\n]*\)\s*version\s*"',
                )
                for accessor in re.findall(r'\blibs\.plugins\.([a-zA-Z0-9_.]+)', content):
                    self.assertIn(accessor, plugins)
                    used_plugins.add(accessor)
                for accessor in re.findall(r'\blibs\.(?!plugins\.)([a-zA-Z0-9_.]+)', content):
                    self.assertIn(accessor, libraries)
                    used_libraries.add(accessor)

        self.assertEqual(used_plugins, plugins, "Unused plugin catalog entries")
        self.assertEqual(used_libraries, libraries, "Unused library catalog entries")


if __name__ == "__main__":
    unittest.main()
