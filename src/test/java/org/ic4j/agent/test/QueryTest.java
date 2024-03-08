package org.ic4j.agent.test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.ArrayUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ic4j.agent.Agent;
import org.ic4j.agent.AgentBuilder;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.ProxyBuilder;
import org.ic4j.agent.QueryBuilder;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.http.ReplicaOkHttpTransport;
import org.ic4j.agent.identity.BasicIdentity;
import org.ic4j.agent.identity.Identity;
import org.ic4j.agent.identity.Secp256k1Identity;
import org.ic4j.agent.replicaapi.Envelope;
import org.ic4j.agent.replicaapi.QueryResponse;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLType;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.candid.types.Label;
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
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public class QueryTest extends MockTest {
	static final Logger LOG = LoggerFactory.getLogger(QueryTest.class);

	@Test
	public void test() {
		objectMapper.registerModule(new Jdk8Module());

		try {
			this.runMockServer();

		} catch (IOException e) {
			LOG.error(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());

			return;
		}

		ReplicaTransport transport;
		try {
			Security.addProvider(new BouncyCastleProvider());

			KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

			Identity identity = BasicIdentity.fromKeyPair(keyPair);

			Path path = Paths
					.get(getClass().getClassLoader().getResource(TestProperties.SECP256K1_IDENTITY_FILE).getPath());

			identity = Secp256k1Identity.fromPEMFile(path);

			String transportType = TestProperties.TRANSPORT_TYPE;

			switch (transportType) {
			case "http.ok":
				transport = ReplicaOkHttpTransport.create("http://localhost:" + TestProperties.MOCK_PORT);
				break;
			default:
				transport = ReplicaApacheHttpTransport.create("http://localhost:" + TestProperties.MOCK_PORT);
				break;
			}

			Agent agent = new AgentBuilder().transport(transport).identity(identity).build();

			// test integer argument
			List<IDLValue> args = new ArrayList<IDLValue>();

			BigInteger intValue = new BigInteger("10000");

			args.add(IDLValue.create(intValue));

			IDLArgs idlArgs = IDLArgs.create(args);

			byte[] buf = idlArgs.toBytes();

			Optional<Long> ingressExpiryDatetime = Optional.empty();
			// ingressExpiryDatetime =
			// Optional.of(Long.parseUnsignedLong("1623389588095477000"));

			CompletableFuture<byte[]> response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "echoInt", buf, ingressExpiryDatetime);

			try {
				byte[] output = response.get();

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());
				Assertions.assertEquals(BigInteger.valueOf(intValue.longValue() + 1),
						outArgs.getArgs().get(0).getValue());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}

			args = new ArrayList<IDLValue>();

			String value = "x";

			args.add(IDLValue.create(value));

			args.add(IDLValue.create(new BigInteger("1")));

			idlArgs = IDLArgs.create(args);

			buf = idlArgs.toBytes();

			response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "peek", buf, Optional.empty());

			try {
				byte[] output = response.get();

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue());
				Assertions.assertEquals("Hello, " + value + "!", outArgs.getArgs().get(0).getValue());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}

			// test Boolean argument

			args = new ArrayList<IDLValue>();

			args.add(IDLValue.create(Boolean.valueOf(true)));

			idlArgs = IDLArgs.create(args);

			buf = idlArgs.toBytes();

			response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "echoBool", buf, Optional.empty());

			try {
				byte[] output = response.get();

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());
				Assertions.assertSame(Boolean.TRUE, outArgs.getArgs().get(0).getValue());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}

			// test Double argument

			args = new ArrayList<IDLValue>();

			Double doubleValue = Double.valueOf(42.42);
			args.add(IDLValue.create(doubleValue));

			idlArgs = IDLArgs.create(args);

			buf = idlArgs.toBytes();

			response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "echoFloat", buf, Optional.empty());

			try {
				byte[] output = response.get();

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());
				Assertions.assertEquals(doubleValue + 1, outArgs.getArgs().get(0).getValue());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}

			// test Principal argument

			args = new ArrayList<IDLValue>();

			Principal principalValue = Principal.fromString(TestProperties.CANISTER_ID);
			args.add(IDLValue.create(principalValue));

			idlArgs = IDLArgs.create(args);

			buf = idlArgs.toBytes();

			response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "echoPrincipal", buf, Optional.empty());

			try {
				byte[] output = response.get();

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());
				Assertions.assertEquals(principalValue.toString(), outArgs.getArgs().get(0).getValue().toString());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}

			// test Arrays argument

			args = new ArrayList<IDLValue>();

			BigInteger[] arrayValue = { new BigInteger("10000"), new BigInteger("20000"), new BigInteger("30000") };

			args.add(IDLValue.create(arrayValue));

			idlArgs = IDLArgs.create(args);

			buf = idlArgs.toBytes();

			response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "echoVec", buf, Optional.empty());

			try {
				byte[] output = response.get();

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				BigInteger[] arrayResponse = (BigInteger[]) outArgs.getArgs().get(0).getValue();

				LOG.info(Integer.toString(arrayResponse.length));
				Assertions.assertSame(arrayValue.length, arrayResponse.length);

				Assertions.assertArrayEquals(arrayValue, arrayResponse);

				LOG.info(arrayResponse[0].toString());

				LOG.info(arrayResponse[1].toString());

				LOG.info(arrayResponse[2].toString());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}
			
			// test Binary argument
			try {
				args = new ArrayList<IDLValue>();

				byte[] binaryValue = getBinary(TestProperties.BINARY_IMAGE_FILE, "png");

				args.add(IDLValue.create(binaryValue, IDLType.createType(org.ic4j.candid.types.Type.VEC, IDLType.createType(org.ic4j.candid.types.Type.NAT8))));

				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();

				response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "echoBinary", buf, Optional.empty());

				byte[] output = response.get();

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				Byte[] binaryResponse = (Byte[]) outArgs.getArgs().get(0).getValue();

				LOG.info(Integer.toString(binaryResponse.length));
				Assertions.assertTrue(binaryValue.length == binaryResponse.length);

				Assertions.assertArrayEquals(binaryValue, ArrayUtils.toPrimitive(binaryResponse));

			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}			

			// test Optional argument

			args = new ArrayList<IDLValue>();

			Optional optionalValue = Optional.of(intValue);
			args.add(IDLValue.create(optionalValue));

			idlArgs = IDLArgs.create(args);

			buf = idlArgs.toBytes();

			response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "echoOption", buf, Optional.empty());

			try {
				byte[] output = response.get();

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());
				Assertions.assertEquals(optionalValue, outArgs.getArgs().get(0).getValue());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}

			// test record

			Map<Label, Object> mapValue = new HashMap<Label, Object>();

			mapValue.put(Label.createNamedLabel("bar"), Boolean.valueOf(true));

			mapValue.put(Label.createNamedLabel("foo"), BigInteger.valueOf(42));

			args = new ArrayList<IDLValue>();

			IDLValue idlValue = IDLValue.create(mapValue);

			args.add(idlValue);

			idlArgs = IDLArgs.create(args);

			buf = idlArgs.toBytes();

			response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "echoRecord", buf, Optional.empty());

			try {
				byte[] output = response.get();

				IDLType[] idlTypes = { idlValue.getIDLType() };

				IDLArgs outArgs = IDLArgs.fromBytes(output, idlTypes);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());
				Assertions.assertEquals(mapValue, outArgs.getArgs().get(0).getValue());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}

			// test invalid method name

			response = agent.queryRaw(Principal.fromString(TestProperties.CANISTER_ID),
					Principal.fromString(TestProperties.CANISTER_ID), "hello", buf, Optional.empty());

			try {
				byte[] output = response.get();

				LOG.info(output.toString());
				Assertions.fail(output.toString());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.assertEquals(ex.getCause().getMessage(),
						"The Replica returned an error: code 3, message: \"IC0302: Canister rrkah-fqaaa-aaaaa-aaaaq-cai has no query method 'hello'\"");

			}

			// test QueryBuilder

			args = new ArrayList<IDLValue>();

			args.add(IDLValue.create(intValue));

			idlArgs = IDLArgs.create(args);

			buf = idlArgs.toBytes();

			response = QueryBuilder.create(agent, Principal.fromString(TestProperties.CANISTER_ID), "echoInt")
					.expireAfter(Duration.ofMinutes(3)).arg(buf).call();

			try {
				byte[] output = response.get();

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());
				Assertions.assertEquals(BigInteger.valueOf(intValue.longValue() + 1),
						outArgs.getArgs().get(0).getValue());
			} catch (Throwable ex) {
				LOG.debug(ex.getLocalizedMessage(), ex);
				Assertions.fail(ex.getLocalizedMessage());
			}

			// test ProxyBuilder

			HelloProxy hello = ProxyBuilder.create(agent, Principal.fromString(TestProperties.CANISTER_ID))
					.getProxy(HelloProxy.class);

			String result = hello.peek(value, intValue);

			LOG.info(result);
			Assertions.assertEquals("Hello, " + value + "!", result);

			BigInteger intResult = hello.getInt(intValue);

			LOG.info(intResult.toString());

			Assertions.assertEquals(BigInteger.valueOf(intValue.longValue() + 1), intResult);

			hello.getFloat(doubleValue).whenComplete((output, ex) -> {
				LOG.info(output.toString());

				Assertions.assertEquals(doubleValue, output);
			});
			
			// test Binary argument
			try {
				byte[] binaryValue = getBinary(TestProperties.BINARY_IMAGE_FILE, "png");
				
				BinaryProxy binary = ProxyBuilder.create(agent, Principal.fromString(TestProperties.CANISTER_ID))
						.getProxy(BinaryProxy.class);

				byte[] binaryResponse = binary.echoBinaryPrimitive(binaryValue);
				
				LOG.info(Integer.toString(binaryResponse.length));
				Assertions.assertTrue(binaryValue.length == binaryResponse.length);

				Assertions.assertArrayEquals(binaryValue, binaryResponse);
				
				Byte[] binaryObjectValue = ArrayUtils.toObject(binaryValue);
				
				Byte[] binaryObjectResponse = binary.echoBinaryObject(binaryObjectValue);
				
				LOG.info(Integer.toString(binaryObjectResponse.length));
				Assertions.assertTrue(binaryObjectValue.length == binaryObjectResponse.length);

				Assertions.assertArrayEquals(binaryObjectValue, binaryObjectResponse);				
				
			}catch(Exception ex)
			{
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
	
	static byte[] getBinary(String fileName, String type) throws Exception{
		InputStream binaryInputStream = QueryTest.class.getClassLoader().getResourceAsStream(fileName);

		BufferedImage bImage = ImageIO.read(binaryInputStream);
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ImageIO.write(bImage, type , bos );
		byte [] data = bos.toByteArray();
		 
		return data;
	}

	void runMockServer() throws IOException {

		mockServerClient.when(new HttpRequest().withMethod("POST")
				.withPath("/api/v2/canister/" + TestProperties.CANISTER_ID + "/query")).respond(httpRequest -> {
					if (httpRequest.getPath().getValue()
							.endsWith("/api/v2/canister/" + TestProperties.CANISTER_ID + "/query")) {
						byte[] request = httpRequest.getBodyAsRawBytes();

						JsonNode requestNode = objectMapper.readValue(request, JsonNode.class);

						LOG.debug("Query Request:" + requestNode.toPrettyString());

						ObjectReader objectReader = objectMapper.readerFor(Envelope.class).withAttribute("request_type",
								"query");

						Envelope<Map> envelope = objectReader.readValue(request, Envelope.class);

						String methodName = (String) ((Map) envelope.content).get("method_name");

						LOG.debug("Method Name: " + methodName);

						String responseFileName = null;

						switch (methodName) {
						case "echoBool":
							responseFileName = TestProperties.CBOR_ECHOBOOL_QUERY_RESPONSE_FILE;
							break;
						case "echoInt":
							responseFileName = TestProperties.CBOR_ECHOINT_QUERY_RESPONSE_FILE;
							break;
						case "echoFloat":
							responseFileName = TestProperties.CBOR_ECHOFLOAT_QUERY_RESPONSE_FILE;
							break;
						case "echoOption":
							responseFileName = TestProperties.CBOR_ECHOOPT_QUERY_RESPONSE_FILE;
							break;
						case "echoVec":
							responseFileName = TestProperties.CBOR_ECHOVEC_QUERY_RESPONSE_FILE;
							break;
						case "echoBinary":
							responseFileName = TestProperties.CBOR_ECHOBINARY_QUERY_RESPONSE_FILE;
							break;							
						case "echoRecord":
							responseFileName = TestProperties.CBOR_ECHORECORD_QUERY_RESPONSE_FILE;
							break;
						case "echoPrincipal":
							responseFileName = TestProperties.CBOR_ECHOPRINCIPAL_QUERY_RESPONSE_FILE;
							break;
						case "peek":
							responseFileName = TestProperties.CBOR_PEEK_QUERY_RESPONSE_FILE;
							break;
						case "hello":
							responseFileName = TestProperties.CBOR_HELLO_QUERY_RESPONSE_FILE;
							break;
						}

						byte[] response = {};

						LOG.debug("Response File Name: " + responseFileName);

						if (responseFileName != null) {
							LOG.debug(Paths.get(getClass().getClassLoader().getResource(responseFileName).getPath())
									.toString());

							response = Files.readAllBytes(
									Paths.get(getClass().getClassLoader().getResource(responseFileName).getPath()));
						} else
							LOG.debug("Unknown Method " + methodName);

						if (TestProperties.FORWARD) {
							NettyHttpClient client = new NettyHttpClient(null, clientEventLoopGroup,
									ProxyConfiguration.proxyConfiguration(Type.HTTP, new InetSocketAddress(
											TestProperties.FORWARD_HOST, TestProperties.FORWARD_PORT)),
									false);

							response = client.sendRequest(
									HttpRequest.request().withMethod("POST").withHeaders(httpRequest.getHeaders())
											.withPath("/api/v2/canister/" + TestProperties.CANISTER_ID + "/query")
											.withBody(request))
									.get().getBodyAsRawBytes();

							if (TestProperties.STORE)
								Files.write(Paths.get(TestProperties.STORE_PATH + File.separator + "cbor." + methodName
										+ ".query.response"), response);
						}

						JsonNode responseNode = objectMapper.readValue(response, JsonNode.class);

						LOG.debug(responseNode.toPrettyString());

						QueryResponse queryResponse = objectMapper.readValue(response, QueryResponse.class);

						return HttpResponse.response().withStatusCode(HttpStatusCode.OK_200.code())
								.withContentType(MediaType.create("application", "cbor")).withBody(response);

					} else {
						return HttpResponse.notFoundResponse();
					}
				});
	}

}
