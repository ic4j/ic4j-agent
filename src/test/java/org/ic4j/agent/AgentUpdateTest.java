package org.ic4j.agent;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.ic4j.agent.identity.AnonymousIdentity;
import org.ic4j.agent.replicaapi.CallRequestContent;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.types.Principal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class AgentUpdateTest {
	private static final ObjectMapper MAPPER = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());

	@Test
	void submitsTheUpdateContentAndReturnsItsRequestIdAndHeaders() throws Exception {
		CapturingTransport transport = new CapturingTransport();
		Agent agent = new AgentBuilder().transport(transport).identity(new AnonymousIdentity()).build();
		Principal canisterId = Principal.fromString("rrkah-fqaaa-aaaaa-aaaaq-cai");
		Principal effectiveCanisterId = Principal.fromString("aaaaa-aa");
		byte[] argument = new byte[] {0, (byte) 0xff, 7};
		long ingressExpiry = 1_700_000_000_000_000_000L;
		Map<String, String> requestHeaders = Collections.singletonMap("x-request-id", "test-update");

		Response<RequestId> response = agent.updateRaw(canisterId, effectiveCanisterId, "mutate",
				new Request<>(argument, requestHeaders), Optional.of(ingressExpiry)).get(5, TimeUnit.SECONDS);

		Assertions.assertEquals(effectiveCanisterId, transport.canisterId);
		Assertions.assertEquals(requestHeaders, transport.headers);
		Assertions.assertEquals(transport.requestId.toHexString(), response.getPayload().toHexString());
		Assertions.assertEquals(transport.responseHeaders, response.getHeaders());

		JsonNode content = MAPPER.readTree(transport.envelope).path("content");
		Assertions.assertEquals("call", content.path("request_type").asText());
		Assertions.assertEquals("mutate", content.path("method_name").asText());
		Assertions.assertArrayEquals(canisterId.getValue(), content.path("canister_id").binaryValue());
		Assertions.assertArrayEquals(Principal.anonymous().getValue(), content.path("sender").binaryValue());
		Assertions.assertArrayEquals(argument, content.path("arg").binaryValue());
		Assertions.assertEquals(ingressExpiry, content.path("ingress_expiry").longValue());
		Assertions.assertEquals(16, content.path("nonce").binaryValue().length);

		CallRequestContent expectedContent = new CallRequestContent();
		expectedContent.callRequest.methodName = "mutate";
		expectedContent.callRequest.canisterId = canisterId;
		expectedContent.callRequest.sender = Principal.anonymous();
		expectedContent.callRequest.arg = argument;
		expectedContent.callRequest.ingressExpiry = ingressExpiry;
		expectedContent.callRequest.nonce = Optional.of(content.path("nonce").binaryValue());
		Assertions.assertEquals(RequestId.toRequestId(expectedContent).toHexString(), transport.requestId.toHexString());
	}

	private static class CapturingTransport implements ReplicaTransport {
		private final Map<String, String> responseHeaders = new HashMap<>();
		private Principal canisterId;
		private byte[] envelope;
		private Map<String, String> headers;
		private RequestId requestId;

		CapturingTransport() {
			responseHeaders.put("x-ic-node-id", "node-test");
		}

		@Override
		public CompletableFuture<ReplicaResponse> status() {
			throw new UnsupportedOperationException();
		}

		@Override
		public CompletableFuture<ReplicaResponse> query(Principal canisterId, byte[] envelope,
				Map<String, String> headers) {
			throw new UnsupportedOperationException();
		}

		@Override
		public CompletableFuture<ReplicaResponse> call(Principal canisterId, byte[] envelope, RequestId requestId,
				Map<String, String> headers) {
			this.canisterId = canisterId;
			this.envelope = envelope;
			this.requestId = requestId;
			this.headers = headers;
			ReplicaResponse response = new ReplicaResponse();
			response.payload = new byte[0];
			response.headers = responseHeaders;
			return CompletableFuture.completedFuture(response);
		}

		@Override
		public CompletableFuture<ReplicaResponse> readState(Principal canisterId, byte[] envelope,
				Map<String, String> headers) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void close() {
		}
	}
}
