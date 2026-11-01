#!/usr/bin/env bash
# Isolated disposable database. Never loads .env or connects to a shared database.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
export PATH="${JAVA_HOME:?Set JAVA_HOME to JDK 21}/bin:$PATH"
urec_classpath_file="$(mktemp -t urec-classpath)"
trap 'rm -f "$urec_classpath_file"' EXIT
mvn -q test-compile dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile="$urec_classpath_file"
urec_classpath="$(cat "$urec_classpath_file")"
java -cp "target/test-classes:target/classes:$urec_classpath" \
  com.ureclive.urec_live_backend.UrecLiveBackendApplication \
  --spring.profiles.active=test --server.address=127.0.0.1 \
  --logging.level.org.hibernate.SQL=WARN --logging.level.org.springframework=WARN "$@"
