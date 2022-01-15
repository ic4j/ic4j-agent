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

import java.util.Optional;

import org.ic4j.agent.replicaapi.CallReply;

public final class RequestStatusResponse {
	public static final String UNKNOWN_STATUS_VALUE = "unknown";
	public static final String RECEIVED_STATUS_VALUE = "received";
	public static final String PROCESSING_STATUS_VALUE = "processing";
	public static final String REJECTED_STATUS_VALUE = "rejected";
	public static final String REPLIED_STATUS_VALUE = "replied";
	public static final String DONE_STATUS_VALUE = "done";

	public InnerStatus status;

	public Optional<CallReply> replied;

	public Optional<Rejected> rejected;
	
	
	RequestStatusResponse(InnerStatus status)
	{
		this.status = status;
	}

	RequestStatusResponse(CallReply replied)
	{
		this.status = InnerStatus.REPLIED_STATUS;
		this.replied = Optional.of(replied);
	}
	
	RequestStatusResponse(Integer rejectCode, String rejectMessage)
	{
		
		this.status = InnerStatus.REJECTED_STATUS;
		this.rejected = Optional.of(new Rejected(rejectCode, rejectMessage));
	}	
	
	public final class Rejected {
		public Integer rejectCode;
		public String rejectMessage;
		
		Rejected(Integer rejectCode, String rejectMessage)
		{
			this.rejectCode = rejectCode;
			this.rejectMessage = rejectMessage;
		}
	}

	public enum InnerStatus {
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
		
		public String toString()
		{
			return this.value;
		}

	}
}
