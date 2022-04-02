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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.types.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicaApacheHttpTransport implements ReplicaTransport {
	protected static final Logger LOG = LoggerFactory.getLogger(ReplicaApacheHttpTransport.class);

	final IOReactorConfig ioReactorConfig;
	final CloseableHttpAsyncClient client;

	URI uri;
	private final ContentType dfinityContentType = ContentType.create(ReplicaHttpProperties.DFINITY_CONTENT_TYPE);

	ReplicaApacheHttpTransport(URI url) {
		this.uri = url;

		ioReactorConfig = IOReactorConfig.custom().setSoTimeout(Timeout.ofSeconds(ReplicaHttpProperties.TIMEOUT)).build();

		client = HttpAsyncClients.custom().setIOReactorConfig(ioReactorConfig).build();
	}

	ReplicaApacheHttpTransport(URI url, int maxTotal, int maxPerRoute, long connectionTimeToLive, int timeout) {
		this.uri = url;

		PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder.create()
				.setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT).setConnPoolPolicy(PoolReusePolicy.LIFO)
				.setConnectionTimeToLive(TimeValue.ofMinutes(connectionTimeToLive)).setMaxConnTotal(maxTotal)
				.setMaxConnPerRoute(maxPerRoute).build();

		ioReactorConfig = IOReactorConfig.custom().setSoTimeout(Timeout.ofSeconds(timeout)).build();

		client = HttpAsyncClients.custom().setConnectionManager(connectionManager).setIOReactorConfig(ioReactorConfig)
				.build();
	}

	ReplicaApacheHttpTransport(URI url, AsyncClientConnectionManager connectionManager, int timeout) {
		this.uri = url;

		ioReactorConfig = IOReactorConfig.custom().setSoTimeout(Timeout.ofSeconds(timeout)).build();

		client = HttpAsyncClients.custom().setConnectionManager(connectionManager).setIOReactorConfig(ioReactorConfig)
				.build();
	}

	public static ReplicaTransport create(String url) throws URISyntaxException {
		return new ReplicaApacheHttpTransport(new URI(url));
	}

	public static ReplicaTransport create(String url, int maxTotal, int maxPerRoute, long connectionTimeToLive,
			int timeout) throws URISyntaxException {
		return new ReplicaApacheHttpTransport(new URI(url), maxTotal, maxPerRoute, connectionTimeToLive, timeout);
	}

	public static ReplicaTransport create(String url, AsyncClientConnectionManager connectionManager, int timeout)
			throws URISyntaxException {
		return new ReplicaApacheHttpTransport(new URI(url), connectionManager, timeout);
	}

	public CompletableFuture<byte[]> status() {

		HttpHost target = HttpHost.create(uri);
		
		SimpleHttpRequest httpRequest = new SimpleHttpRequest(Method.GET,target,ReplicaHttpProperties.API_VERSION_URL_PART + ReplicaHttpProperties.STATUS_URL_PART);

		return this.execute(httpRequest, Optional.empty());

	}

	public CompletableFuture<byte[]> query(Principal containerId, byte[] envelope) {

		HttpHost target = HttpHost.create(uri);
		
		SimpleHttpRequest httpRequest = new SimpleHttpRequest(Method.POST,target,ReplicaHttpProperties.API_VERSION_URL_PART + String.format(ReplicaHttpProperties.QUERY_URL_PART, containerId.toString()));
				
		return this.execute(httpRequest, Optional.of(envelope));

	}

	public CompletableFuture<byte[]> call(Principal containerId, byte[] envelope, RequestId requestId) {

		HttpHost target = HttpHost.create(uri);
		
		SimpleHttpRequest httpRequest = new SimpleHttpRequest(Method.POST,target,ReplicaHttpProperties.API_VERSION_URL_PART + String.format(ReplicaHttpProperties.CALL_URL_PART, containerId.toString()));

		return this.execute(httpRequest, Optional.of(envelope));

	}

	public CompletableFuture<byte[]> readState(Principal containerId, byte[] envelope) {

		HttpHost target = HttpHost.create(uri);

		SimpleHttpRequest httpRequest = new SimpleHttpRequest(Method.POST,target,ReplicaHttpProperties.API_VERSION_URL_PART + String.format(ReplicaHttpProperties.READ_STATE_URL_PART, containerId.toString()));

		return this.execute(httpRequest, Optional.of(envelope));

	}

	CompletableFuture<byte[]> execute(SimpleHttpRequest httpRequest, Optional<byte[]> payload) throws AgentError {

		try {
			client.start();

			URI requestUri = httpRequest.getUri();

			LOG.debug("Executing request " + httpRequest.getMethod() + " " + requestUri);

			if (payload.isPresent())
				httpRequest.setBody(payload.get(), dfinityContentType);
			else
				httpRequest.setHeader(HttpHeaders.CONTENT_TYPE, ReplicaHttpProperties.DFINITY_CONTENT_TYPE);

			CompletableFuture<byte[]> response = new CompletableFuture<byte[]>();

			client.execute(httpRequest, new FutureCallback<SimpleHttpResponse>() {

				@Override
				public void completed(SimpleHttpResponse httpResponse) {
					LOG.debug(requestUri + "->" + httpResponse.getCode());

					byte[] bytes = httpResponse.getBodyBytes();

					if (bytes == null)
						bytes = ArrayUtils.EMPTY_BYTE_ARRAY;

					response.complete(bytes);
				}

				@Override
				public void failed(Exception ex) {
					LOG.debug(requestUri + "->" + ex);
					response.completeExceptionally(
							AgentError.create(AgentError.AgentErrorCode.HTTP_ERROR, ex, ex.getLocalizedMessage()));
				}

				@Override
				public void cancelled() {
					LOG.debug(requestUri + " cancelled");
					response.completeExceptionally(
							AgentError.create(AgentError.AgentErrorCode.TRANSPORT_ERROR, requestUri));
				}

			});

			return response;

		} catch (URISyntaxException e) {
			throw AgentError.create(AgentError.AgentErrorCode.URL_PARSE_ERROR, e);
		}

	}

}
