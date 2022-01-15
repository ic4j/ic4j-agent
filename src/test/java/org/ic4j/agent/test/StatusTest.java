package org.ic4j.agent.test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;

import org.ic4j.agent.Agent;
import org.ic4j.agent.AgentBuilder;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.Status;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.http.ReplicaOkHttpTransport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockserver.client.NettyHttpClient;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.mockserver.proxyconfiguration.ProxyConfiguration;
import org.mockserver.proxyconfiguration.ProxyConfiguration.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public class StatusTest extends MockTest {
	static final Logger LOG = LoggerFactory.getLogger(StatusTest.class);

	@Test
	public void test() {
		objectMapper.registerModule(new Jdk8Module());

		try {
			this.runMockServer();

		} catch (IOException e) {
			Assertions.fail(e.getMessage());

			LOG.error(e.getLocalizedMessage(), e);

			return;
		}

		ReplicaTransport transport;
		try {
			String transportType = TestProperties.TRANSPORT_TYPE;

			switch (transportType) {
			case "http.ok":
				transport = ReplicaOkHttpTransport.create("http://localhost:" + TestProperties.MOCK_PORT);
				break;
			default:
				transport = ReplicaApacheHttpTransport.create("http://localhost:" + TestProperties.MOCK_PORT);
				break;
			}

			Agent agent = new AgentBuilder().transport(transport).build();

			Status status = agent.status().get();

			Assertions.assertEquals(status.icAPIVersion, "0.18.0");
			Assertions.assertEquals(status.implVersion.get(), "0.8.0");
			// assertEquals(status.rootKey.get(),"0.1.0");
			LOG.info(status.icAPIVersion);

		} catch (URISyntaxException | InterruptedException | ExecutionException | AgentError e) {
			LOG.error(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());
		} finally {
			mockServerClient.stop();
		}
	}

	void runMockServer() throws IOException {

		mockServerClient.when(new HttpRequest().withMethod("GET").withPath("/api/v2/status")).respond(httpRequest -> {
			if (httpRequest.getPath().getValue().endsWith("/api/v2/status")) {
				LOG.debug("Status Request Path:" + httpRequest.getPath().getValue());
				byte[] response = {};

				if (TestProperties.FORWARD) {
					NettyHttpClient client = new NettyHttpClient(null, clientEventLoopGroup,
							ProxyConfiguration.proxyConfiguration(Type.HTTP,
									new InetSocketAddress(TestProperties.FORWARD_HOST, TestProperties.FORWARD_PORT)),
							false);

					response = client.sendRequest(HttpRequest.request().withMethod("GET")
							.withHeaders(httpRequest.getHeaders()).withPath("/api/v2/status")).get()
							.getBodyAsRawBytes();

					if (TestProperties.STORE)
						Files.write(Paths.get(
								TestProperties.STORE_PATH + File.separator + TestProperties.CBOR_STATUS_RESPONSE_FILE),
								response);

				} else
					response = Files.readAllBytes(Paths.get(getClass().getClassLoader()
							.getResource(TestProperties.CBOR_STATUS_RESPONSE_FILE).getPath()));

				JsonNode responseNode = objectMapper.readValue(response, JsonNode.class);

				LOG.debug(responseNode.toPrettyString());

				return HttpResponse.response().withStatusCode(HttpStatusCode.OK_200.code())
						.withContentType(MediaType.create("application", "cbor")).withBody(response);

			} else {
				return HttpResponse.notFoundResponse();
			}
		});
	}

}
