#!/usr/bin/env python3
"""Smoke checks for the packaged JAR or container; run in CI."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request


def check_http(url):
    deadline = time.monotonic() + 30
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url + "/calc?x=3&y=4&op=*", timeout=2) as response:
                assert response.status == 200
                assert response.read() == b"12.0"
                assert response.headers.get("X-Request-Id")
            return
        except (OSError, urllib.error.URLError):
            time.sleep(.2)
    raise AssertionError("Server did not become ready")


def check_logs(path):
    lines = Path(path).read_text().splitlines()
    assert lines, "No application logs"
    events = [json.loads(line) for line in lines if not line.startswith("Picked up JAVA_TOOL_OPTIONS:")]
    assert any(event.get("message") == "Request completed" for event in events)


def jar_smoke(jar):
    Path("target").mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        config = Path(directory) / "server.yaml"
        config.write_text("port: 18080\nhandler: com.wintermindset.handler.CalcHandler\nmanagementPort: 18081\n")
        with open("target/smoke-jar.log", "w") as logs:
            process = subprocess.Popen(["java", "-jar", jar, str(config)], stdout=logs, stderr=subprocess.STDOUT)
            try:
                check_http("http://127.0.0.1:18080")
                with urllib.request.urlopen("http://127.0.0.1:18081/readyz", timeout=2) as response:
                    assert response.read() == b"ready\n"
            finally:
                process.terminate()
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait()
                    raise
        check_logs("target/smoke-jar.log")
        failure = subprocess.run(["java", "-jar", jar, str(Path(directory) / "missing.yaml")],
                                 capture_output=True, timeout=10)
        assert failure.returncode != 0, "Startup failure returned success"


def container_smoke(image):
    name = "tcp-http-smoke"
    subprocess.run(["docker", "run", "--detach", "--name", name, "-p", "127.0.0.1:18080:8080", image], check=True)
    try:
        check_http("http://127.0.0.1:18080")
        subprocess.run(["docker", "stop", "--time", "10", name], check=True, timeout=15)
        state = json.loads(subprocess.check_output(["docker", "inspect", name]))[0]["State"]
        assert not state["Running"] and state["ExitCode"] != 137, state
        Path("target").mkdir(exist_ok=True)
        logs = subprocess.check_output(["docker", "logs", name], stderr=subprocess.STDOUT)
        Path("target/smoke-container.log").write_bytes(logs)
        check_logs("target/smoke-container.log")
    finally:
        subprocess.run(["docker", "rm", "--force", name], check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--jar")
    mode.add_argument("--image")
    args = parser.parse_args()
    if args.jar:
        jar_smoke(args.jar)
    else:
        container_smoke(args.image)
