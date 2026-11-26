# IC4J compatibility pilot

This read-only GitHub Actions job does not publish packages or call ICP mainnet.

| Lane | Build and unit coverage | Local ICP coverage |
| --- | --- | --- |
| Temurin 8 | Candid (asserts Gson tests ran), then Agent's own test suite | Not in this pilot lane |
| Temurin 11 | Candid, Agent's own test suite, then Java HTTP transport | Agent runs with Apache HttpClient 5.6.5 and 5.5.2; Java HTTP Transport runs separately once. Each asserts an UPDATE and reads that exact value through QUERY |

Gradle 8.14 runs on the lane JDK. Each lane starts with its own empty Maven directory. Candid regenerates its JavaCC parsers on clean checkout. Pinned Candid and Transport commits are declared in the workflow. The Gradle init script limits `org.ic4j` resolution to that directory; the smoke consumer independently checks the actual loaded JAR bytes against the ones produced in the job. The smoke uses the same Agent JAR for both Apache versions, strictly resolves each version with its own transitive dependencies, verifies the loaded HttpClient JAR and logs the actual HttpCore JAR. The 5.5.2 test is a compatibility check, not a change to the shipped dependency. A regression or missing test fails the lane. Verify results in the uploaded JUnit XML and smoke log. The artifact contains reports and hashes, not the entire Maven repository.

The fixture is a disposable Motoko canister compiled and deployed by a SHA-256-verified DFX 0.32.0 release. The smoke client only accepts the fixture ID and has a fixed loopback endpoint. Local trust-root discovery uses that replica. It uses a fresh state/configuration on the hosted runner; existing project canisters and real keys are not needed.

Before merge GitHub cannot dispatch a new workflow via `workflow_dispatch` because the file does not exist on the default branch. A maintainer may explicitly run this pilot before merge by pushing a commit containing `[run ic4j pilot]` to the dedicated branch `ci/ic4j-java-compatibility-pilot` in `ic4j/ic4j-agent`. No fork/PR-wide trigger is enabled. Once the workflow is on the default branch, it can be launched from Actions. No Kafka, Camel, GitHub Packages, Maven Central upload, or Java 27 lane in this first PR.
