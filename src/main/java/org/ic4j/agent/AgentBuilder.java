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

import java.time.Duration;
import java.util.Optional;

import org.ic4j.agent.identity.Identity;


public class AgentBuilder {
	AgentConfig config = new AgentConfig();
	
	/**
	 * Create an instance of [Agent] with the information from this builder.
	 * @return agent Dfinity agent instance
	 */
	public Agent build()
	{
		Agent agent = new Agent(this);
		
		return agent;
	}

	public AgentBuilder transport(ReplicaTransport transport)
	{
		this.config.transport = Optional.of(transport);
		return this;
	}
	
	/**
    * Provides a _default_ ingress expiry. This is the delta that will be applied
    * at the time an update or query is made. The default expiry cannot be a
    * fixed system time.
    * @param duration default ingress expiry
    */
	
	public AgentBuilder ingresExpiry(Duration duration)
	{
		this.config.ingressExpiryDuration = Optional.of(duration);
		return this;
	}	
	
	/*
	 * Add an identity provider for signing messages. This is required.
	 * @param identity identity provider
	 */
	public AgentBuilder identity(Identity identity)
	{
		this.config.identity = identity;
		return this;
	}	
	
	/*
	* Add a NonceFactory to this Agent. By default, no nonce is produced.
	*/
	
	public AgentBuilder nonceFactory(NonceFactory nonceFactory)
	{
		this.config.nonceFactory = nonceFactory;
		return this;
	}
	
	

}
