package org.ic4j.agent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.ic4j.agent.certification.Certificate;
import org.ic4j.agent.replicaapi.CallReply;
import org.ic4j.agent.requestid.RequestId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

class ResponseAuthenticationTest {
	private static final RequestId REQUEST_ID = RequestId.fromHex(new byte[] {1, 2, 3});
	private static final ObjectMapper MAPPER = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());

	@ParameterizedTest
	@ValueSource(strings = {"done", "processing", "received"})
	void readsSimpleRequestStatuses(String status) throws Exception {
		RequestStatusResponse response = ResponseAuthentication.lookupRequestStatus(
				certificate(statusTree(Collections.singletonMap("status", utf8(status)))), REQUEST_ID);

		Assertions.assertEquals(status, response.status.toString());
	}

	@Test
	void reportsUnknownWhenRequestStatusIsPruned() throws Exception {
		Object requestStatus = label("request_status",
				label(REQUEST_ID.get(), Arrays.asList(4, new byte[32])));
		RequestStatusResponse response = ResponseAuthentication.lookupRequestStatus(certificate(requestStatus), REQUEST_ID);

		Assertions.assertEquals(RequestStatusResponse.InnerStatus.UNKNOWN_STATUS, response.status);
	}

	@Test
	void rejectsAbsentOrInvalidRequestStatus() throws Exception {
		Certificate absent = certificate(statusTree(Collections.singletonMap("other", utf8("value"))));
		AgentError absentError = Assertions.assertThrows(AgentError.class,
				() -> ResponseAuthentication.lookupRequestStatus(absent, REQUEST_ID));
		Assertions.assertEquals(AgentError.AgentErrorCode.LOOKUP_PATH_ABSENT, absentError.getCode());

		Certificate invalid = certificate(statusTree(Collections.singletonMap("status", utf8("queued"))));
		AgentError invalidError = Assertions.assertThrows(AgentError.class,
				() -> ResponseAuthentication.lookupRequestStatus(invalid, REQUEST_ID));
		Assertions.assertEquals(AgentError.AgentErrorCode.INVALID_REQUEST_STATUS, invalidError.getCode());
	}

	@Test
	void readsRejectionCodeAndMessageFromCertificate() throws Exception {
		Map<String, byte[]> fields = new TreeMap<>();
		fields.put("reject_code", new byte[] {4});
		fields.put("reject_message", utf8("canister trapped"));
		fields.put("status", utf8("rejected"));

		RequestStatusResponse response = ResponseAuthentication.lookupRequestStatus(
				certificate(statusTree(fields)), REQUEST_ID);

		Assertions.assertEquals(RequestStatusResponse.InnerStatus.REJECTED_STATUS, response.status);
		Assertions.assertEquals(4, response.rejected.get().rejectCode);
		Assertions.assertEquals("canister trapped", response.rejected.get().rejectMessage);
	}

	@Test
	void readsReplyPayloadFromCertificate() throws Exception {
		byte[] reply = new byte[] {0, 1, (byte) 0xff};
		Map<String, byte[]> fields = new TreeMap<>();
		fields.put("reply", reply);
		fields.put("status", utf8("replied"));

		RequestStatusResponse response = ResponseAuthentication.lookupRequestStatus(
				certificate(statusTree(fields)), REQUEST_ID);

		Assertions.assertEquals(RequestStatusResponse.InnerStatus.REPLIED_STATUS, response.status);
		CallReply callReply = response.replied.get();
		Assertions.assertArrayEquals(reply, callReply.arg);
	}

	private static Object statusTree(Map<String, byte[]> fields) {
		List<Object> entries = new ArrayList<>();
		for (Map.Entry<String, byte[]> entry : fields.entrySet())
			entries.add(label(entry.getKey(), leaf(entry.getValue())));

		return label("request_status", label(REQUEST_ID.get(), fork(entries)));
	}

	private static Object fork(List<Object> nodes) {
		if (nodes.size() == 1)
			return nodes.get(0);
		int middle = nodes.size() / 2;
		return Arrays.asList(1, fork(nodes.subList(0, middle)), fork(nodes.subList(middle, nodes.size())));
	}

	private static Object label(String name, Object subtree) {
		return label(utf8(name), subtree);
	}

	private static Object label(byte[] name, Object subtree) {
		return Arrays.asList(2, name, subtree);
	}

	private static Object leaf(byte[] value) {
		return Arrays.asList(3, value);
	}

	private static byte[] utf8(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static Certificate certificate(Object tree) throws Exception {
		Certificate certificate = new Certificate();
		certificate.tree = MAPPER.readValue(MAPPER.writeValueAsBytes(tree),
				org.ic4j.agent.certification.hashtree.HashTree.class);
		return certificate;
	}
}
