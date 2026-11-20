"""Guard the MathCat -> Evoleq ToolKit namespace migration."""

import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MODULES = json.loads((ROOT / "compatibility/modules.json").read_text())["modules"]


class ToolkitBrandingTests(unittest.TestCase):
    def test_gradle_module_names_match_manifest(self):
        settings = (ROOT / "settings.gradle.kts").read_text()
        self.assertIn('rootProject.name = "toolkit"', settings)
        declared = set(re.findall(r'include\(":([^"]+)"\)', settings))
        self.assertEqual(declared, set(MODULES))
        for module in MODULES:
            with self.subTest(module=module):
                self.assertTrue(module.startswith("toolkit-"))
                self.assertTrue((ROOT / module / "build.gradle.kts").is_file())
        self.assertFalse(list(ROOT.glob("mathcat-*/build.gradle.kts")))

    def test_source_packages_and_directory_paths(self):
        kotlin_files = list(ROOT.glob("toolkit-*/src/**/*.kt"))
        self.assertTrue(kotlin_files)
        for path in kotlin_files:
            with self.subTest(path=path.relative_to(ROOT)):
                source = path.read_text()
                self.assertNotIn("org.evoleq.mathcat", source)
                declared = re.search(r"(?m)^package\s+([^\s]+)", source)
                self.assertIsNotNone(declared)
                self.assertTrue(declared.group(1).startswith("evoleq.toolkit."), declared.group(1))
                self.assertIn("/evoleq/toolkit/", path.as_posix())

    def test_ksp_service_provider_is_loadable(self):
        provider = "evoleq.toolkit.serialization.ksp.ToolkitSerializersProcessorProvider"
        module = ROOT / "toolkit-serialization-ksp"
        service = module / "src/main/resources/META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider"
        self.assertEqual(service.read_text().strip(), provider)
        kotlin_file = module / "src/main/kotlin/evoleq/toolkit/serialization/ksp/ToolkitSerializersProcessor.kt"
        content = kotlin_file.read_text()
        self.assertIn("class ToolkitSerializersProcessorProvider", content)
        self.assertIn('"toolkit.serializers.package"', content)
        self.assertIn('"toolkit.serializers.name"', content)

    def test_jitpack_repository_group_stays_stable_until_repo_rename(self):
        root_build = (ROOT / "build.gradle.kts").read_text()
        self.assertIn('"com.github.evoleq.mathcat"', root_build)
        self.assertIn("toolkit-api", (ROOT / "publishing-smoke/build.gradle.kts").read_text())
        self.assertIn('REPOSITORY = "evoleq/mathcat"', (ROOT / "scripts/release.py").read_text())

    def test_branding_asset_present(self):
        self.assertTrue((ROOT / "assets/toolkit-original-stokes.png").is_file())
        self.assertIn("assets/toolkit-original-stokes.png", (ROOT / "ReadMe.md").read_text())


if __name__ == "__main__":
    unittest.main()
