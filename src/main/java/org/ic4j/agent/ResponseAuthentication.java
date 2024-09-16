/*
 * Copyright 2021 Exilor Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.ic4j.agent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ArrayUtils;
import org.ic4j.agent.certification.Certificate;
import org.ic4j.agent.certification.hashtree.HashTree;
import org.ic4j.agent.certification.hashtree.Label;
import org.ic4j.agent.certification.hashtree.LookupResult;
import org.ic4j.agent.certification.hashtree.SubtreeLookupResult;
import org.ic4j.agent.replicaapi.CallReply;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.candid.Leb128;
import org.ic4j.types.Principal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public final class ResponseAuthentication {
	static final byte[] DER_PREFIX;
	static final int KEY_LENGTH = 96;

	static {
		DER_PREFIX = Hex.decodeHex("308182301d060d2b0601040182dc7c0503010201060c2b0601040182dc7c05030201036100");
	}

	static byte[] extractDer(byte[] buf) throws AgentError {
		int expectedLength = DER_PREFIX.length + KEY_LENGTH;

		if (buf.length != expectedLength)
			throw AgentError.create(AgentError.AgentErrorCode.DER_KEY_LENGTH_MISMATCH, expectedLength, buf.length);

		byte[] prefix = ArrayUtils.subarray(buf, 0, DER_PREFIX.length);

		if (!Arrays.equals(DER_PREFIX, prefix))
			throw AgentError.create(AgentError.AgentErrorCode.DER_PREFIX_MISMATCH, expectedLength, buf.length);

		return ArrayUtils.subarray(buf, DER_PREFIX.length, buf.length);
	}

	static RequestStatusResponse lookupRequestStatus(Certificate certificate, RequestId requestId) throws AgentError {
		List<Label> pathStatus = new ArrayList<Label>();
		pathStatus.add(new Label("request_status"));
		pathStatus.add(new Label(requestId.get()));
		pathStatus.add(new Label("status"));

		LookupResult result = certificate.tree.lookupPath(pathStatus);

		switch (result.status) {
		case ABSENT:
			throw AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_ABSENT, pathStatus);
		case UNKNOWN:
			return new RequestStatusResponse(RequestStatusResponse.InnerStatus.UNKNOWN_STATUS);
		case FOUND: {
			String status = new String(result.value, StandardCharsets.UTF_8);

			switch (status) {
			case RequestStatusResponse.DONE_STATUS_VALUE:
				return new RequestStatusResponse(RequestStatusResponse.InnerStatus.DONE_STATUS);
			case RequestStatusResponse.PROCESSING_STATUS_VALUE:
				return new RequestStatusResponse(RequestStatusResponse.InnerStatus.PROCESSING_STATUS);
			case RequestStatusResponse.RECEIVED_STATUS_VALUE:
				return new RequestStatusResponse(RequestStatusResponse.InnerStatus.RECEIVED_STATUS);
			case RequestStatusResponse.REJECTED_STATUS_VALUE:
				return lookupRejection(certificate, requestId);
			case RequestStatusResponse.REPLIED_STATUS_VALUE:
				return lookupReply(certificate, requestId);
			default:
				throw AgentError.create(AgentError.AgentErrorCode.INVALID_REQUEST_STATUS, pathStatus, status);

			}
		}

		default:
			throw AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_ERROR, pathStatus);

		}

	}

	static RequestStatusResponse lookupRejection(Certificate certificate, RequestId requestId) {
		Integer rejectCode = lookupRejectCode(certificate, requestId);
		String rejectMessage = lookupRejectMessage(certificate, requestId);

		return new RequestStatusResponse(rejectCode, rejectMessage);

	}

	static Integer lookupRejectCode(Certificate certificate, RequestId requestId) {
		List<Label> path = new ArrayList<Label>();
		path.add(new Label("request_status"));
		path.add(new Label(requestId.get()));
		path.add(new Label("reject_code"));

		byte[] code = lookupValue(certificate, path);

		return Leb128.readUnsigned(code);
	}

	static String lookupRejectMessage(Certificate certificate, RequestId requestId) {
		List<Label> path = new ArrayList<Label>();
		path.add(new Label("request_status"));
		path.add(new Label(requestId.get()));
		path.add(new Label("reject_message"));

		byte[] msg = lookupValue(certificate, path);

		return new String(msg, StandardCharsets.UTF_8);
	}

	static RequestStatusResponse lookupReply(Certificate certificate, RequestId requestId) {
		List<Label> path = new ArrayList<Label>();
		path.add(new Label("request_status"));
		path.add(new Label(requestId.get()));
		path.add(new Label("reply"));

		byte[] replyData = lookupValue(certificate, path);

		CallReply reply = new CallReply(replyData);

		return new RequestStatusResponse(reply);
	}

	static SubnetResponse lookupSubnet(Certificate certificate, byte[] rootKey) {
		ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());
		objectMapper.registerModule(new Jdk8Module());

		Principal subnetId = Agent.getSubnetId(certificate, rootKey);

		List<Label> path = new ArrayList<Label>();
		path.add(new Label("subnet"));
		path.add(new Label(subnetId.getValue()));

		HashTree subnetTree = lookupTree(certificate, path);

		path = new ArrayList<Label>();
		path.add(new Label("public_key"));

		byte[] key = lookupValue(subnetTree, path);

		path = new ArrayList<Label>();
		path.add(new Label("node"));

		HashTree nodeKeysSubtree = lookupTree(subnetTree, path);

		Map<Principal, byte[]> nodeKeys = new HashMap<Principal, byte[]>();

		List<List<Label>> paths = nodeKeysSubtree.listPaths();

		Label publicKeyLabel = new Label("public_key");
		for (List<Label> pathItem : paths) {
			if (pathItem.size() < 2)
				// if it's absent, it's because this is the wrong subnet
				throw AgentError.create(AgentError.AgentErrorCode.CERTIFICATE_NOT_AUTHORIZED);

			if (!pathItem.get(1).equals(publicKeyLabel))
				continue;

			if (pathItem.size() > 2)
				AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_ERROR, pathItem);

			Principal nodeId = Principal.from(pathItem.get(0).get());

			List<Label> itemPath = new ArrayList<Label>();
			itemPath.add(new Label(nodeId.getValue()));
			itemPath.add(new Label("public_key"));

			byte[] nodeKey = lookupValue(nodeKeysSubtree, itemPath);

			nodeKeys.put(nodeId, nodeKey);
		}

		path = new ArrayList<Label>();
		path.add(new Label("canister_ranges"));

		byte[] canisterRange = lookupValue(subnetTree, path);
		List<PrincipalRange> ranges = new ArrayList<PrincipalRange>();

		try {
			TypeReference<HashMap<Principal, Principal>> typeRef = new TypeReference<HashMap<Principal, Principal>>() {
			};

			List<List<byte[]>> rangesJson = new ArrayList<>();

			rangesJson = objectMapper.readValue(canisterRange, List.class);

			for (Iterator<List<byte[]>> iterator = rangesJson.iterator(); iterator.hasNext();) {
				List<byte[]> rangeJson = (List<byte[]>) iterator.next();

				PrincipalRange range = new PrincipalRange();

				range.low = Principal.from(rangeJson.get(0));
				range.high = Principal.from(rangeJson.get(1));
				ranges.add(range);
			}

		} catch (Exception e) {
			throw AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e, canisterRange.toString());
		}

		Subnet subnet = new Subnet();

		subnet.key = key;
		subnet.nodeKeys = nodeKeys;
		subnet.ranges = ranges;

		return new SubnetResponse(subnetId, subnet);
	}

	static byte[] lookupMetadata(Certificate certificate, Principal canisterId, String name) {
		List<Label> path = new ArrayList<Label>();
		path.add(new Label("canister"));
		path.add(new Label(canisterId.getValue()));
		path.add(new Label("metadata"));
		path.add(new Label(name));

		byte[] metadataData = lookupValue(certificate, path);

		return metadataData;
	}

	static byte[] lookupValue(Certificate certificate, List<Label> path) {
		return lookupValue(certificate.tree, path);
	}

	static byte[] lookupValue(HashTree tree, List<Label> path) {
		LookupResult result = tree.lookupPath(path);

		switch (result.status) {
		case ABSENT:
			throw AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_ABSENT, path);
		case UNKNOWN:
			throw AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_UNKNOWN, path);
		case FOUND:
			return result.value;
		case ERROR:
			throw AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_ERROR, path);
		default:
			throw AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_ERROR, path);

		}
	}

	static HashTree lookupTree(Certificate certificate, List<Label> path) {
		return lookupTree(certificate.tree, path);
	}

	static HashTree lookupTree(HashTree tree, List<Label> path) {

		SubtreeLookupResult result = tree.lookupSubtree(path);

		switch (result.status) {
		case ABSENT:
			throw AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_ABSENT, path);
		case UNKNOWN:
			throw AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_UNKNOWN, path);
		case FOUND:
			return result.value;
		default:
			throw AgentError.create(AgentError.AgentErrorCode.LOOKUP_PATH_ERROR, path);

		}
	}

}
