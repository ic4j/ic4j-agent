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

package org.ic4j.agent.replicaapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.ArrayUtils;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.Hex;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.types.Principal;
import org.ic4j.types.PrincipalError;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class QueryResponse extends Response {
	static final String REJECTED_STATUS_VALUE = "rejected";
	static final String REPLIED_STATUS_VALUE = "replied";

	public InnerStatus status;

	public Optional<CallReply> replied;

	public Optional<Rejected> rejected;

	public List<NodeSignature> signatures;

	@JsonSetter("status")
	void setStatus(JsonNode statusNode) {
		if (statusNode != null && statusNode.isTextual()) {
			if (REJECTED_STATUS_VALUE.equals(statusNode.asText())) {
				this.rejected = Optional.of(new Rejected());
				this.replied = Optional.empty();
				this.status = InnerStatus.REJECTED_STATUS;
			} else if (REPLIED_STATUS_VALUE.equals(statusNode.asText())) {
				this.replied = Optional.of(new CallReply());
				this.rejected = Optional.empty();
				this.status = InnerStatus.REPLIED_STATUS;
			}
		}
	}

	@JsonSetter("reject_code")
	void setRejectCode(JsonNode rejectCodeNode) {
		if (rejectCodeNode != null && rejectCodeNode.isInt()) {
			if (this.rejected.isPresent())
				this.rejected.get().rejectCode = RejectCode.create(rejectCodeNode.asInt());

		}
	}

	@JsonSetter("reject_message")
	void setRejectMessage(JsonNode rejectMessageNode) {
		if (rejectMessageNode != null && rejectMessageNode.isTextual()) {
			if (this.rejected.isPresent())
				this.rejected.get().rejectMessage = rejectMessageNode.asText();

		}
	}

	@JsonSetter("error_code")
	void setErrorCode(JsonNode errorCodeNode) {
		if (errorCodeNode != null && errorCodeNode.isTextual()) {
			if (this.rejected.isPresent())
				this.rejected.get().errorCode = Optional.ofNullable(errorCodeNode.asText());
		}
	}

	@JsonSetter("reply")
	void setReply(JsonNode replyNode) {
		if (replyNode != null && replyNode.has("arg")) {
			JsonNode argNode = replyNode.get("arg");
			if (this.replied.isPresent())
				try {
					this.replied.get().arg = argNode.binaryValue();
				} catch (IOException e) {
					throw AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e);
				}

		}
	}

	@JsonSetter("signatures")
	void setSignatures(JsonNode signaturesNode) {
		if (signaturesNode != null && signaturesNode.isArray()) {
			ArrayNode arrayNode = (ArrayNode) signaturesNode;
			this.signatures = new ArrayList<NodeSignature>();

			Iterator<JsonNode> it = arrayNode.elements();

			while (it.hasNext()) {
				NodeSignature nodeSignature = new NodeSignature();

				ObjectNode itemNode = (ObjectNode) it.next();

				if (itemNode.has("timestamp") && !itemNode.get("timestamp").isNull())
					nodeSignature.timestamp = itemNode.get("timestamp").asLong();

				if (itemNode.has("signature") && !itemNode.get("signature").isNull()) {
					try {
						nodeSignature.signature = itemNode.get("signature").binaryValue();
					} catch (IOException e) {
						throw AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e);
					}
				}

				if (itemNode.has("identity") && !itemNode.get("identity").isNull()) {
					try {
						nodeSignature.identity = Principal.from(itemNode.get("identity").binaryValue());
					} catch (PrincipalError | IOException e) {
						throw AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e);
					}
				}

				this.signatures.add(nodeSignature);
			}
		}
	}

	public byte[] signable(RequestId requestId, long timestamp) {
		QueryResponseSignable responseSignable = new QueryResponseSignable(this, requestId, timestamp);

		RequestId responseId = RequestId.toRequestId(responseSignable);
		byte[] prefix = ArrayUtils.addAll(Hex.decodeHex("0B"), "ic-response".getBytes(StandardCharsets.UTF_8));
		byte[] signable = ArrayUtils.addAll(prefix, responseId.get());

		return signable;

	}

	public final class Rejected {
		public RejectCode rejectCode;
		public String rejectMessage;
		public Optional<String> errorCode;
	}

	public enum InnerStatus {
		REJECTED_STATUS(REJECTED_STATUS_VALUE), REPLIED_STATUS(REPLIED_STATUS_VALUE);

		String value;

		InnerStatus(String value) {
			this.value = value;
		}

	}

}
