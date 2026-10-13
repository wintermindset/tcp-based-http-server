#!/usr/bin/env python3
"""Prepare and validate the versioned release bundle in CI."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET


def main():
    version = ET.parse("pom.xml").getroot().findtext("{http://maven.apache.org/POM/4.0.0}version")
    assert re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", version), version
    commit = os.environ["GITHUB_SHA"]
    assert re.fullmatch(r"[0-9a-f]{40}", commit), commit
    if os.environ.get("GITHUB_REF_TYPE") == "tag":
        assert os.environ["GITHUB_REF_NAME"] == version, "Tag and POM version differ"
    bom = json.loads(Path("target/bom.json").read_text())
    assert bom["bomFormat"] == "CycloneDX"
    assert bom["metadata"]["component"]["version"] == version
    components = bom["components"]
    assert any(c["name"] == "log4j-core" for c in components)
    assert any(c["name"] == "log4j-layout-template-json" for c in components)
    assert not any(c.get("group", "").startswith("org.junit") for c in components)
    output = Path("dist")
    output.mkdir(exist_ok=True)
    assert not list(output.iterdir()), "Release directory must be empty"
    jar = f"tcp-based-http-server-{version}.jar"
    shutil.copyfile(Path("target") / jar, output / jar)
    shutil.copyfile("target/bom.json", output / f"tcp-based-http-server-{version}.sbom.json")
    shutil.copyfile("LICENSE", output / "LICENSE")
    (output / "build-info.json").write_text(json.dumps({"version": version, "commit": commit,
        "workflow_run": os.environ["GITHUB_RUN_ID"]}, indent=2) + "\n")
    checksums = [f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n"
                 for path in sorted(output.iterdir())]
    (output / "SHA256SUMS").write_text("".join(checksums))


if __name__ == "__main__":
    main()
