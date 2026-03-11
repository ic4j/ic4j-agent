# Playground Test Plan

## Goal

Predeploy the Motoko integration-test canisters to the ICP playground, capture their temporary canister IDs, sync the Java test configuration, and then run the Gradle test suite against those deployed canisters.

## Scope

This workflow applies to these Motoko canisters:

- `src/test/ICTest.mo`
- `src/test/LoanTest.mo`

This workflow updates these Java-side integration test settings:

- `src/test/resources/test.properties`
- `src/test/java/org/ic4j/agent/test/HelloProxy.java`

## Why playground

`dfx` cannot target ICP Ninja directly. For temporary hosted integration testing from the CLI, the ICP playground is the correct target.

Properties of playground:

- Deployable from `dfx`.
- No cycles required.
- Canisters are temporary and expire automatically.
- Good fit for CI-like manual verification before mainnet.

## Repo setup

The repository now includes a minimal `dfx.json` with two Motoko canisters:

- `ictest` -> `src/test/ICTest.mo`
- `loantest` -> `src/test/LoanTest.mo`

For a custom playground or custom named network, add a `networks` section like this to `dfx.json`:

```json
{
	"networks": {
		"myplayground": {
			"playground": {
				"playground_canister": "<playground canister pool ID>",
				"timeout_seconds": 1200
			},
			"providers": ["https://icp-api.io"]
		}
	}
}
```

If you want `dfx deploy --playground` to use your custom playground, name that network `playground` instead of `myplayground`.

This repository's deployment script also supports named networks directly:

```bash
DFX_NETWORK=myplayground DFX_PROVIDER_URL=https://icp-api.io/ ./scripts/deploy_playground_tests.sh
```

## Execution steps

1. Ensure `dfx` is installed and authenticated as needed.
2. If needed, add a custom playground or custom named network to `dfx.json`.
3. Run `scripts/deploy_playground_tests.sh` from the repository root.
4. The script deploys `ictest` and `loantest` to either public playground or the network named by `DFX_NETWORK`.
5. The script captures the generated canister IDs.
6. The script updates `icCanisterId` and `loanCanisterId` in `src/test/resources/test.properties`.
7. The script rewrites `@Canister(...)` and `@EffectiveCanister(...)` in `src/test/java/org/ic4j/agent/test/HelloProxy.java` to the deployed `ictest` ID.
8. Run `gradle test`.

## Expected config after deployment

`test.properties`

- `icUrl=https://icp-api.io/`
- `loanUrl=https://icp-api.io/`
- `icCanisterId=<playground ictest id>`
- `loanCanisterId=<playground loantest id>`

`HelloProxy.java`

- `@Canister("<playground ictest id>")`
- `@EffectiveCanister("<playground ictest id>")`

## Important notes

- Do not overwrite `canisterId` in `test.properties`. That property is used by the mock-server-backed tests, not the playground integration path.
- `HelloProxy` is already invoked with an explicit principal in the test code, so the annotation IDs are mainly kept in sync for consistency and for any annotation-driven defaults.
- Playground canisters are short-lived. Re-run the deployment script before integration test sessions if the old IDs have expired.
- Public playground limits can still block installation. A custom playground network is the supported CLI path when public playground resource limits are too restrictive.

## Recommended workflow

1. Run local mock-backed tests during regular development.
2. Deploy Motoko test canisters to playground.
3. Sync IDs with the script.
4. Run `gradle test`.
5. Re-deploy to playground whenever the temporary canisters expire.
