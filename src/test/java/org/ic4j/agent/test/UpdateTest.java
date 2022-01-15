package org.ic4j.agent.test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ic4j.agent.Agent;
import org.ic4j.agent.AgentBuilder;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.NonceFactory;
import org.ic4j.agent.ProxyBuilder;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.RequestStatusResponse;
import org.ic4j.agent.UpdateBuilder;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.http.ReplicaOkHttpTransport;
import org.ic4j.agent.identity.BasicIdentity;
import org.ic4j.agent.identity.Identity;
import org.ic4j.agent.replicaapi.CallRequestContent;
import org.ic4j.agent.replicaapi.Certificate;
import org.ic4j.agent.replicaapi.Envelope;
import org.ic4j.agent.replicaapi.ReadStateResponse;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.types.Principal;
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

public class UpdateTest extends MockTest {
	static final Logger LOG = LoggerFactory.getLogger(UpdateTest.class);

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
			Security.addProvider(new BouncyCastleProvider());

			KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

			Identity identity = BasicIdentity.fromKeyPair(keyPair);

			String transportType = TestProperties.TRANSPORT_TYPE;

			switch (transportType) {
			case "http.ok":
				transport = ReplicaOkHttpTransport.create("http://localhost:" + TestProperties.MOCK_PORT);
				break;
			default:
				transport = ReplicaApacheHttpTransport.create("http://localhost:" + TestProperties.MOCK_PORT);
				break;
			}

			Agent agent = new AgentBuilder().transport(transport).identity(identity).nonceFactory(new NonceFactory())
					.build();

			// test String argument
			List<IDLValue> args = new ArrayList<IDLValue>();

			String value = "x";

			args.add(IDLValue.create(new String(value)));

			IDLArgs idlArgs = IDLArgs.create(args);

			byte[] buf = idlArgs.toBytes();

			Optional<Long> ingressExpiryDatetime = Optional.empty();
			// ingressExpiryDatetime =
			// Optional.of(Long.parseUnsignedLong("1623389588095477000"));

