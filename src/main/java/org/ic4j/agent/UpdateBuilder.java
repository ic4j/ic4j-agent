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
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.ArrayUtils;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.types.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
* An Update Request Builder.
* This makes it easier to do update calls without actually passing all arguments or specifying
* if you want to wait or not.
*/
public final class UpdateBuilder {
	static final Logger LOG = LoggerFactory.getLogger(UpdateBuilder.class);
	
	Agent agent;
	Principal effectiveCanisterId;
	Principal canisterId;
	String methodName;
	byte[] arg;
	Optional<Long> ingressExpiryDatetime;
	
	UpdateBuilder(Agent agent, Principal canisterId,String methodName )
	{
		this.agent = agent;
		this.canisterId = canisterId;
		this.methodName = methodName;
		this.effectiveCanisterId = canisterId.clone();
		this.ingressExpiryDatetime = Optional.empty();
		this.arg = ArrayUtils.EMPTY_BYTE_ARRAY;
	}
	
	public static UpdateBuilder create(Agent agent, Principal canisterId,String methodName )
	{
		return new UpdateBuilder(agent, canisterId, methodName);
	}
	
	public UpdateBuilder effectiveCanisterId(Principal effectiveCanisterId)
	{
		this.effectiveCanisterId = effectiveCanisterId;
		return this;	
	}
	
	public UpdateBuilder arg(byte[] arg)
	{
		this.arg = arg;
		return this;	
	}
	
	/**
	Takes a SystemTime converts it to a Duration by calling
    duration_since(UNIX_EPOCH) to learn about where in time this SystemTime lies.
    The Duration is converted to nanoseconds and stored in ingressExpiryDatetime
  	*/
	public UpdateBuilder expireAt(LocalDateTime time) 
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
	
	public UpdateBuilder expireAfter(Duration duration) 
	{
		Duration permittedDrift = Duration.ofSeconds(Agent.DEFAULT_PERMITTED_DRIFT);

		this.ingressExpiryDatetime =  Optional.of((Duration.ofMillis(System.currentTimeMillis()).minus(permittedDrift)).toNanos());
		
		return this;
	}
	
	/*
	 * Make a update call. This will return a byte vector.
	 */
	 
	public CompletableFuture<byte[]> callAndWait(Waiter waiter) throws AgentError
	{
		RequestId requestId;
		try {
			requestId = agent.updateRaw(this.canisterId, this.effectiveCanisterId, this.methodName, this.arg, this.ingressExpiryDatetime).get();
		} catch (InterruptedException | ExecutionException | AgentError e) {
			throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e, e.getLocalizedMessage());
		}
		
		CompletableFuture<byte[]> response = new CompletableFuture<byte[]>();
		
		do
		{
				try {
					RequestStatusResponse statusResponse = agent.requestStatusRaw(requestId, effectiveCanisterId).get();
					
					switch(statusResponse.status)
					{
						case REPLIED_STATUS:
							response.complete(statusResponse.replied.get().arg);
							return response;
						case REJECTED_STATUS:
							response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.REPLICA_ERROR,statusResponse.rejected.get().rejectCode,statusResponse.rejected.get().rejectMessage));
							return response;
						case DONE_STATUS:	
							response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.REQUEST_STATUS_DONE_NO_REPLY,requestId.toHexString()));
							return response;							
							
					}
				} catch (InterruptedException | ExecutionException e) {
					LOG.debug(e.getLocalizedMessage(),e);
				}
				catch(AgentError e)
				{
					LOG.debug(e.getLocalizedMessage(),e);
				}

		}while(waiter.waitUntil());
		
		throw AgentError.create(AgentError.AgentErrorCode.TIMEOUT_WAITING_FOR_RESPONSE);
	}	
	
	/*
	 * Make a update call. This will return a byte RequestId.
	 */
	 
	public CompletableFuture<RequestId> call() throws AgentError
	{
		return agent.updateRaw(this.canisterId, this.effectiveCanisterId, this.methodName, this.arg, this.ingressExpiryDatetime);
	}
	

	public CompletableFuture<byte[]> getState(RequestId requestId, Waiter waiter) throws AgentError
	{
	
		CompletableFuture<byte[]> response = new CompletableFuture<byte[]>();
		
		do
		{
				try {
					RequestStatusResponse statusResponse = agent.requestStatusRaw(requestId, effectiveCanisterId).get();
					
					switch(statusResponse.status)
					{
						case REPLIED_STATUS:
							response.complete(statusResponse.replied.get().arg);
							return response;
						case REJECTED_STATUS:
							response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.REPLICA_ERROR,statusResponse.rejected.get().rejectCode,statusResponse.rejected.get().rejectMessage));
							return response;
						case DONE_STATUS:	
							response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.REQUEST_STATUS_DONE_NO_REPLY,requestId.toHexString()));
							return response;							
							
					}
				} catch (InterruptedException | ExecutionException e) {
					LOG.debug(e.getLocalizedMessage(),e);
				}
				catch(AgentError e)
				{
					LOG.debug(e.getLocalizedMessage(),e);
				}

		}while(waiter.waitUntil());
		
		throw AgentError.create(AgentError.AgentErrorCode.TIMEOUT_WAITING_FOR_RESPONSE);
	}	
	
	/*
	 * Make a update call. This will return AgentResponse with a requestId and headers.
	 */
	 
	public CompletableFuture<Response<RequestId>> call(Map<String, String> headers) throws AgentError
	{
		Request<byte[]> request = new Request<byte[]>(this.arg, headers);
		return agent.updateRaw(this.canisterId, this.effectiveCanisterId, this.methodName, request, this.ingressExpiryDatetime);
	}
	
	public CompletableFuture<Response<byte[]>> getState(RequestId requestId, Map<String, String> headers, Waiter waiter) throws AgentError
	{
	
		CompletableFuture<Response<byte[]>> response = new CompletableFuture<Response<byte[]>>();
		
		do
		{
				try {
					Request<Void> request = new Request<Void>(null, headers);
					
					Response<RequestStatusResponse> rawResponse = agent.requestStatusRaw(requestId, effectiveCanisterId, request).get();
					RequestStatusResponse statusResponse = rawResponse.getPayload();
					
					switch(statusResponse.status)
					{
						case REPLIED_STATUS:
							Response<byte[]> stateResponse = new Response<byte[]>(statusResponse.replied.get().arg, rawResponse.getHeaders());
							response.complete(stateResponse);
							return response;
						case REJECTED_STATUS:
							response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.REPLICA_ERROR,statusResponse.rejected.get().rejectCode,statusResponse.rejected.get().rejectMessage));
							return response;
						case DONE_STATUS:	
							response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.REQUEST_STATUS_DONE_NO_REPLY,requestId.toHexString()));
							return response;							
							
					}
				} catch (InterruptedException | ExecutionException e) {
					LOG.debug(e.getLocalizedMessage(),e);
				}
				catch(AgentError e)
				{
					LOG.debug(e.getLocalizedMessage(),e);
				}

		}while(waiter.waitUntil());
		
		throw AgentError.create(AgentError.AgentErrorCode.TIMEOUT_WAITING_FOR_RESPONSE);
	}	
	
}
