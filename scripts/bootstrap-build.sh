#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TC="${HOME}/.hermes/cache/toolchains"
mkdir -p "$TC"
fetch_verify() { url=$1 file=$2 sum=$3 algo=$4; if [ ! -f "$file" ]; then curl -fL --retry 3 "$url" -o "$file"; fi; printf '%s  %s\n' "$sum" "$file" | "${algo}sum" -c -; }
fetch_verify https://aka.ms/download-jdk/microsoft-jdk-17-linux-x64.tar.gz "$TC/jdk17.tar.gz" f7230e6d76ccfd62ceb11687a24e93d1f63d236e6a5fdeb8d923700075de42f8 sha256
[ -x "$TC/jdk17/bin/javac" ] || { mkdir -p "$TC/jdk17"; tar -xzf "$TC/jdk17.tar.gz" -C "$TC/jdk17" --strip-components=1; }
fetch_verify https://archive.apache.org/dist/ant/binaries/apache-ant-1.10.15-bin.tar.gz "$TC/ant.tar.gz" d78427aff207592c024ff1552dc04f7b57065a195c42d398fcffe7a0145e8d00cd46786f5aa52e77ab0fdf81334f065eb8011eecd2b48f7228e97ff4cb20d16c sha512
[ -x "$TC/apache-ant-1.10.15/bin/ant" ] || tar -xzf "$TC/ant.tar.gz" -C "$TC"
fetch_verify https://josm.openstreetmap.de/download/josm-snapshot-19678.jar "$TC/josm-19678.jar" 9bae2b2b105002e4a948441a7e8a5fa4c184f7cfddd031d9a64d18a8d6d645fd sha256
fetch_verify https://repo.maven.apache.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar "$TC/junit-console.jar" b016ef6b1c3454d6d7c2c88ce081dabf289699686af6622d6e4e2e1b54b4a2fc sha256
PATH="$TC/jdk17/bin:$TC/apache-ant-1.10.15/bin:$PATH" ant -f "$ROOT/build.xml" "$@"
