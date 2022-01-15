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

import java.util.List;

import org.ic4j.agent.Serialize;
import org.ic4j.agent.Serializer;
import org.ic4j.types.Principal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.annotation.JsonAppend;

public final class ReadStateContent implements Serialize{

	@JsonUnwrapped
	public ReadStateRequest readStateRequest = new ReadStateRequest();
	
	@JsonAppend(
		    prepend = true,
		    attrs = {
		            @JsonAppend.Attr( value = "request_type")
		    })
	public final class ReadStateRequest
	{
		@JsonProperty("ingress_expiry")
		public Long ingressExpiry;		
		@JsonProperty("sender")
		public Principal sender;		
		@JsonProperty("paths")
		public List<List<byte[]>> paths;
	}

	@Override
	public void serialize(Serializer serializer) {
		serializer.serializeField("request_type", "read_state");
		serializer.serializeField("ingress_expiry", readStateRequest.ingressExpiry);
		serializer.serializeField("sender",readStateRequest.sender);
		if(readStateRequest.paths != null && !readStateRequest.paths.isEmpty())
			serializer.serializeField("paths",readStateRequest.paths);		
	}
}
