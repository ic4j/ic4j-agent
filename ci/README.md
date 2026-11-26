# IC4J compatibility pilot

This read-only GitHub Actions job does not publish packages or call ICP mainnet.

| Temurin runtime | Unit tests | Local ICP UPDATE + QUERY |
| --- | --- | --- |
| 8 | Candid (including Gson), Agent | Apache HttpClient 5.6.5 / 5.5.2 and OkHttp 4.12.0 / 5.5.0 |
| 11, 21 | Candid (including Gson), Agent | Same four combinations plus Java HTTP Transport |

Gradle 8.14 runs on the lane JDK. Each lane starts with an empty Maven directory. Candid regenerates its JavaCC parsers on clean checkout. Pinned Candid and Transport commits are declared in the workflow. The Gradle init script limits `org.ic4j` resolution to that directory. The smoke checks the bytes of the loaded IC4J JARs, plus the filename of the selected HTTP client JAR. Each transport/version is tested in a separate JVM with a strictly selected client and its own transitive runtime dependencies. This does not change the libraries shipped with Agent. The artifact contains JUnit XML, smoke logs and hashes, not the entire Maven repository.

The fixture is a disposable Motoko canister compiled and deployed by a SHA-256-verified DFX 0.32.0 release. Each smoke writes a distinct value and reads it back. The fixture ID is supplied at run time and the address is fixed to loopback. Local trust-root discovery uses that replica. No real keys or pre-existing canisters are needed.

Before merge GitHub cannot dispatch a new workflow via `workflow_dispatch` because the file does not exist on the default branch. A maintainer may explicitly run this pilot before merge by pushing a commit containing `[run ic4j pilot]` to the dedicated branch `ci/ic4j-java-compatibility-pilot` in `ic4j/ic4j-agent`. Once on the default branch, it can be launched from Actions. No Kafka, Camel, GitHub Packages, Maven Central upload or Java 27 lane in this PR.

The workflow also runs automatically when a PR targets `master`. It skips the
integration suite only when every changed path is Markdown (including both paths
for a rename), and runs the suite when changed-file information is incomplete.
It also runs the suite when the PR head or base no longer matches the event.
The named PR check reports a result in either case. Manual dispatch still
runs the full suite.
