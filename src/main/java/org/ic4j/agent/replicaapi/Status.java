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
import java.util.Optional;

import org.ic4j.agent.AgentError;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonNode;

public final class Status {
	static final String UNKNOWN_STATUS_VALUE = "unknown";
	static final String RECEIVED_STATUS_VALUE = "received";
	static final String PROCESSING_STATUS_VALUE = "processing";
	static final String REJECTED_STATUS_VALUE = "rejected";
	static final String REPLIED_STATUS_VALUE = "replied";
	static final String DONE_STATUS_VALUE = "done";

	public InnerStatus status;

	public Optional<CallReply> replied;

	public Optional<Rejected> rejected;

	@JsonSetter("status")
    void setStatus(JsonNode statusNode) {
        if (statusNode != null && statusNode.isTextual()) { 
        	switch(statusNode.asText())
        	{
        	case UNKNOWN_STATUS_VALUE:
        		this.status = InnerStatus.UNKNOWN_STATUS;break;
        	case RECEIVED_STATUS_VALUE:
        		this.status = InnerStatus.RECEIVED_STATUS;break;
        	case PROCESSING_STATUS_VALUE:
        		this.status = InnerStatus.PROCESSING_STATUS;break;         		
        	case REJECTED_STATUS_VALUE:
            {
            	this.rejected =  Optional.of(new Rejected()); 
            	this.replied = Optional.empty();
            	this.status = InnerStatus.REJECTED_STATUS;
            	break;
            }
        	case REPLIED_STATUS_VALUE:
            {
            	this.replied =  Optional.of(new CallReply());  
            	this.rejected = Optional.empty();
            	this.status = InnerStatus.REPLIED_STATUS;
            	break;   	
            }
        	case DONE_STATUS_VALUE:
        		this.status = InnerStatus.DONE_STATUS;
        	}
        }
    }

	@JsonSetter("reject_code")
	void setRejectCode(JsonNode rejectCodeNode) {
		if (rejectCodeNode != null && rejectCodeNode.isInt()) {
			if (this.rejected.isPresent())
				this.rejected.get().rejectCode = rejectCodeNode.asInt();

		}
	}

	@JsonSetter("reject_message")
	void setRejectMessage(JsonNode rejectMessageNode) {
		if (rejectMessageNode != null && rejectMessageNode.isTextual()) {
			if (this.rejected.isPresent())
				this.rejected.get().rejectMessage = rejectMessageNode.asText();

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

	public final class Rejected {
		public Integer rejectCode;
		public String rejectMessage;
	}

	enum InnerStatus {
		UNKNOWN_STATUS(UNKNOWN_STATUS_VALUE),
		RECEIVED_STATUS(RECEIVED_STATUS_VALUE),
		PROCESSING_STATUS(PROCESSING_STATUS_VALUE),
		REJECTED_STATUS(REJECTED_STATUS_VALUE),
		REPLIED_STATUS(REPLIED_STATUS_VALUE),
		DONE_STATUS(DONE_STATUS_VALUE);

		String value;

		InnerStatus(String value) {
			this.value = value;
		}

	}
}
