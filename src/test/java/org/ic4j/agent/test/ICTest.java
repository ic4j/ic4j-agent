package org.ic4j.agent.test;


import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Files;
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ic4j.agent.Agent;
import org.ic4j.agent.AgentBuilder;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.FuncProxy;
import org.ic4j.agent.Response;
import org.ic4j.agent.ServiceProxy;
import org.ic4j.agent.NonceFactory;
import org.ic4j.agent.ProxyBuilder;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.Request;
import org.ic4j.agent.RequestStatusResponse;
import org.ic4j.agent.UpdateBuilder;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.http.ReplicaOkHttpTransport;
import org.ic4j.agent.identity.BasicIdentity;
import org.ic4j.agent.identity.Identity;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.candid.dom.DOMDeserializer;
import org.ic4j.candid.dom.DOMSerializer;
import org.ic4j.candid.dom.DOMUtils;
import org.ic4j.candid.jackson.JacksonDeserializer;
import org.ic4j.candid.jackson.JacksonSerializer;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLType;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.candid.pojo.PojoDeserializer;
import org.ic4j.candid.pojo.PojoSerializer;
import org.ic4j.candid.types.Label;
import org.ic4j.candid.types.Mode;
import org.ic4j.candid.types.Type;
import org.ic4j.types.Func;
import org.ic4j.types.Principal;
import org.ic4j.types.Service;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ICTest {
	static final Logger LOG = LoggerFactory.getLogger(ICTest.class);
	
	static final String SIMPLE_JSON_NODE_FILE = "SimpleNode.json";
	static final String SIMPLE_JSON_ARRAY_NODE_FILE = "SimpleArrayNode.json";	
	static final String SIMPLE_DOM_NODE_FILE = "SimpleNode.xml";
	static final String SIMPLE_DOM_ARRAY_NODE_FILE = "ComplexNode.xml";	
	static final String LOAN_APPLICATION_NODE_FILE = "LoanApplication.xml";
	static final String IC_TEST_IDL_FILE = "ICTest.did";
	
	
	// Instantiate the Factory
	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();	

	ObjectMapper mapper = new ObjectMapper();

	@Test
	public void test() {
		dbf.setNamespaceAware(true);
		dbf.setIgnoringElementContentWhitespace(true);
		
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
				String idlFile = agent.getIDL(Principal.fromString(TestProperties.IC_CANISTER_ID));
				
				Service service = new Service(Principal.fromString(TestProperties.IC_CANISTER_ID));
				
				ProxyBuilder proxyBuilder = ProxyBuilder.create(agent).loadIDL(true);
				
				ServiceProxy serviceProxy = proxyBuilder.getServiceProxy(service);
				
				Func funcValue = new Func("peek");
				
				FuncProxy<String> funcProxy = serviceProxy.getFuncProxy(funcValue);
				
				String peek = funcProxy.call("Motoko", BigInteger.valueOf(100));
				
				Assertions.assertEquals("Hello, Motoko!",peek);
				
				funcValue = new Func("greet");
				
				FuncProxy<String> greetFuncProxy = serviceProxy.getFuncProxy(funcValue);
				
				String greetResponse = greetFuncProxy.call("Motoko");
				
				Assertions.assertEquals("Hello, Motoko!",greetResponse);
				
				funcValue = new Func(Principal.fromString(TestProperties.IC_CANISTER_ID), "greet");
				
				greetFuncProxy = proxyBuilder.getFuncProxy(funcValue);
				
				greetResponse = greetFuncProxy.call("Motoko");
				
				Assertions.assertEquals("Hello, Motoko!",greetResponse);
				
				Pojo pojoValue = new Pojo();
				
				pojoValue.bar = new Boolean(false);
				pojoValue.foo = BigInteger.valueOf(43); 
				
				ComplexPojo complexPojoValue = new ComplexPojo();
				complexPojoValue.bar = new Boolean(true);
				complexPojoValue.foo = BigInteger.valueOf(42);
				
				complexPojoValue.pojo = pojoValue;
				
				funcValue = new Func(Principal.fromString(TestProperties.IC_CANISTER_ID), "echoComplexPojo");
				
				FuncProxy<ComplexPojo> complexPojoFuncProxy = serviceProxy.getFuncProxy(funcValue);
				
				complexPojoFuncProxy.setResponseClass(ComplexPojo.class);
				
				ComplexPojo complexPojoResponse = complexPojoFuncProxy.call(complexPojoValue);
				
				Assertions.assertEquals(complexPojoValue, complexPojoResponse);
				
				JsonNode jsonValue = readJacksonNode(SIMPLE_JSON_NODE_FILE);
				
				funcValue = new Func(Principal.fromString(TestProperties.IC_CANISTER_ID), "echoPojo");
				
				FuncProxy<JsonNode> jacksonPojoFuncProxy = serviceProxy.getFuncProxy(funcValue);
				
				jacksonPojoFuncProxy.setSerializers(new JacksonSerializer());
				jacksonPojoFuncProxy.setDeserializer(new JacksonDeserializer());
				
				jacksonPojoFuncProxy.setResponseClass(JsonNode.class);
				
				JsonNode jsonResult = jacksonPojoFuncProxy.call(jsonValue);
				
				JSONAssert.assertEquals(jsonValue.asText(), jsonResult.asText(), JSONCompareMode.LENIENT);
				
				Node domValue = this.readDOMNode(SIMPLE_DOM_NODE_FILE);
				
				FuncProxy<Node> domPojoFuncProxy = serviceProxy.getFuncProxy(funcValue);
				
				DOMDeserializer domDeserializer = DOMDeserializer.create()
						.rootElement("http://ic4j.org/candid/test", "pojo")
						.setAttributes(true);
				
				domPojoFuncProxy.setSerializers(new DOMSerializer());
				domPojoFuncProxy.setDeserializer(domDeserializer);
				
				domPojoFuncProxy.setResponseClass(Node.class);
				
				Node domResult = domPojoFuncProxy.call(domValue);
					
				HelloProxy helloProxy = proxyBuilder.getProxy(service, HelloProxy.class);
				
				funcValue = new Func(Principal.fromString("w7x7r-cok77-xa"),"a");
				
				Func funcResult = helloProxy.echoFunc(funcValue);								
				
				Assertions.assertEquals(funcValue,funcResult);
				
				funcValue = new Func(Principal.fromString(TestProperties.IC_CANISTER_ID),"peek");
				
				funcProxy = proxyBuilder.getFuncProxy(funcValue, HelloProxy.class);
				
				peek = funcProxy.call("Motoko", BigInteger.valueOf(100));
				
				Assertions.assertEquals("Hello, Motoko!",peek);

				List<IDLValue> args = new ArrayList<IDLValue>();

				String stringValue = "x";

				args.add(IDLValue.create(stringValue));

				args.add(IDLValue.create(new BigInteger("1")));

				IDLArgs idlArgs = IDLArgs.create(args);

				byte[] buf = idlArgs.toBytes();

				CompletableFuture<byte[]> queryResponse = agent.queryRaw(
						Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "peek", buf, Optional.empty());
				
				Path idlPath = Paths.get(getClass().getClassLoader().getResource(IC_TEST_IDL_FILE).getPath());
				
				proxyBuilder = ProxyBuilder.create(agent).idlFile(idlPath);
			
				funcValue = new Func(Principal.fromString(TestProperties.IC_CANISTER_ID),"peek");
				
				funcProxy = proxyBuilder.getFuncProxy(funcValue);
				
				peek = funcProxy.call("Motoko", BigInteger.valueOf(100));
				
				Assertions.assertEquals("Hello, Motoko!",peek);

				try {
					byte[] queryOutput = queryResponse.get();

					IDLArgs outArgs = IDLArgs.fromBytes(queryOutput);

					LOG.info(outArgs.getArgs().get(0).getValue());
					Assertions.assertEquals("Hello, " + stringValue + "!", outArgs.getArgs().get(0).getValue());
				} catch (Throwable ex) {
					LOG.debug(ex.getLocalizedMessage(), ex);
					Assertions.fail(ex.getLocalizedMessage());
				}								
				
				args = new ArrayList<IDLValue>();

				Principal principalValue = Principal.fromString("aapvz-eiaaa-aaaaa-aadia-cai");

				args.add(IDLValue.create(principalValue));


				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();

				queryResponse = agent.queryRaw(
						Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "echoPrincipal", buf, Optional.empty());

				try {
					byte[] queryOutput = queryResponse.get();

					IDLArgs outArgs = IDLArgs.fromBytes(queryOutput);
					
					Principal principalResponse = outArgs.getArgs().get(0).getValue();

					LOG.info(principalResponse.toString());
					Assertions.assertEquals(principalValue, principalResponse);
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
				
				//test with headers
				
				Request<byte[]> queryAgentRequest = new Request<byte[]>(buf);
				CompletableFuture<Response<byte[]>> queryAgentResponse = agent.queryRaw(Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "echoRecord", queryAgentRequest, Optional.empty());

				try {
					byte[] queryOutput = queryAgentResponse.get().getPayload();

					IDLType[] idlTypes = { idlValue.getIDLType() };

					IDLArgs outArgs = IDLArgs.fromBytes(queryOutput, idlTypes);

					LOG.info(outArgs.getArgs().get(0).getValue().toString());
					Assertions.assertEquals(mapValue, outArgs.getArgs().get(0).getValue());
					
					Map<String,String> headers = queryAgentResponse.get().getHeaders();
					
					for(String name : headers.keySet())
					{
						LOG.info("Header " + name + ":" + headers.get(name));
					}
								
					// IC not returning IC headers
					//Assertions.assertTrue(headers.containsKey(Response.X_IC_CANISTER_ID_HEADER));
					//Assertions.assertTrue(headers.containsKey(Response.X_IC_NODE_ID_HEADER));
					//Assertions.assertTrue(headers.containsKey(Response.X_IC_SUBNET_ID_HEADER));
					Assertions.assertTrue(headers.containsKey("content-type"));
					
					Assertions.assertEquals(headers.get("content-type"),"application/cbor");								
				} catch (Throwable ex) {
					LOG.debug(ex.getLocalizedMessage(), ex);
					Assertions.fail(ex.getLocalizedMessage());
				}
				
				// Variant
				mapValue = new HashMap<Label, Object>();

				mapValue.put(Label.createNamedLabel("Ok"), principalValue);

				args = new ArrayList<IDLValue>();

				idlValue = IDLValue.create(mapValue, Type.VARIANT);

				args.add(idlValue);

				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();

				queryResponse = agent.queryRaw(Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "echoVariant", buf, Optional.empty());

				try {
					byte[] queryOutput = queryResponse.get();

					IDLArgs outArgs = IDLArgs.fromBytes(queryOutput);
					
					Map<Label, Object> mapResult = outArgs.getArgs().get(0).getValue();
					
					if(mapResult.containsKey(Label.createNamedLabel("Ok")))
					{
						Principal principalResult = (Principal) mapResult.get(Label.createNamedLabel("Ok"));
						LOG.info(principalResult.toString());
						Assertions.assertEquals(principalValue, principalResult);
					}
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
				
				
				// test with headers
				
				Request<byte[]> updateAgentRequest = new Request<byte[]>(buf);
				
				CompletableFuture<Response<RequestId>> agentUpdateResponse = agent.updateRaw(
						Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "greet", updateAgentRequest, ingressExpiryDatetime);

				requestId = agentUpdateResponse.get().getPayload();
				
				Map<String,String> headers = agentUpdateResponse.get().getHeaders();
				
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_CANISTER_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_NODE_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_SUBNET_ID_HEADER));
				Assertions.assertTrue(headers.containsKey("content-length"));
				
				Assertions.assertEquals(headers.get("content-length"),"0");				

				LOG.debug("Request Id:" + requestId.toHexString());

				TimeUnit.SECONDS.sleep(20);
				
				Request<Void> statusAgentRequest = new Request<Void>(null);

				CompletableFuture<Response<RequestStatusResponse>> agentStatusResponse = agent.requestStatusRaw(requestId,
						Principal.fromString(TestProperties.IC_CANISTER_ID), statusAgentRequest);

				requestStatusResponse = agentStatusResponse.get().getPayload();

				LOG.debug(requestStatusResponse.status.toString());

				Assertions.assertEquals(RequestStatusResponse.REPLIED_STATUS_VALUE,requestStatusResponse.status.toString());

				output = requestStatusResponse.replied.get().arg;

				outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());

				Assertions.assertEquals(outArgs.getArgs().get(0).getValue().toString(), "Hello, " + value + "!");
				
				headers = agentStatusResponse.get().getHeaders();
				
				for(String name : headers.keySet())
				{
					LOG.info("Header " + name + ":" + headers.get(name));
				}
				
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_CANISTER_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_NODE_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_SUBNET_ID_HEADER));
				Assertions.assertTrue(headers.containsKey("content-type"));
				
				Assertions.assertEquals(headers.get("content-type"),"application/cbor");				
				
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
				
				Assertions.assertEquals("Hello, " + stringValue + "!", outArgs.getArgs().get(0).getValue());
				
				// test with header
				updateBuilder = UpdateBuilder
						.create(agent, Principal.fromString(TestProperties.IC_CANISTER_ID), "greet").arg(buf);

				CompletableFuture<RequestId> builderResponseWithHeader = updateBuilder.call();
					
				Response<byte[]> responseWithHeader = updateBuilder.getState(builderResponseWithHeader.get(), null, org.ic4j.agent.Waiter.create(60, 5)).get();		

				output = responseWithHeader.getPayload();
				outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());
				
				Assertions.assertEquals("Hello, " + stringValue + "!", outArgs.getArgs().get(0).getValue());
				
				headers = responseWithHeader.getHeaders();
				
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_CANISTER_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_NODE_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_SUBNET_ID_HEADER));
				Assertions.assertTrue(headers.containsKey("content-type"));
				
				Assertions.assertEquals(headers.get("content-type"),"application/cbor");

				HelloProxy hello = ProxyBuilder.create(agent, Principal.fromString(TestProperties.IC_CANISTER_ID))
						.getProxy(HelloProxy.class);

				CompletableFuture<String> proxyResponse = hello.greet(value);
				

				LOG.info(proxyResponse.get());
				Assertions.assertEquals(proxyResponse.get(), "Hello, " + value + "!");
				
				//test with headers

				Response<byte[]> proxyResponseWithHeader = hello.greetWithHeader(value).get();
				
				output = proxyResponseWithHeader.getPayload();
				
				outArgs = IDLArgs.fromBytes(output);

				LOG.info(outArgs.getArgs().get(0).getValue().toString());

				Assertions.assertEquals(outArgs.getArgs().get(0).getValue().toString(), "Hello, " + value + "!");	
				
				headers = proxyResponseWithHeader.getHeaders();
				
				
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_CANISTER_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_NODE_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_SUBNET_ID_HEADER));
				Assertions.assertTrue(headers.containsKey("content-type"));
				
				Assertions.assertEquals(headers.get("content-type"),"application/cbor");

				BigInteger intValue = new BigInteger("10000");

				String result = hello.peek(value, intValue);
				
				CompletableFuture<Void> noReturn = hello.noReturn("Motoko");
				
				noReturn.get();

				LOG.info(result);
				Assertions.assertEquals("Hello, " + value + "!", result);
				
				pojoValue = new Pojo();
				
				pojoValue.bar = new Boolean(false);
				pojoValue.foo = BigInteger.valueOf(43); 
				
				complexPojoValue = new ComplexPojo();
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
				
				CompletableFuture<ComplexPojo> complexProxyResponse = hello.updateComplexPojo(complexPojoValue);
				
				complexPojoResult = complexProxyResponse.get();
				
				Assertions.assertEquals(complexPojoValue,complexPojoResult);
				
				ComplexPojo complexPojoValue2 = new ComplexPojo();
				complexPojoValue2.bar = new Boolean(true);
				complexPojoValue2.foo = BigInteger.valueOf(44);	
				
				complexPojoValue2.pojo = pojoValue;
				
				ComplexPojo[] complexPojoArrayValue = {complexPojoValue,complexPojoValue2};			
				
				idlValue = IDLValue.create(complexPojoArrayValue, new PojoSerializer());

				args = new ArrayList<IDLValue>();
				args.add(idlValue);

				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();
				
				ComplexPojo[] complexPojoArrayResult = hello.echoComplexPojo( complexPojoArrayValue);
				
				Assertions.assertArrayEquals(complexPojoArrayValue, complexPojoArrayResult);
				
				proxyResponseWithHeader = hello.echoComplexPojoWithHeader(complexPojoArrayValue);
				
				output = proxyResponseWithHeader.getPayload();
				
				outArgs = IDLArgs.fromBytes(output);
				
				complexPojoArrayResult = outArgs.getArgs().get(0)
						.getValue(new PojoDeserializer(), ComplexPojo[].class);
				
				Assertions.assertArrayEquals(complexPojoArrayValue, complexPojoArrayResult);
				
				headers = proxyResponseWithHeader.getHeaders();
				
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_CANISTER_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_NODE_ID_HEADER));
				//Assertions.assertTrue(headers.containsKey(Response.X_IC_SUBNET_ID_HEADER));
				Assertions.assertTrue(headers.containsKey("content-type"));
				
				Assertions.assertEquals(headers.get("content-type"),"application/cbor");	
				
				// test functions
				

				
				List<IDLType> funcArgs = new ArrayList<IDLType>();
				List<IDLType> funcRets = new ArrayList<IDLType>();
				List<Mode> funcModes = new ArrayList<Mode>();
				
				
				funcArgs = new ArrayList<IDLType>();
				funcRets = new ArrayList<IDLType>();
				funcModes = new ArrayList<Mode>();
				
				idlValue = IDLValue.create(funcValue, IDLType.createType(funcArgs,funcRets,funcModes));
				
				args = new ArrayList<IDLValue>();

				args.add(idlValue);
				
				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();
				
				queryResponse = agent.queryRaw(Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "echoFunc0", buf, Optional.empty());
				
				try {
					byte[] queryOutput = queryResponse.get();
					
					Assertions.assertArrayEquals(buf, queryOutput);		

					outArgs = IDLArgs.fromBytes(queryOutput);
					
					funcResult = outArgs.getArgs().get(0).getValue();				

					Assertions.assertEquals(funcValue, funcResult);

				} catch (Throwable ex) {
					LOG.debug(ex.getLocalizedMessage(), ex);
					Assertions.fail(ex.getLocalizedMessage());
				}	
				
				funcArgs.add(IDLType.createType(Type.TEXT));
				funcRets.add(IDLType.createType(Type.TEXT));
				
				idlValue = IDLValue.create(funcValue, IDLType.createType(funcArgs,funcRets,funcModes));
				
				args = new ArrayList<IDLValue>();

				args.add(idlValue);
				
				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();
				
				queryResponse = agent.queryRaw(Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "echoFunc1", buf, Optional.empty());
				
				try {
					byte[] queryOutput = queryResponse.get();
					
					Assertions.assertArrayEquals(buf, queryOutput);	

					outArgs = IDLArgs.fromBytes(queryOutput);
					
					funcResult = outArgs.getArgs().get(0).getValue();
					
					Assertions.assertEquals(funcValue, funcResult);

				} catch (Throwable ex) {
					LOG.debug(ex.getLocalizedMessage(), ex);
					Assertions.fail(ex.getLocalizedMessage());
				}
				
				funcRets = new ArrayList<IDLType>();
				funcModes.add(Mode.ONEWAY);
				
				idlValue = IDLValue.create(funcValue, IDLType.createType(funcArgs,funcRets,funcModes));
				
				args = new ArrayList<IDLValue>();

				args.add(idlValue);
				
				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();
				
				queryResponse = agent.queryRaw(Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "echoFunc3", buf, Optional.empty());
				
				try {
					byte[] queryOutput = queryResponse.get();
					
					Assertions.assertArrayEquals(buf, queryOutput);	

					outArgs = IDLArgs.fromBytes(queryOutput);
					
					funcResult = outArgs.getArgs().get(0).getValue();
					
					Assertions.assertEquals(funcValue, funcResult);

				} catch (Throwable ex) {
					LOG.debug(ex.getLocalizedMessage(), ex);
					Assertions.fail(ex.getLocalizedMessage());
				}				
				
				funcArgs.add(IDLType.createType(Type.BOOL));
				
				idlValue = IDLValue.create(funcValue, IDLType.createType(funcArgs,funcRets,funcModes));
				
				args = new ArrayList<IDLValue>();

				args.add(idlValue);
				
				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();
				
				queryResponse = agent.queryRaw(Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "echoFunc2", buf, Optional.empty());
				
				try {
					byte[] queryOutput = queryResponse.get();
					
					Assertions.assertArrayEquals(buf, queryOutput);	

					outArgs = IDLArgs.fromBytes(queryOutput);
					
					funcResult = outArgs.getArgs().get(0).getValue();
					
					Assertions.assertEquals(funcValue, funcResult);

				} catch (Throwable ex) {
					LOG.debug(ex.getLocalizedMessage(), ex);
					Assertions.fail(ex.getLocalizedMessage());
				}				

				funcArgs = new ArrayList<IDLType>();
				funcRets = new ArrayList<IDLType>();
				funcModes = new ArrayList<Mode>();
				
				funcRets.add(IDLType.createType(Type.TEXT));
				funcModes.add(Mode.QUERY);
				
				idlValue = IDLValue.create(funcValue, IDLType.createType(funcArgs,funcRets,funcModes));
				
				args = new ArrayList<IDLValue>();

				args.add(idlValue);
				
				idlArgs = IDLArgs.create(args);

				buf = idlArgs.toBytes();
				
				queryResponse = agent.queryRaw(Principal.fromString(TestProperties.IC_CANISTER_ID),
						Principal.fromString(TestProperties.IC_CANISTER_ID), "echoFunc4", buf, Optional.empty());
				
				try {
					byte[] queryOutput = queryResponse.get();
					
					Assertions.assertArrayEquals(buf, queryOutput);		

					outArgs = IDLArgs.fromBytes(queryOutput);
					
					funcResult = outArgs.getArgs().get(0).getValue();
					
					Assertions.assertEquals(funcValue, funcResult);

				} catch (Throwable ex) {
					LOG.debug(ex.getLocalizedMessage(), ex);
					Assertions.fail(ex.getLocalizedMessage());
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

		}

	}
	
	JsonNode readJacksonNode(String fileName) throws JsonProcessingException, IOException {
		byte[] input = Files.readAllBytes(Paths.get(getClass().getClassLoader().getResource(fileName).getPath()));

		JsonNode rootNode = (JsonNode) mapper.readTree(input);

		return rootNode;
	}
	
	Node readDOMNode(String fileName) throws SAXException, IOException, ParserConfigurationException {
		// parse XML file
		DocumentBuilder db = dbf.newDocumentBuilder();

		Document doc = db.parse(getClass().getClassLoader().getResource(fileName).getFile());

		try {
			String domString = DOMUtils.getStringFromDocument(doc);
		} catch (TransformerException e) {

		}
		return doc.getDocumentElement();
	}	

}
