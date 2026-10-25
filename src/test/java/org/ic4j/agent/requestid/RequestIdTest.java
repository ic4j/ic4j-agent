package org.ic4j.agent.requestid;

import java.util.Arrays;
import java.util.List;

import org.ic4j.agent.Hex;
import org.ic4j.agent.Serialize;
import org.ic4j.agent.Serializer;
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
