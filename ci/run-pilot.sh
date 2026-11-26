#!/usr/bin/env bash
set -euo pipefail

: "${RUNNER_TEMP:?}" "${GITHUB_WORKSPACE:?}" "${CI_MAVEN_REPO:?}" "${TEST_JAVA:?}"
[[ "$CI_MAVEN_REPO" == "$RUNNER_TEMP/ic4j-maven" ]]
[[ "$TEST_JAVA" == 8 || "$TEST_JAVA" == 11 || "$TEST_JAVA" == 21 ]]
smoke_java="$TEST_JAVA"
smoke_task=run
if [[ -n "${IC4J_SMOKE_JAVA_HOME:-}" ]]; then
  [[ "${1:-}" == smoke && "$TEST_JAVA" == 21 ]]
  smoke_java=27
  smoke_task=runExperimental
  smoke_exe="$IC4J_SMOKE_JAVA_HOME/bin/java"
  [[ -x "$smoke_exe" ]]
fi
base="$(cd "$(dirname "$0")" && pwd)"
reports="$RUNNER_TEMP/ic4j-pilot-reports"
mkdir -p "$reports"

g() {
  gradle --no-daemon --console=plain -Dmaven.repo.local="$CI_MAVEN_REPO" \
    --init-script "$base/local-artifacts.init.gradle" "$@"
}

# Fail if a repository's tests were all skipped, or if the integrated Gson
# cases were not actually executed, regardless of Gradle's task exit code.
check_tests() {
  python3 - "$1" "${2:-}" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

files = list(Path(sys.argv[1]).glob('TEST-*.xml'))
tests = [case for p in files for case in ET.parse(p).getroot().iter('testcase')]
ran = [case for case in tests if case.find('skipped') is None]
if not ran:
    raise SystemExit("No tests executed")
if sys.argv[2] and not any(sys.argv[2] in c.attrib.get('classname', '') for c in ran):
    raise SystemExit("Expected test class did not run: " + sys.argv[2])
print(f"{len(ran)} tests executed; {len(tests)-len(ran)} skipped")
PY
}

case "${1:-}" in
  build)
    mkdir "$CI_MAVEN_REPO"
    java -version 2>&1 | tee "$reports/java-version.txt"
    java_spec="$(java -XshowSettings:properties -version 2>&1 | awk '$1 == "java.specification.version" {print $3}')"
    [[ "${java_spec#1.}" == "$TEST_JAVA" ]]
    gradle --version | tee "$reports/gradle-version.txt"
    for project in candid agent; do
      git -C "$GITHUB_WORKSPACE/$project" rev-parse HEAD
    done | tee "$reports/source-commits.txt"
    g -p "$GITHUB_WORKSPACE/candid" -PrunJavacc=true clean test publishToMavenLocal
    check_tests "$GITHUB_WORKSPACE/candid/build/test-results/test" GsonTest
    g -p "$GITHUB_WORKSPACE/agent" clean test publishToMavenLocal
    check_tests "$GITHUB_WORKSPACE/agent/build/test-results/test"
    if [[ "$TEST_JAVA" != 8 ]]; then
      git -C "$GITHUB_WORKSPACE/transport" rev-parse HEAD >> "$reports/source-commits.txt"
      g -p "$GITHUB_WORKSPACE/transport" clean assemble publishToMavenLocal
    fi
    (cd "$CI_MAVEN_REPO"; find org/ic4j -type f \( -name '*.jar' -o -name '*.pom' \) -print0 |
      sort -z | xargs -0 -r sha256sum) | tee "$reports/ic4j-artifacts.sha256"
    ;;
  smoke)
    if [[ "$smoke_java" == 27 ]]; then
      "$smoke_exe" -version 2>&1 | tee "$reports/smoke-java-version.txt"
      smoke_spec="$("$smoke_exe" -XshowSettings:properties -version 2>&1 | awk '$1 == "java.specification.version" {print $3}')"
      [[ "$smoke_spec" == "$smoke_java" ]]
    fi
    archive="$RUNNER_TEMP/dfx-0.32.0-x86_64-linux.tar.gz"
    curl --fail --silent --show-error --location --retry 3 --max-time 180 \
      'https://github.com/dfinity/sdk/releases/download/0.32.0/dfx-0.32.0-x86_64-linux.tar.gz' -o "$archive"
    echo "218dad11e0519e11c7a310b5c7cb1eadff109bb5db1ea3432f08c870432a80fc  $archive" | sha256sum -c -
    bin="$(mktemp -d "$RUNNER_TEMP/ic4j-dfx.XXXXXX")"
    tar -xzf "$archive" -C "$bin" dfx
    export PATH="$bin:$PATH"
    dfx --version | tee "$reports/dfx-version.txt"
    [[ "$(dfx --version)" == 'dfx 0.32.0' ]]
    export DFX_CONFIG_ROOT
    DFX_CONFIG_ROOT="$(mktemp -d "$RUNNER_TEMP/ic4j-dfx-config.XXXXXX")"
    work="$(mktemp -d "$RUNNER_TEMP/ic4j-fixture.XXXXXX")"
    cp "$base/smoke/canister/"* "$work/"
    cd "$work"
    trap 'dfx stop >> "$reports/dfx.log" 2>&1 || true' EXIT
    dfx start --host 127.0.0.1:4943 --background > "$reports/dfx.log" 2>&1
    curl --fail --silent --show-error --retry 15 --retry-delay 1 --retry-connrefused --max-time 3 \
      http://127.0.0.1:4943/api/v2/status > /dev/null
    dfx deploy --network local --identity anonymous --no-wallet ictest 2>&1 | tee "$reports/deploy.log"
    export IC4J_CI_CANISTER
    IC4J_CI_CANISTER="$(dfx canister --network local id ictest)"
    [[ "$IC4J_CI_CANISTER" =~ ^[a-z0-9]+(-[a-z0-9]+)+$ ]]
    (cd "$CI_MAVEN_REPO"; sha256sum -c "$reports/ic4j-artifacts.sha256")
    run_case() {
      local kind="$1" version="$2" log="$reports/icp-$1-$2.log"
      if ! timeout 300s gradle --no-daemon --console=plain \
        -Dmaven.repo.local="$CI_MAVEN_REPO" --init-script "$base/local-artifacts.init.gradle" \
        -p "$base/smoke" "-PpilotTransport=$kind" "-PpilotClientVersion=$version" \
        "-PpilotJava=$smoke_java" "$smoke_task" 2>&1 | tee "$log"; then
        return 1
      fi
      grep -Fq "PASS: $kind $version UPDATE + QUERY (Java $smoke_java)" "$log"
    }
    failures=0
    run_case apache 5.6.5 || failures=$((failures + 1))
    run_case apache 5.5.2 || failures=$((failures + 1))
    run_case okhttp 4.12.0 || failures=$((failures + 1))
    run_case okhttp 5.5.0 || failures=$((failures + 1))
    if [[ "$smoke_java" != 8 && "$smoke_java" != 27 ]]; then
      run_case java-http 0.8.5 || failures=$((failures + 1))
    fi
    (( failures == 0 ))
    ;;
  *) echo 'Usage: run-pilot.sh build|smoke' >&2; exit 2 ;;
esac
