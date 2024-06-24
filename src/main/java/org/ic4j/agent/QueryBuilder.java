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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.ArrayUtils;
import org.ic4j.types.Principal;

/*
 * A Query Request Builder.
 *	This makes it easier to do query calls without actually passing all arguments.
 */

public final class QueryBuilder {
	Agent agent;
	Principal effectiveCanisterId;
	Principal canisterId;
	String methodName;
	byte[] arg;
	Optional<Long> ingressExpiryDatetime;
	
	QueryBuilder(Agent agent, Principal canisterId,String methodName )
	{
		this.agent = agent;
		this.canisterId = canisterId;
		this.methodName = methodName;
		this.effectiveCanisterId = canisterId.clone();
		this.ingressExpiryDatetime = Optional.empty();
		this.arg = ArrayUtils.EMPTY_BYTE_ARRAY;
	}
	
	public static QueryBuilder create(Agent agent, Principal canisterId,String methodName )
	{
		return new QueryBuilder(agent, canisterId, methodName);
	}
	
	public QueryBuilder effectiveCanisterId(Principal effectiveCanisterId)
	{
		this.effectiveCanisterId = effectiveCanisterId;
		return this;	
	}
	
	public QueryBuilder arg(byte[] arg)
	{
		this.arg = arg;
		return this;	
	}
	
	/**
	Takes a SystemTime converts it to a Duration by calling
    duration_since(UNIX_EPOCH) to learn about where in time this SystemTime lies.
    The Duration is converted to nanoseconds and stored in ingressExpiryDatetime
  	*/
	public QueryBuilder expireAt(LocalDateTime time) 
	{	
		this.ingressExpiryDatetime = Optional.of(time.toEpochSecond(ZoneOffset.UTC));
				
		return this;		
	}
    /**
	Takes a Duration (i.e. 30 sec/5 min 30 sec/1 h 30 min, etc.) and adds it to the
    Duration of the current SystemTime since the UNIX_EPOCH
    Subtracts a permitted drift from the sum to account for using system time and not block time.
    Converts the difference to nanoseconds and stores in ingressExpiryDatetime	
	*/
	
	public QueryBuilder expireAfter(Duration duration) 
	{
		Duration permittedDrift = Duration.ofSeconds(Agent.DEFAULT_PERMITTED_DRIFT);

		this.ingressExpiryDatetime =  Optional.of((Duration.ofMillis(System.currentTimeMillis()).plus(duration).minus(permittedDrift)).toNanos());
		
		return this;
	}
	
	/*
	 * Make a query call. This will return a byte vector.
	 */
	 
	public CompletableFuture<byte[]> call() throws AgentError
	{
		return agent.queryRaw(this.canisterId, this.effectiveCanisterId, this.methodName, this.arg, this.ingressExpiryDatetime);
	}
	
	/*
	 * Make a query call. This will return AgentResponse with a byte vector and headers.
	 */
	 
	public CompletableFuture<Response<byte[]>> call(Map<String, String> headers) throws AgentError
	{
		Request<byte[]> request = new Request<byte[]>(this.arg, headers);
		
		return agent.queryRaw(this.canisterId, this.effectiveCanisterId, this.methodName, request, this.ingressExpiryDatetime);
	}	
	
	/*
    * Make a query call with signature verification. This will return a byte vector.
    *
    * Compared with [call][Self::call], this method will **always** verify the signature of the query response
    * regardless the Agent level configuration from [AgentBuilder::with_verify_query_signatures].
	*/
	 
	public CompletableFuture<byte[]> callWithVerification() throws AgentError
	{
		return agent.queryRaw(this.canisterId, this.effectiveCanisterId, this.methodName, this.arg, this.ingressExpiryDatetime, true);
	}
	
	/*
	 * Make a query call with signature verification. This will return AgentResponse with a byte vector and headers.
	 * 
     * Compared with [call][Self::call], this method will **always** verify the signature of the query response
     * regardless the Agent level configuration from [AgentBuilder::with_verify_query_signatures].
	 */
	 
	public CompletableFuture<Response<byte[]>> callWithVerification(Map<String, String> headers) throws AgentError
	{
		Request<byte[]> request = new Request<byte[]>(this.arg, headers);
		
		return agent.queryRaw(this.canisterId, this.effectiveCanisterId, this.methodName, request, this.ingressExpiryDatetime,true);
	}	
}
