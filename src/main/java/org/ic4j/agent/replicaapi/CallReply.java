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

import org.ic4j.agent.Serialize;
import org.ic4j.agent.Serializer;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class CallReply extends Response implements Serialize {
	@JsonProperty("arg")
	public byte[] arg;
	
	public CallReply()
	{
		
	}
	
	public CallReply(byte[] arg)
	{
		this.arg = arg;
	}

	@Override
	public void serialize(Serializer serializer) {
		serializer.serializeField("arg", this.arg);	
	}
}
