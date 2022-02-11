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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.types.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ReplicaOkHttpTransport implements ReplicaTransport {
	static final String CONTENT_TYPE = "Content-Type"; 
	static final int TIMEOUT = 2;
	static final long CONNECTION_TTL = 1L;

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

	public CompletableFuture<byte[]> status() {
		
		Request httpRequest = new Request.Builder().url(uri.toString() + ReplicaHttpProperties.API_VERSION_URL_PART + ReplicaHttpProperties.STATUS_URL_PART).get().addHeader(CONTENT_TYPE, ReplicaHttpProperties.DFINITY_CONTENT_TYPE).build();		

		return this.execute(httpRequest);

	}

	public CompletableFuture<byte[]> query(Principal containerId, byte[] envelope) {
		RequestBody requestBody =RequestBody.create(envelope,dfinityContentType);
			
		Request httpRequest = new Request.Builder().url(uri.toString() + ReplicaHttpProperties.API_VERSION_URL_PART + String.format(ReplicaHttpProperties.QUERY_URL_PART, containerId.toString())).post(requestBody).build();		
				
		return this.execute(httpRequest);

	}

	public CompletableFuture<byte[]> call(Principal containerId, byte[] envelope, RequestId requestId) {
		RequestBody requestBody =RequestBody.create(envelope,dfinityContentType);
		
		Request httpRequest = new Request.Builder().url(uri.toString() + ReplicaHttpProperties.API_VERSION_URL_PART + String.format(ReplicaHttpProperties.CALL_URL_PART, containerId.toString())).post(requestBody).build();		
				
		return this.execute(httpRequest);

	}

	public CompletableFuture<byte[]> readState(Principal containerId, byte[] envelope) {
		RequestBody requestBody =RequestBody.create(envelope,dfinityContentType);
		
		Request httpRequest = new Request.Builder().url(uri.toString() + ReplicaHttpProperties.API_VERSION_URL_PART + String.format(ReplicaHttpProperties.READ_STATE_URL_PART, containerId.toString())).post(requestBody).build();		
				
		return this.execute(httpRequest);

	}

	CompletableFuture<byte[]> execute(Request httpRequest) throws AgentError {

		try {
			URI requestUri = httpRequest.url().uri();

			LOG.debug("Executing request " + httpRequest.method() + " " + requestUri);

			CompletableFuture<byte[]> response = new CompletableFuture<byte[]>();

			Call call = client.newCall(httpRequest);
			//		(httpRequest, new FutureCallback<SimpleHttpResponse>()
			
			call.enqueue(new Callback()
			{

				@Override
				public void onResponse(Call call, Response httpResponse) {
					LOG.debug(requestUri + "->" + httpResponse.code());

					byte[] bytes;
					try {
						bytes = httpResponse.body().bytes();
						if (bytes == null)
							bytes = ArrayUtils.EMPTY_BYTE_ARRAY;

						response.complete(bytes);						
					} catch (IOException e) {
						response.completeExceptionally(
								AgentError.create(AgentError.AgentErrorCode.HTTP_ERROR, e, e.getLocalizedMessage()));
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
			// TODO Auto-generated catch block
			LOG.debug(e.getLocalizedMessage());
			throw AgentError.create(AgentError.AgentErrorCode.URL_PARSE_ERROR, e);
		}

	}

}
