package org.ic4j.agent.test;


import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLType;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.candid.types.Label;
import org.ic4j.types.Principal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ICTest {
	static final Logger LOG = LoggerFactory.getLogger(ICTest.class);

	@Test
	public void test() {
		ReplicaTransport transport;

		try {
			Security.addProvider(new BouncyCastleProvider());

			KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

			Identity identity = BasicIdentity.fromKeyPair(keyPair);

			// Test also SECP256k Identity
			Path path = Paths
					.get(getClass().getClassLoader().getResource(TestProperties.SECP256K1_IDENTITY_FILE).getPath());

			// identity = Secp256k1Identity.fromPEMFile(path);

			String transportType = TestProperties.TRANSPORT_TYPE;

			switch (transportType) {
			case "http.ok":
				transport = ReplicaOkHttpTransport.create(TestProperties.IC_URL);
				break;
			default:
				transport = ReplicaApacheHttpTransport.create(TestProperties.IC_URL);
				break;
			}

			Agent agent = new AgentBuilder().transport(transport).identity(identity).nonceFactory(new NonceFactory())
					.build();

			try {

				List<IDLValue> args = new ArrayList<IDLValue>();

				String stringValue = "x";

				args.add(IDLValue.create(stringValue));

				args.add(IDLValue.create(new BigInteger("1")));

				IDLArgs idlArgs = IDLArgs.create(args);

				byte[] buf = idlArgs.toBytes();

				CompletableFuture<byte[]> queryResponse = agent.queryRaw(
						Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "peek", buf, Optional.empty());

				try {
					byte[] queryOutput = queryResponse.get();

					IDLArgs outArgs = IDLArgs.fromBytes(queryOutput);

					LOG.info(outArgs.getArgs().get(0).getValue());
					Assertions.assertEquals("Hello, " + stringValue + "!", outArgs.getArgs().get(0).getValue());
				} catch (Throwable ex) {
					LOG.debug(ex.getLocalizedMessage(), ex);
					Assertions.fail(ex.getLocalizedMessage());
				}

				// Record
				Map<Label, Object> mapValue = new HashMap<Label, Object>();

				mapValue.put(Label.createNamedLabel("bar"), new Boolean(true));

				mapValue.put(Label.createNamedLabel("foo"), BigInteger.valueOf(42));

				args = new ArrayList<IDLValue>();

				IDLValue idlValue = IDLValue.create(mapValue);

				args.add(idlValue);

				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();

				queryResponse = agent.queryRaw(Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "echoRecord", buf, Optional.empty());

				try {
					byte[] queryOutput = queryResponse.get();

					IDLType[] idlTypes = { idlValue.getIDLType() };

					IDLArgs outArgs = IDLArgs.fromBytes(queryOutput, idlTypes);

					LOG.info(outArgs.getArgs().get(0).getValue().toString());
					Assertions.assertEquals(mapValue, outArgs.getArgs().get(0).getValue());
				} catch (Throwable ex) {
					LOG.debug(ex.getLocalizedMessage(), ex);
					Assertions.fail(ex.getLocalizedMessage());
				}

				// test String argument
				args = new ArrayList<IDLValue>();

				String value = "x";

				args.add(IDLValue.create(new String(value)));

				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();

				Optional<Long> ingressExpiryDatetime = Optional.empty();
				// ingressExpiryDatetime =
				// Optional.of(Long.parseUnsignedLong("1623389588095477000"));

				CompletableFuture<RequestId> response = agent.updateRaw(
						Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "greet", buf, ingressExpiryDatetime);

				RequestId requestId = response.get();

				LOG.debug("Request Id:" + requestId.toHexString());

				TimeUnit.SECONDS.sleep(10);

				CompletableFuture<RequestStatusResponse> statusResponse = agent.requestStatusRaw(requestId,
						Principal.fromString(TestProperties.IC_CANISTER_ID));

				RequestStatusResponse requestStatusResponse = statusResponse.get();

				LOG.debug(requestStatusResponse.status.toString());

				Assertions.assertEquals(requestStatusResponse.status.toString(),
						RequestStatusResponse.REPLIED_STATUS_VALUE);

				byte[] output = requestStatusResponse.replied.get().arg;

				IDLArgs outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());

				Assertions.assertEquals(outArgs.getArgs().get(0).getValue().toString(), "Hello, " + value + "!");

				args = new ArrayList<IDLValue>();

				args.add(IDLValue.create(new String(stringValue)));

				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();

				UpdateBuilder updateBuilder = UpdateBuilder
						.create(agent, Principal.fromString(TestProperties.IC_CANISTER_ID), "greet").arg(buf);

				CompletableFuture<byte[]> builderResponse = updateBuilder
						.callAndWait(org.ic4j.agent.Waiter.create(60, 5));

				output = builderResponse.get();
				outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());

				HelloProxy hello = ProxyBuilder.create(agent, Principal.fromString(TestProperties.IC_CANISTER_ID))
						.getProxy(HelloProxy.class);

				CompletableFuture<String> proxyResponse = hello.greet(value);

				LOG.info(proxyResponse.get());
				Assertions.assertEquals(proxyResponse.get(), "Hello, " + value + "!");

				BigInteger intValue = new BigInteger("10000");

				String result = hello.peek(value, intValue);

				LOG.info(result);
				Assertions.assertEquals("Hello, " + value + "!", result);
				
				Pojo pojoValue = new Pojo();
				
				pojoValue.bar = new Boolean(false);
				pojoValue.foo = BigInteger.valueOf(43); 
				
				ComplexPojo complexPojoValue = new ComplexPojo();
				complexPojoValue.bar = new Boolean(true);
				complexPojoValue.foo = BigInteger.valueOf(42);	
				
				complexPojoValue.pojo = pojoValue;
				
				Pojo pojoResult = hello.getPojo(pojoValue);
				Assertions.assertEquals(pojoValue,pojoResult);
				
				pojoResult = hello.echoOptionPojo(Optional.ofNullable(pojoValue));
				Assertions.assertEquals(pojoValue,pojoResult);
				
				pojoResult = hello.subComplexPojo(complexPojoValue);
				Assertions.assertEquals(pojoValue,pojoResult);
				
				ComplexPojo complexPojoResult = hello.echoComplexPojo(complexPojoValue);	
				Assertions.assertEquals(complexPojoValue,complexPojoResult);
				
				ComplexPojo complexPojoValue2 = new ComplexPojo();
				complexPojoValue2.bar = new Boolean(true);
				complexPojoValue2.foo = BigInteger.valueOf(44);	
				
				complexPojoValue2.pojo = pojoValue;
				
				ComplexPojo[] complexPojoArrayValue = {complexPojoValue,complexPojoValue2};
				
				ComplexPojo[] complexPojoArrayResult = hello.echoComplexPojo( complexPojoArrayValue);
				
				Assertions.assertArrayEquals(complexPojoArrayValue, complexPojoArrayValue);

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

		}

	}

}