			CompletableFuture<RequestId> response = agent.updateRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "greet", buf, ingressExpiryDatetime);

			try {
				RequestId requestId = response.get();

				LOG.debug("Request Id:" + requestId.toHexString());

				TimeUnit.SECONDS.sleep(10);

				if (!TestProperties.FORWARD) {
					// get hard coded request id if we use mock response file
					// TODO still need to figure it out, for now we skip text if response
					// comes from file

					requestId = RequestId
							.fromHexString("fea35ddba44484c16a83551d1e756062f135a738da19ca74d75ccc66472338a6");
				}

				CompletableFuture<RequestStatusResponse> statusResponse = agent.requestStatusRaw(requestId,
						Principal.fromString(TestProperties.CANISTER_ID));

				if (TestProperties.FORWARD) {

					RequestStatusResponse requestStatusResponse = statusResponse.get();

					LOG.debug(requestStatusResponse.status.toString());

					Assertions.assertEquals(requestStatusResponse.status.toString(),
							RequestStatusResponse.REPLIED_STATUS_VALUE);

					byte[] output = requestStatusResponse.replied.get().arg;

					IDLArgs outArgs = IDLArgs.fromBytes(output);

					LOG.info(outArgs.getArgs().get(0).getValue().toString());

					Assertions.assertEquals(outArgs.getArgs().get(0).getValue().toString(), "Hello, " + value + "!");

				}

				UpdateBuilder updateBuilder = UpdateBuilder
						.create(agent, Principal.fromString(TestProperties.CANISTER_ID), "greet").arg(buf);

				if (TestProperties.FORWARD) {
					CompletableFuture<byte[]> builderResponse = updateBuilder
							.callAndWait(org.ic4j.agent.Waiter.create(60, 5));

					byte[] output = builderResponse.get();
					IDLArgs outArgs = IDLArgs.fromBytes(output);

					LOG.info(outArgs.getArgs().get(0).getValue().toString());

					Assertions.assertEquals(outArgs.getArgs().get(0).getValue().toString(), "Hello, " + value + "!");
				}

				// test ProxyBuilder

				HelloProxy hello = ProxyBuilder.create(agent, Principal.fromString(TestProperties.CANISTER_ID))
						.getProxy(HelloProxy.class);

				if (TestProperties.FORWARD) {
					CompletableFuture<String> proxyResponse = hello.greet(value);

					String output = proxyResponse.get();
					LOG.info(output);
					Assertions.assertEquals(output, "Hello, " + value + "!");
				}

			} catch (Throwable ex) {
				LOG.error(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getMessage());
			}

		} catch (URISyntaxException e) {
			LOG.error(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());
		} catch (AgentError e) {
			LOG.error(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());
		} catch (NoSuchAlgorithmException e) {
			LOG.error(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());
		} finally {
			mockServerClient.stop();
		}

	}

	void runMockServer() throws IOException {

		mockServerClient.when(new HttpRequest().withMethod("POST")).respond(httpRequest -> {
			LOG.debug("Update Request Path:" + httpRequest.getPath().getValue());
			if (httpRequest.getPath().getValue()
					.endsWith("/api/v2/canister/" + TestProperties.CANISTER_ID + "/read_state")) {
				byte[] request = httpRequest.getBodyAsRawBytes();

				JsonNode requestNode = objectMapper.readValue(request, JsonNode.class);

				LOG.debug(requestNode.toPrettyString());

				LOG.debug(Paths.get(getClass().getClassLoader()
						.getResource(TestProperties.CBOR_UPDATE_GREET_RESPONSE_FILE).getPath()).toString());

				byte[] response = Files.readAllBytes(Paths.get(getClass().getClassLoader()
						.getResource(TestProperties.CBOR_UPDATE_GREET_RESPONSE_FILE).getPath()));

				if (TestProperties.FORWARD) {
					NettyHttpClient client = new NettyHttpClient(null, clientEventLoopGroup,
							ProxyConfiguration.proxyConfiguration(Type.HTTP,
									new InetSocketAddress(TestProperties.FORWARD_HOST, TestProperties.FORWARD_PORT)),
							false);

					response = client
							.sendRequest(HttpRequest.request().withMethod("POST").withHeaders(httpRequest.getHeaders())
									.withPath("/api/v2/canister/" + TestProperties.CANISTER_ID + "/read_state")
									.withBody(request))
							.get().getBodyAsRawBytes();

					if (TestProperties.STORE)
						Files.write(Paths.get(TestProperties.STORE_PATH + File.separator
								+ TestProperties.CBOR_UPDATE_GREET_RESPONSE_FILE), response);
				}

				JsonNode responseNode = objectMapper.readValue(response, JsonNode.class);

				LOG.debug(responseNode.toPrettyString());

				ReadStateResponse readStateResponse = objectMapper.readValue(response, ReadStateResponse.class);

				Certificate cert = objectMapper.readValue(readStateResponse.certificate, Certificate.class);

				// LOG.info(cert.tree.toPrettyString());

				return HttpResponse.response().withStatusCode(HttpStatusCode.OK_200.code())
						.withContentType(MediaType.create("application", "cbor")).withBody(response);

			} else if (httpRequest.getPath().getValue()
					.endsWith("/api/v2/canister/" + TestProperties.CANISTER_ID + "/call")) {
				byte[] request = httpRequest.getBodyAsRawBytes();

				JsonNode requestNode = objectMapper.readValue(request, JsonNode.class);

				LOG.debug(requestNode.toPrettyString());

				byte[] response = {};

				Envelope<CallRequestContent> envelope = objectMapper.readValue(request, Envelope.class);

				if (TestProperties.FORWARD) {
					NettyHttpClient client = new NettyHttpClient(null, clientEventLoopGroup,
							ProxyConfiguration.proxyConfiguration(Type.HTTP,
									new InetSocketAddress(TestProperties.FORWARD_HOST, TestProperties.FORWARD_PORT)),
							false);

					response = client
							.sendRequest(HttpRequest.request().withMethod("POST").withHeaders(httpRequest.getHeaders())
									.withPath("/api/v2/canister/rrkah-fqaaa-aaaaa-aaaaq-cai/call").withBody(request))
							.get().getBodyAsRawBytes();
				}

				return HttpResponse.response().withStatusCode(HttpStatusCode.OK_200.code()).withBody(response);

			}
			{
				return HttpResponse.notFoundResponse();
			}
		});
	}

}
