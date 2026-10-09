#!/bin/sh
set -eu
BASE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR="$BASE/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "$JAR" ]; then
  python3 - "$JAR" <<'PY'
import sys, json, base64, hashlib, urllib.request, pathlib
sha = "eddabd2eef8d94a5437d6168ff9c87a78ff725b3"
url = "https://api.github.com/repos/android/nowinandroid/git/blobs/" + sha
req = urllib.request.Request(url, headers={"User-Agent": "SecurityPatrolBuild", "Accept": "application/vnd.github+json"})
with urllib.request.urlopen(req, timeout=30) as response:
    blob = json.load(response)
if blob["sha"] != sha or blob["encoding"] != "base64":
    raise SystemExit("Unexpected Gradle wrapper blob")
data = base64.b64decode(blob["content"])
if hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest() != sha:
    raise SystemExit("Invalid Gradle wrapper integrity hash")
pathlib.Path(sys.argv[1]).write_bytes(data)
PY
fi
JAVA=java
if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; fi
exec "$JAVA" -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
