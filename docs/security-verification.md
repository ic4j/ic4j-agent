# Response verification

Verified queries reject invalid signatures, malformed signatures, invalid public
key encodings, and timestamps outside the allowed window. Query signature
verification remains opt-in through the existing verified-query APIs.

Certificate verification now requires the response certificate's signed `time`
leaf. The maximum age is the agent's configured ingress expiry duration
(300 seconds by default); future timestamps have a 60-second clock tolerance.
Applications must keep their system clocks synchronized. Archived certificates
that previously passed signature-only verification now fail freshness checks.

Delegation certificates retain signature and canister-range checks without
requiring the delegation itself to be recent. Nested subnet delegations are
rejected. Principal range comparisons distinguish byte-array prefixes from
equal principals.

Subnet node-key caches are isolated per agent, expire after the configured
ingress expiry duration, and are cleared when the root key or verification
setting changes. Cache entries also distinguish effective canister IDs.

Malformed CBOR hash trees fail parsing instead of leaving response futures
pending. Hash-tree nesting is limited to 128 levels.

Do not disable verification or fetch an untrusted root key in production.
Existing explicit verification bypass options are intended for controlled test
environments and have not been removed.

## Regression checks

The offline security suite uses ephemeral keys and local fixtures:

```sh
gradle --offline --no-daemon test --tests org.ic4j.agent.AgentSecurityTest
```

The historical certificate fixture in `IdentityTest` now asserts that its
otherwise valid signature is rejected because the certificate is stale.

## Dependency diagnostic follow-up

On 2026-09-05, the editor reported an unnamed medium-severity OSV advisory for
`com.fasterxml.jackson.core:jackson-databind:2.22.2`. An exact package/version
query to the OSV API returned no advisories, and Maven Central listed `2.22.2`
as the latest Jackson 2.x release. Jackson was left unchanged and the warning
was not suppressed. The scanner's advisory ID and affected-version range are
needed to reconcile the discrepancy; this is not a claim that the package is
free of vulnerabilities.