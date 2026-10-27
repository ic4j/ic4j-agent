package org.ic4j.agent.requestid;

import java.util.Arrays;
import java.util.List;

import org.ic4j.agent.Hex;
import org.ic4j.agent.Serialize;
import org.ic4j.agent.Serializer;
import org.ic4j.agent.replicaapi.CallRequestContent;
import org.ic4j.agent.replicaapi.Delegation;
import org.ic4j.types.Principal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RequestIdTest {
	@Test
	void hashesFieldsUsingProtocolEncodingAndUnsignedKeyOrdering() {
		RequestId id = RequestId.toRequestId(new TestValue(false));

		Assertions.assertEquals("54f199e8d410e8fa622d8c0862df733400810e4d7b159cbcbf043f27d602957e",
				id.toHexString());
		Assertions.assertEquals(id.toHexString(), RequestId.toRequestId(new TestValue(true)).toHexString());
	}

	@Test
	void hashesListsPrincipalsAndNestedSerializableValues() {
		RequestId id = RequestId.toRequestId(new CompositeValue());

		Assertions.assertEquals("34484cb450b81816ea5740bf1485fa1f12fd1db18eaa682922fb343c30c81d1d",
				id.toHexString());
	}

	@Test
	void matchesOfficialHttpsInterfaceRequestIdVector() {
		CallRequestContent request = new CallRequestContent();
		request.callRequest.nonce = java.util.Optional.empty();
		request.callRequest.ingressExpiry = 1685570400000000000L;
		request.callRequest.sender = Principal.from(new byte[] {4});
		request.callRequest.canisterId = Principal.from(new byte[] {0, 0, 0, 0, 0, 0, 4, (byte) 0xd2});
		request.callRequest.methodName = "hello";
		request.callRequest.arg = new byte[] {'D', 'I', 'D', 'L', 0, (byte) 0xfd, 42};

		Assertions.assertEquals("1d1091364d6bb8a6c16b203ee75467d59ead468f523eb058880ae8ec80e2b101",
				RequestId.toRequestId(request).toHexString());
	}

	@Test
	void hashesStringsAsUtf8() {
		RequestId id = RequestId.toRequestId(serializer -> serializer.serializeField("name", "café"));
		Assertions.assertEquals("d6d176159748307b122ad6b2ee036d90f04a71db1bf8e72e9add073950f4a92b",
				id.toHexString());
	}

	@Test
	void delegationHashCoversExpirationAndPermissions() {
		Delegation delegation = new Delegation(1685570400000000000L, new byte[] {1, 2}, null);
		Assertions.assertEquals("1a69632d726571756573742d617574682d64656c65676174696f6e084018c5cbd5ccbba7fe6ae28f92902ddd7db77d458b9dac2b1c8ce6b1437f32",
				Hex.encodeHexString(delegation.signable()));
		delegation.permissions = "queries";
		Assertions.assertEquals("1a69632d726571756573742d617574682d64656c65676174696f6e2ef581fd72fd938074ad654ff3b6fe089b0cd658d013451950060ca32aabf223",
				Hex.encodeHexString(delegation.signable()));
		delegation.expiration++;
		Assertions.assertNotEquals("1a69632d726571756573742d617574682d64656c65676174696f6e2ef581fd72fd938074ad654ff3b6fe089b0cd658d013451950060ca32aabf223",
				Hex.encodeHexString(delegation.signable()));
	}

	@Test
	void parsesAndFormatsHexRequestIds() {
		String expected = "fea35ddba44484c16a83551d1e756062f135a738da19ca74d75ccc66472338a6";

		Assertions.assertEquals(expected, RequestId.fromHexString(expected).toHexString());
		Assertions.assertEquals(expected, RequestId.fromHex(Hex.decodeHex(expected)).toHexString());
	}

	@Test
	void rejectsNullRequestIdInput() {
		RequestIdError byteError = Assertions.assertThrows(RequestIdError.class, () -> RequestId.fromHex(null));
		RequestIdError stringError = Assertions.assertThrows(RequestIdError.class, () -> RequestId.fromHexString(null));

		Assertions.assertEquals(RequestIdError.RequestIdErrorCode.EMPTY_SERIALIZER, byteError.getCode());
		Assertions.assertEquals(RequestIdError.RequestIdErrorCode.EMPTY_SERIALIZER, stringError.getCode());
	}

	private static class TestValue implements Serialize {
		private final boolean reverse;

		TestValue(boolean reverse) {
			this.reverse = reverse;
		}

		@Override
		public void serialize(Serializer serializer) {
			if (reverse) {
				serializer.serializeField("name", "agent");
				serializer.serializeField("payload", new byte[] {0, (byte) 0xff, 1});
				serializer.serializeField("memo", 127L);
			} else {
				serializer.serializeField("memo", 127L);
				serializer.serializeField("payload", new byte[] {0, (byte) 0xff, 1});
				serializer.serializeField("name", "agent");
			}
		}
	}

	private static class CompositeValue implements Serialize {
		@Override
		public void serialize(Serializer serializer) {
			List<Object> values = Arrays.<Object>asList("x", 2L, new byte[] {3});
			serializer.serializeField("values", values);
			serializer.serializeField("principal", Principal.from(new byte[] {1, 2}));
			serializer.serializeField("inner", new NestedValue());
		}
	}

	private static class NestedValue implements Serialize {
		@Override
		public void serialize(Serializer serializer) {
			serializer.serializeField("nested", 9L);
			serializer.serializeField("label", "child");
		}
	}
}
