package org.ic4j.agent;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ic4j.agent.certification.Certificate;
import org.ic4j.agent.certification.Delegation;
import org.ic4j.candid.Leb128;
import org.ic4j.agent.replicaapi.CallReply;
import org.ic4j.agent.replicaapi.NodeSignature;
import org.ic4j.agent.replicaapi.QueryResponse;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.types.Principal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.miracl.core.BLS12381.BLS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public class AgentSecurityTest {
	private final Principal canister = Principal.fromString("rrkah-fqaaa-aaaaa-aaaaq-cai");
	private final RequestId requestId = RequestId.fromHex(new byte[32]);
	private Agent agent;
	private KeyPair keyPair;
	private Subnet subnet;
	private QueryResponse response;
	private NodeSignature signature;

	@BeforeEach
	void prepareQuery() throws Exception {
		Security.addProvider(new BouncyCastleProvider());
		agent = new AgentBuilder().transport(new StubTransport()).build();
		keyPair = KeyPairGenerator.getInstance("Ed25519", "BC").generateKeyPair();
		signature = new NodeSignature();
		signature.identity = Principal.selfAuthenticating(keyPair.getPublic().getEncoded());
		signature.timestamp = nowNanos();
		subnet = new Subnet();
		subnet.nodeKeys = new HashMap<>();
		subnet.nodeKeys.put(signature.identity, keyPair.getPublic().getEncoded());
		agent.subnetCache.put(canister, new Agent.CachedSubnet(subnet, canister, Instant.now().plusSeconds(300)));
		response = new QueryResponse();
		response.status = QueryResponse.InnerStatus.REPLIED_STATUS;
		response.replied = Optional.of(new CallReply(new byte[] {1, 2, 3}));
		response.rejected = Optional.empty();
		response.signatures = Collections.singletonList(signature);
		sign();
	}

	@Test
	void acceptsValidQuerySignature() {
		Assertions.assertDoesNotThrow(() -> agent.verifySignatures(response, canister, requestId));
	}

	@Test
	void rejectsTamperedQueryResponse() {
		response.replied.get().arg[0] ^= 1;
		assertQueryError(AgentError.AgentErrorCode.QUERY_SIGNATURE_VERIFICATION_FAILED);
	}

	@Test
	void rejectsMalformedAndMissingSignatures() {
		signature.signature = new byte[] {1};
		assertQueryError(AgentError.AgentErrorCode.MALFORMED_SIGNATURE);
		signature.signature = null;
		assertQueryError(AgentError.AgentErrorCode.MALFORMED_SIGNATURE);
		response.signatures = Collections.emptyList();
		assertQueryError(AgentError.AgentErrorCode.MISSING_SIGNATURE);
	}

	@Test
	void rejectsInvalidPublicKeyPrefix() {
		subnet.nodeKeys.get(signature.identity)[0] ^= 1;
		assertQueryError(AgentError.AgentErrorCode.DER_PREFIX_MISMATCH);
	}

	@Test
	void rejectsStaleAndFutureSignatures() throws Exception {
		signature.timestamp = nowNanos() - 600_000_000_000L;
		sign();
		assertQueryError(AgentError.AgentErrorCode.CERTIFICATE_OUTDATED);
		signature.timestamp = nowNanos() + 120_000_000_000L;
		sign();
		assertQueryError(AgentError.AgentErrorCode.CERTIFICATE_OUTDATED);
	}

	@Test
	void isolatesCachesBetweenAgents() throws Exception {
		Agent other = new AgentBuilder().transport(new StubTransport()).build();
		Assertions.assertTrue(other.subnetCache.isEmpty());
		Assertions.assertSame(subnet, agent.getSubnet(canister, canister));
	}

	@Test
	void invalidatesCacheWhenTrustChanges() {
		agent.setVerify(false);
		Assertions.assertTrue(agent.subnetCache.isEmpty());
		agent.subnetCache.put(canister, new Agent.CachedSubnet(subnet, canister, Instant.now().plusSeconds(300)));
		agent.setVerify(true);
		Assertions.assertTrue(agent.subnetCache.isEmpty());
		agent.subnetCache.put(canister, new Agent.CachedSubnet(subnet, canister, Instant.now().plusSeconds(300)));
		byte[] rootKey = new byte[] {1, 2, 3};
		agent.setRootKey(rootKey);
		rootKey[0] = 9;
		Assertions.assertEquals(1, agent.getRootKey()[0]);
		Assertions.assertTrue(agent.subnetCache.isEmpty());
	}

	@Test
	void expiredCacheRequiresNewCertificate() {
		agent.subnetCache.put(canister, new Agent.CachedSubnet(subnet, canister, Instant.EPOCH));
		Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () ->
				Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
						() -> agent.getSubnet(canister, canister)));
		Assertions.assertEquals(1, ((StubTransport) agent.transport).reads);
		Assertions.assertTrue(agent.subnetCache.isEmpty());
	}

	@Test
	void verifiesFreshCertificateAndRejectsStaleOrFutureTime() throws Exception {
		byte[][] keys = blsKeys();
		agent.setRootKey(keys[1]);
		Certificate fresh = signedCertificate(label("time", leaf(timestamp(nowNanos()))), keys[0]);
		Assertions.assertDoesNotThrow(() -> agent.verify(fresh, canister, false));
		for(long time : new long[] {0, nowNanos() - 600_000_000_000L, nowNanos() + 120_000_000_000L}) {
			Certificate invalid = signedCertificate(label("time", leaf(timestamp(time))), keys[0]);
			AgentError error = Assertions.assertThrows(AgentError.class, () -> agent.verify(invalid, canister, false));
			Assertions.assertEquals(AgentError.AgentErrorCode.CERTIFICATE_OUTDATED, error.getCode());
		}
	}

	@Test
	void rejectsMissingAndMalformedCertifiedTime() throws Exception {
		byte[][] keys = blsKeys();
		agent.setRootKey(keys[1]);
		Certificate missing = signedCertificate(Collections.singletonList(0), keys[0]);
		Assertions.assertThrows(AgentError.class, () -> agent.verify(missing, canister, false));
		for(byte[] time : new byte[][] {new byte[0], {(byte) 128}, {0, 0},
				{(byte) 128, (byte) 128, (byte) 128, (byte) 128, (byte) 128,
				 (byte) 128, (byte) 128, (byte) 128, (byte) 128, 0}}) {
			Certificate invalid = signedCertificate(label("time", leaf(time)), keys[0]);
			AgentError error = Assertions.assertThrows(AgentError.class, () -> agent.verify(invalid, canister, false));
			Assertions.assertEquals(AgentError.AgentErrorCode.INVALID_CBOR_DATA, error.getCode());
		}
	}

	@Test
	void acceptsFreshCertificateWithOlderAuthorizedDelegation() throws Exception {
		byte[][] root = blsKeys();
		byte[][] delegated = blsKeys();
		agent.setRootKey(root[1]);
		byte[] subnetId = new byte[] {1};
		Object ranges = label("canister_ranges", leaf(mapper().writeValueAsBytes(
				Collections.singletonList(Arrays.asList(canister.getValue(), canister.getValue())))));
		Object subnetTree = Arrays.asList(1, ranges, label("public_key", leaf(delegated[1])));
		Object tree = Arrays.asList(1, label("subnet", Arrays.asList(2, subnetId, subnetTree)), label("time", leaf(timestamp(0))));
		Certificate parent = signedCertificate(tree, root[0]);
		Certificate child = signedCertificate(label("time", leaf(timestamp(nowNanos()))), delegated[0]);
		Delegation delegation = new Delegation();
		delegation.subnetId = subnetId;
		Map<String, Object> parentWire = new HashMap<>();
		parentWire.put("tree", tree);
		parentWire.put("signature", parent.signature);
		delegation.certificate = mapper().writeValueAsBytes(parentWire);
		child.delegation = Optional.of(delegation);
		Assertions.assertDoesNotThrow(() -> agent.verify(child, canister, false));
		Assertions.assertThrows(AgentError.class,
				() -> agent.verify(child, Principal.fromString("aaaaa-aa"), false));
		child.signature[0] ^= 1;
		Assertions.assertThrows(AgentError.class, () -> agent.verify(child, canister, false));
	}

	@Test
	void malformedCertificatesCompleteExceptionally() throws Exception {
		Object nested = Collections.singletonList(0);
		for(int depth = 0; depth < 130; depth++)
			nested = label("nested", nested);
		for(Object tree : Arrays.asList(Collections.singletonList(99), Collections.emptyList(),
				Arrays.asList(1, Collections.singletonList(0)), Arrays.asList(4, new byte[1]),
				Arrays.asList(3, "not bytes"), Collections.singletonList("0"), nested)) {
			Map<String, Object> certificate = new HashMap<>();
			certificate.put("tree", tree);
			certificate.put("signature", new byte[48]);
			StubTransport transport = new StubTransport();
			transport.payload = mapper().writeValueAsBytes(Collections.singletonMap("certificate",
					mapper().writeValueAsBytes(certificate)));
			Agent malformedAgent = new AgentBuilder().transport(transport).build();
			CompletableFuture<Agent.CertificateResponse> future = malformedAgent.readStateRaw(canister, Collections.emptyList(), null);
			java.util.concurrent.ExecutionException error = Assertions.assertThrows(java.util.concurrent.ExecutionException.class,
					() -> future.get(2, java.util.concurrent.TimeUnit.SECONDS));
			Assertions.assertTrue(error.getCause() instanceof AgentError);
		}
	}

	private static ObjectMapper mapper() {
		return new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());
	}

	private static Object label(String name, Object tree) {
		return Arrays.asList(2, name.getBytes(StandardCharsets.UTF_8), tree);
	}

	private static Object leaf(byte[] value) {
		return Arrays.asList(3, value);
	}

	private static byte[] timestamp(long value) {
		if(value == 0) return new byte[] {0};
		ByteBuffer buffer = ByteBuffer.allocate(10);
		Leb128.writeUnsigned(buffer, value);
		return Arrays.copyOf(buffer.array(), buffer.position());
	}

	private static byte[][] blsKeys() {
		byte[] secret = new byte[BLS.BGS];
		byte[] key = new byte[96];
		byte[] seed = new byte[32];
		new java.security.SecureRandom().nextBytes(seed);
		Assertions.assertEquals(0, BLS.KeyPairGenerate(seed, secret, key));
		byte[] der = org.apache.commons.lang3.ArrayUtils.addAll(ResponseAuthentication.DER_PREFIX, key);
		return new byte[][] {secret, der};
	}

	private static Certificate signedCertificate(Object tree, byte[] secret) throws Exception {
		Map<String, Object> encoded = new HashMap<>();
		encoded.put("tree", tree);
		encoded.put("signature", new byte[48]);
		Certificate certificate = mapper().readValue(mapper().writeValueAsBytes(encoded), Certificate.class);
		byte[] message = org.apache.commons.lang3.ArrayUtils.addAll(Agent.IC_STATE_ROOT_DOMAIN_SEPARATOR, certificate.tree.digest());
		Assertions.assertEquals(0, BLS.core_sign(certificate.signature, message, secret));
		return certificate;
	}

	private void assertQueryError(AgentError.AgentErrorCode code) {
		AgentError error = Assertions.assertThrows(AgentError.class,
				() -> agent.verifySignatures(response, canister, requestId));
		Assertions.assertEquals(code, error.getCode());
	}

	private void sign() throws Exception {
		java.security.Signature signer = java.security.Signature.getInstance("Ed25519", "BC");
		signer.initSign(keyPair.getPrivate());
		signer.update(response.signable(requestId, signature.timestamp));
		signature.signature = signer.sign();
	}

	private static long nowNanos() {
		Instant now = Instant.now();
		return now.getEpochSecond() * 1_000_000_000L + now.getNano();
	}

	private static class StubTransport implements ReplicaTransport {
		byte[] payload;
		int reads;

		private CompletableFuture<ReplicaResponse> result() {
			if(payload == null) {
				CompletableFuture<ReplicaResponse> failed = new CompletableFuture<>();
				failed.completeExceptionally(new java.io.IOException("No stub response configured"));
				return failed;
			}
			ReplicaResponse result = new ReplicaResponse();
			result.payload = payload;
			result.headers = Collections.emptyMap();
			return CompletableFuture.completedFuture(result);
		}

		public CompletableFuture<ReplicaResponse> status() { return result(); }
		public CompletableFuture<ReplicaResponse> query(Principal canister, byte[] envelope, Map<String, String> headers) { return result(); }
		public CompletableFuture<ReplicaResponse> call(Principal canister, byte[] envelope, RequestId requestId, Map<String, String> headers) { return result(); }
		public CompletableFuture<ReplicaResponse> readState(Principal canister, byte[] envelope, Map<String, String> headers) {
			reads++;
			return result();
		}
		public void close() {}
	}
}