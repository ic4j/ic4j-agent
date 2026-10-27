package org.ic4j.agent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.ic4j.agent.identity.Signature;
import org.ic4j.agent.replicaapi.Delegation;
import org.ic4j.agent.replicaapi.Envelope;
import org.ic4j.agent.replicaapi.SignedDelegation;
import org.ic4j.types.Principal;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class DelegationCompatibilityTest {
	private final Principal target = Principal.from(new byte[] {1, 2, 3});

	@Test
	void envelopePreservesDelegationExpiryPermissionsAndTargets() throws Exception {
		long expiration = expiryAfterSeconds(60);
		Delegation delegation = new Delegation(expiration, new byte[] {4, 5},
				Collections.singletonList(target), "queries");
		Envelope<Object> envelope = new Envelope<>();
		envelope.senderDelegation = Optional.of(Collections.singletonList(new SignedDelegation(delegation, new byte[] {9})));

		ObjectMapper mapper = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());
		JsonNode decoded = mapper.readTree(mapper.writeValueAsBytes(envelope));
		JsonNode wireDelegation = decoded.path("sender_delegation").get(0).path("delegation");
		assertEquals(expiration, wireDelegation.path("expiration").longValue());
		assertEquals("queries", wireDelegation.path("permissions").textValue());
		assertEquals(target.toString(), Principal.from(wireDelegation.path("targets").get(0).binaryValue()).toString());
	}

	@Test
	void queryOnlyPermissionAllowsQueryAndReadStateButRejectsCall() {
		Signature signature = signature(new Delegation(expiryAfterSeconds(60), new byte[] {4}, null, "queries"));
		assertDoesNotThrow(() -> Agent.validateDelegations(signature, target, "query"));
		assertDoesNotThrow(() -> Agent.validateDelegations(signature, null, "read_state"));
		assertThrows(AgentError.class, () -> Agent.validateDelegations(signature, target, "call"));
	}

	@Test
	void enforcesTargetsExpiryUnknownPermissionsCyclesAndChainLimit() {
		Signature targeted = signature(new Delegation(expiryAfterSeconds(60), new byte[] {4},
				Collections.singletonList(target), null));
		assertDoesNotThrow(() -> Agent.validateDelegations(targeted, target, "query"));
		assertThrows(AgentError.class, () -> Agent.validateDelegations(targeted,
				Principal.from(new byte[] {7, 8}), "query"));

		assertThrows(AgentError.class, () -> Agent.validateDelegations(
				signature(new Delegation(1, new byte[] {4}, null)), target, "query"));
		assertThrows(AgentError.class, () -> Agent.validateDelegations(
				signature(new Delegation(expiryAfterSeconds(60), new byte[] {4}, null, "updates")), target, "query"));
		assertThrows(AgentError.class, () -> Agent.validateDelegations(
				signature(new Delegation(expiryAfterSeconds(60), new byte[] {1, 2}, null)), target, "query"));

		List<SignedDelegation> tooMany = new java.util.ArrayList<>();
		for (int i = 0; i < 21; i++)
			tooMany.add(new SignedDelegation(new Delegation(expiryAfterSeconds(60), new byte[] {(byte) (i + 10)}, null), new byte[] {1}));
		Signature longChain = new Signature(new byte[] {1}, new byte[] {2}, tooMany);
		assertThrows(AgentError.class, () -> Agent.validateDelegations(longChain, target, "query"));
	}

	@Test
	void authorizationChecksDoNotMutateTheCallerProvidedChain() {
		List<SignedDelegation> chain = new java.util.ArrayList<>();
		chain.add(new SignedDelegation(new Delegation(expiryAfterSeconds(60), new byte[] {4}, null), new byte[] {1}));
		org.ic4j.agent.identity.DelegatedIdentity identity = new org.ic4j.agent.identity.DelegatedIdentity(
				new org.ic4j.agent.identity.AnonymousIdentity(), new byte[] {1}, chain);
		Signature signed = identity.sign(new byte[32]);
		assertEquals(1, chain.size());
		assertEquals(1, signed.delegations.get().size());
	}

	private Signature signature(Delegation delegation) {
		return new Signature(new byte[] {1, 2}, new byte[] {3},
				Collections.singletonList(new SignedDelegation(delegation, new byte[] {4})));
	}

	private static long expiryAfterSeconds(long seconds) {
		Instant expiry = Instant.now().plusSeconds(seconds);
		return expiry.getEpochSecond() * 1_000_000_000L + expiry.getNano();
	}
}
