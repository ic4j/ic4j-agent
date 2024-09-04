/*
 * Copyright 2024 Exilor Inc.
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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.ic4j.agent.Hex;
import org.ic4j.agent.Serialize;
import org.ic4j.agent.Serializer;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.candid.annotations.Field;
import org.ic4j.candid.annotations.Name;
import org.ic4j.candid.types.Type;
import org.ic4j.types.Principal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Delegation implements Serialize{
	static final byte[] IC_REQUEST_DELEGATION_DOMAIN_SEPARATOR = "ic-request-auth-delegation".getBytes(StandardCharsets.UTF_8);
	// The delegated-to key.
    @Name("pubkey")
    @Field(Type.NAT8)		
	@JsonProperty("pubkey")
	public byte[] pubKey;
	
	// A nanosecond timestamp after which this delegation is no longer valid.
    @Name("expiration")
    @Field(Type.NAT64)
	@JsonProperty("expiration")
	public long expiration;
	
	// If present, this delegation only applies to requests sent to one of these canisters.
    @Name("targets")
    @Field(Type.PRINCIPAL)
	@JsonProperty("targets")
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public List<Principal> targets;	
	
	
    public Delegation() {
		super();
	}

	public Delegation(long expiration,byte[] pubKey, List<Principal> targets) {
		super();
		this.pubKey = pubKey;
		this.expiration = expiration;
		this.targets = targets;
	}

	// Returns the signable form of the delegation, by running it through [`to_request_id`]
    // and prepending `\x1Aic-request-auth-delegation` to the result.
	public byte[] signable()
	{
		byte[] hash = RequestId.toRequestId(this).get();
		

			byte[] separator = ArrayUtils.addAll(Hex.decodeHex("1A"),IC_REQUEST_DELEGATION_DOMAIN_SEPARATOR);
			byte[] bytes = ArrayUtils.addAll(separator,hash);
			
			return bytes;
	}

	@Override
	public void serialize(Serializer serializer) {
		serializer.serializeField("pubkey", pubKey);
		
		if(targets != null)
			serializer.serializeField("targets", targets);
		
	}

}
