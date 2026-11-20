"""Offline regression tests for Sonatype bundle validation and version isolation."""
import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from central_bundle import BundleError, inspect, make_bundle, required_artifacts

VERSION = "0.3.0"
MANIFEST = {"modules": {
    "toolkit-result": {"publication": "kmp", "requires": {}},
    "toolkit-context": {"publication": "jvm", "requires": {}},
    "toolkit-fixture": {"publication": "none", "requires": {}},
}}

def pom(artifact, dependency_group="org.evoleq.toolkit"):
    return ('''<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion><groupId>org.evoleq.toolkit</groupId>
    <artifactId>''' + artifact + '''</artifactId><version>''' + VERSION + '''</version>
    <name>ToolKit</name><description>Typed library</description>
    <url>https://github.com/evoleq/toolkit</url>
    <licenses><license><name>Test license only</name>
    <url>https://example.org/test-license</url></license></licenses>
    <developers><developer><name>Evoleq contributors</name></developer></developers>
    <scm><url>https://github.com/evoleq/toolkit</url></scm>
    <dependencies><dependency><groupId>''' + dependency_group + '''</groupId>
    <artifactId>toolkit-context</artifactId><version>''' + VERSION + '''</version>
    </dependency></dependencies></project>''')

class CentralBundleTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.staging = self.base / "staging"
        for artifact in required_artifacts(MANIFEST, "linux"):
            directory = self.staging / "org/evoleq/toolkit" / artifact / VERSION
            directory.mkdir(parents=True)
            name = artifact + "-" + VERSION
            for filename, content in (
                (name + ".pom", pom(artifact)),
                (name + "-javadoc.jar", "docs"),
                (name + "-sources.jar", "source"),
                (name + ".jar", "binary"),
            ):
                path = directory / filename
                path.write_text(content)
                (directory / (filename + ".asc")).write_text("test signature")
    def test_signed_linux_publications_validate(self):
        self.assertGreater(len(inspect(self.staging, VERSION, MANIFEST, "linux")), 1)

    def test_absent_signature_fails_closed(self):
        file = next(self.staging.rglob("*.pom.asc"))
        file.unlink()
        with self.assertRaisesRegex(BundleError, "signature"):
            inspect(self.staging, VERSION, MANIFEST, "linux")

    def test_wrong_internal_group_is_rejected(self):
        file = next(self.staging.rglob("*.pom"))
        file.write_text(file.read_text().replace(
            "<groupId>org.evoleq.toolkit</groupId>\n    <artifactId>toolkit-context",
            "<groupId>com.github.evoleq.mathcat</groupId>\n    <artifactId>toolkit-context"))
        with self.assertRaisesRegex(BundleError, "Internal dependency"):
            inspect(self.staging, VERSION, MANIFEST, "linux")

    def test_all_scope_requires_native_host_artifacts(self):
        with self.assertRaisesRegex(BundleError, "Missing artifact publication"):
            inspect(self.staging, VERSION, MANIFEST, "all")

    def test_partial_release_cannot_make_bundle(self):
        with self.assertRaises(BundleError):
            make_bundle(self.staging, VERSION, MANIFEST, self.base / "bundle.zip")

    def test_snapshot_rejected(self):
        with self.assertRaisesRegex(BundleError, "stable numeric SemVer"):
            inspect(self.staging, "0.3.0-SNAPSHOT", MANIFEST, "linux")

    def test_unexpected_artifact_rejected(self):
        (self.staging / "org/evoleq/toolkit/unknown").mkdir()
        with self.assertRaisesRegex(BundleError, "Unexpected staged artifact"):
            inspect(self.staging, VERSION, MANIFEST, "linux")

if __name__ == "__main__":
    unittest.main()
