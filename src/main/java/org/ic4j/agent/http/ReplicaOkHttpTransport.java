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

package org.ic4j.agent.http;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.ReplicaResponse;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.types.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ReplicaOkHttpTransport implements ReplicaTransport {

	protected static final Logger LOG = LoggerFactory.getLogger(ReplicaOkHttpTransport.class);

	final OkHttpClient client;

	URI uri;
	
	private final MediaType dfinityContentType = MediaType.parse(ReplicaHttpProperties.DFINITY_CONTENT_TYPE);

	ReplicaOkHttpTransport(URI url) {
		
		//check if url ends with /	
		if('/' == url.toString().charAt(url.toString().length() - 1))
			this.uri = URI.create(url.toString().substring(0, url.toString().length() - 1));
		else	
			this.uri = url;

		client = new OkHttpClient();
	}

	ReplicaOkHttpTransport(URI url, int timeout) {
		//check if url ends with /	
		if('/' == url.toString().charAt(url.toString().length() - 1))
			this.uri = URI.create(url.toString().substring(0, url.toString().length() - 1));
		else	
			this.uri = url;

		client = new OkHttpClient.Builder().readTimeout(timeout, TimeUnit.SECONDS).build();
	}

	public static ReplicaTransport create(String url) throws URISyntaxException {
		return new ReplicaOkHttpTransport(new URI(url));
	}

	public static ReplicaTransport create(String url,  int timeout)
			throws URISyntaxException {
		return new ReplicaOkHttpTransport(new URI(url), timeout);
	}

	public CompletableFuture<ReplicaResponse> status() {
		
		Request httpRequest = new Request.Builder().url(uri.toString() + ReplicaHttpProperties.API_VERSION_URL_PART + ReplicaHttpProperties.STATUS_URL_PART).get().addHeader(ReplicaHttpProperties.CONTENT_TYPE, ReplicaHttpProperties.DFINITY_CONTENT_TYPE).build();		

		return this.execute(httpRequest);
	}

	public CompletableFuture<ReplicaResponse> query(Principal containerId, byte[] envelope, Map<String,String> headers) {
		RequestBody requestBody =RequestBody.create(envelope,dfinityContentType);
			
		Builder builder = new Request.Builder().url(uri.toString() + ReplicaHttpProperties.API_VERSION_URL_PART + String.format(ReplicaHttpProperties.QUERY_URL_PART, containerId.toString())).post(requestBody);		
				
		if(headers != null)
		{
			Iterator<String> names = headers.keySet().iterator();
			
			while(names.hasNext())
			{
				String name = names.next();
				builder.addHeader(name, headers.get(name));
			}			
		}
		
		Request httpRequest =  builder.build();		
		
		return this.execute(httpRequest);
	}

	public CompletableFuture<ReplicaResponse> call(Principal containerId, byte[] envelope, RequestId requestId, Map<String,String> headers) {
		RequestBody requestBody =RequestBody.create(envelope,dfinityContentType);
		
		Builder builder  = new Request.Builder().url(uri.toString() + ReplicaHttpProperties.API_VERSION_URL_PART + String.format(ReplicaHttpProperties.CALL_URL_PART, containerId.toString())).post(requestBody);		
				
		
		if(headers != null)
		{
			Iterator<String> names = headers.keySet().iterator();
			
			while(names.hasNext())
			{
				String name = names.next();
				builder.addHeader(name, headers.get(name));
			}			
		}
		
		Request httpRequest =  builder.build();
		
		return this.execute(httpRequest);
	}

	public CompletableFuture<ReplicaResponse> readState(Principal containerId, byte[] envelope, Map<String,String> headers) {
		RequestBody requestBody =RequestBody.create(envelope,dfinityContentType);
		
		Builder builder = new Request.Builder().url(uri.toString() + ReplicaHttpProperties.API_VERSION_URL_PART + String.format(ReplicaHttpProperties.READ_STATE_URL_PART, containerId.toString())).post(requestBody);		
				
		if(headers != null)
		{
			Iterator<String> names = headers.keySet().iterator();
			
			while(names.hasNext())
			{
				String name = names.next();
				builder.addHeader(name, headers.get(name));
			}			
		}
		
		Request httpRequest =  builder.build();
		
		return this.execute(httpRequest);
	}

	CompletableFuture<ReplicaResponse> execute(Request httpRequest) throws AgentError {

		try {
			URI requestUri = httpRequest.url().uri();

			LOG.debug("Executing request " + httpRequest.method() + " " + requestUri);

			CompletableFuture<ReplicaResponse> response = new CompletableFuture<ReplicaResponse>();

			Call call = client.newCall(httpRequest);
			
			call.enqueue(new Callback()
			{

				@Override
				public void onResponse(Call call, Response httpResponse) {
					LOG.debug(requestUri + "->" + httpResponse.code());

					byte[] bytes;
					try {
						ReplicaResponse replicaResponse = new ReplicaResponse();
						
						replicaResponse.headers = new HashMap<String,String>();
						
						Headers headers = httpResponse.headers();
						
						Iterator<String> names = headers.names().iterator();
						
						while(names.hasNext())
						{
							String name = names.next();
							replicaResponse.headers.put(name, headers.get(name));
						}
						
						bytes = httpResponse.body().bytes();
						if (bytes == null)
							bytes = ArrayUtils.EMPTY_BYTE_ARRAY;
						
						replicaResponse.payload = bytes;

						response.complete(replicaResponse);						
					} catch (Throwable t) {
						LOG.debug(requestUri + "->" + t);
						response.completeExceptionally(
								AgentError.create(AgentError.AgentErrorCode.HTTP_ERROR, t, t.getLocalizedMessage()));
					}

				}

				@Override
				public void onFailure(Call call, IOException ex) {
					LOG.debug(requestUri + "->" + ex);
					response.completeExceptionally(
							AgentError.create(AgentError.AgentErrorCode.HTTP_ERROR, ex, ex.getLocalizedMessage()));
				}
			});

			return response;

		} catch (Exception e) {
			throw AgentError.create(AgentError.AgentErrorCode.URL_PARSE_ERROR, e);
		}

	}
	
	public void close()
	{	
        if(this.client == null)
        	return;
		// Get the dispatcher from the client
        Dispatcher dispatcher = this.client.dispatcher();

        // Shut down the dispatcher to stop accepting new calls
        dispatcher.executorService().shutdown();

        // Cancel any ongoing calls
        for (Call call : dispatcher.queuedCalls()) {
            call.cancel();
        }
        for (Call call : dispatcher.runningCalls()) {
            call.cancel();
        }

        // Close the client to clean up resources
        if(this.client.connectionPool() != null)
        	this.client.connectionPool().evictAll();
        try {
        	if(this.client.cache() != null)
        		this.client.cache().close();
		} catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
		}
	}

}
