package org.ic4j.agent.test;

import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.ic4j.agent.Agent;
import org.ic4j.agent.AgentBuilder;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.FuncProxy;
import org.ic4j.agent.Request;
import org.ic4j.agent.Response;
import org.ic4j.agent.NonceFactory;
import org.ic4j.agent.ProxyBuilder;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.http.ReplicaOkHttpTransport;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.candid.pojo.PojoDeserializer;
import org.ic4j.candid.pojo.PojoSerializer;
import org.ic4j.types.Func;
import org.ic4j.types.Principal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoanTest {
	static final Logger LOG = LoggerFactory.getLogger(LoanTest.class);

	@Test
	public void test() {
	ReplicaTransport transport;

	try {
		String transportType = TestProperties.TRANSPORT_TYPE;

		switch (transportType) {
		case "http.ok":
			transport = ReplicaOkHttpTransport.create(TestProperties.LOAN_URL);
			break;
		default:
			transport = ReplicaApacheHttpTransport.create(TestProperties.LOAN_URL);
			break;
		}
		
		

		Agent agent = new AgentBuilder().transport(transport).nonceFactory(new NonceFactory())
				.build();
		
		boolean local = false;
		if(local)
			agent.fetchRootKey();
				
		
		// Get Loan Offer Request	

		List<IDLValue> args = new ArrayList<IDLValue>();

		IDLArgs idlArgs = IDLArgs.create(args);

		byte[] buf = idlArgs.toBytes();

		CompletableFuture<byte[]> queryResponse = agent.queryRaw(
				Principal.fromString(TestProperties.LOAN_CANISTER_ID),
				Principal.fromString(TestProperties.LOAN_CANISTER_ID), "getOffer", buf, Optional.empty());

		try {
			byte[] queryOutput = queryResponse.get();

			LoanOffer loanResult = IDLArgs.fromBytes(queryOutput).getArgs().get(0).getValue(new PojoDeserializer(), LoanOffer.class);

			long millis = TimeUnit.MILLISECONDS.convert(loanResult.created, TimeUnit.NANOSECONDS); 

			Date date = new Date(millis);
			
			Assertions.assertEquals(1, loanResult.applicationId);
			Assertions.assertEquals(3.14, loanResult.apr);
			Assertions.assertEquals("Loan Provider", loanResult.providerName);

		} catch (Throwable ex) {
			LOG.debug(ex.getLocalizedMessage(), ex);
			Assertions.fail(ex.getLocalizedMessage());
		}
		
		Request<byte[]> queryAgentRequest = new Request<byte[]>(buf);
		CompletableFuture<Response<byte[]>> queryAgentResponse = agent.queryRaw(
				Principal.fromString(TestProperties.LOAN_CANISTER_ID),
				Principal.fromString(TestProperties.LOAN_CANISTER_ID), "getOffer", queryAgentRequest, Optional.empty());

		try {
			byte[] queryOutput = queryAgentResponse.get().getPayload();

			LoanOffer loanResult = IDLArgs.fromBytes(queryOutput).getArgs().get(0).getValue(new PojoDeserializer(), LoanOffer.class);

			long millis = TimeUnit.MILLISECONDS.convert(loanResult.created, TimeUnit.NANOSECONDS); 

			Date date = new Date(millis);
			
			Assertions.assertEquals(1, loanResult.applicationId);
			Assertions.assertEquals(3.14, loanResult.apr);
			Assertions.assertEquals("Loan Provider", loanResult.providerName);
			
			Map<String,String> headers = queryAgentResponse.get().getHeaders();
			
			for(String name : headers.keySet())
			{
				LOG.info("Header " + name + ":" + headers.get(name));
			}
			
			//Assertions.assertTrue(headers.containsKey(Response.X_IC_CANISTER_ID_HEADER));
			//Assertions.assertTrue(headers.containsKey(Response.X_IC_NODE_ID_HEADER));
			//Assertions.assertTrue(headers.containsKey(Response.X_IC_SUBNET_ID_HEADER));
			Assertions.assertTrue(headers.containsKey("content-type"));
			
			Assertions.assertEquals(headers.get("content-type"),"application/cbor");			

		} catch (Throwable ex) {
			LOG.debug(ex.getLocalizedMessage(), ex);
			Assertions.fail(ex.getLocalizedMessage());
		}		
		
		// Loan Offer Request	
		
		LoanOfferRequest loanRequest = new LoanOfferRequest();
		
		loanRequest.userId = Principal.fromString("ubgwl-msd3g-gr5yh-cwpic-elony-lnexo-5f3wf-atisx-hxeyt-ffmfu-tqe");
		loanRequest.amount = (double) 20000.00;
		loanRequest.applicationId = new BigInteger("11");
		loanRequest.term = 48;
		loanRequest.rating = 670;
		loanRequest.zipcode = "95134";
		loanRequest.created = new BigInteger("0");
		
		LoanOfferRequest[] loanRequestArray = {loanRequest};

		IDLValue idlValue = IDLValue.create(loanRequestArray, new PojoSerializer());

		args = new ArrayList<IDLValue>();
		args.add(idlValue);

		idlArgs = IDLArgs.create(args);

		buf = idlArgs.toBytes();

		queryResponse = agent.queryRaw(
				Principal.fromString(TestProperties.LOAN_CANISTER_ID),
				Principal.fromString(TestProperties.LOAN_CANISTER_ID), "echoOfferRequests", buf, Optional.empty());

		try {
			byte[] queryOutput = queryResponse.get();

			LoanOfferRequest[] loanRequestArrayResult = IDLArgs.fromBytes(queryOutput).getArgs().get(0).getValue(new PojoDeserializer(), LoanOfferRequest[].class);
			
			Assertions.assertArrayEquals(loanRequestArray, loanRequestArrayResult);

		} catch (Throwable ex) {
			LOG.debug(ex.getLocalizedMessage(), ex);
			Assertions.fail(ex.getLocalizedMessage());
		}
		
		args = new ArrayList<IDLValue>();

		idlArgs = IDLArgs.create(args);

		buf = idlArgs.toBytes();

		queryResponse = agent.queryRaw(
				Principal.fromString(TestProperties.LOAN_CANISTER_ID),
				Principal.fromString(TestProperties.LOAN_CANISTER_ID), "getName", buf, Optional.empty());

		try {
			byte[] queryOutput = queryResponse.get();

			Optional<String> nameResult = IDLArgs.fromBytes(queryOutput).getArgs().get(0).getValue(new PojoDeserializer(), Optional.class);
			
			Assertions.assertEquals("Name", nameResult.get());

		} catch (Throwable ex) {
			LOG.debug(ex.getLocalizedMessage(), ex);
			Assertions.fail(ex.getLocalizedMessage());
		}		
		
		// Loan Offer Request		
		
		LoanOffer loan = new LoanOffer();
		
		loan.userId = Principal.fromString("ubgwl-msd3g-gr5yh-cwpic-elony-lnexo-5f3wf-atisx-hxeyt-ffmfu-tqe");
		loan.apr = (double) 3.4;
		loan.applicationId = 11;
		loan.providerName = "United Loan";
		loan.providerId = "zrakb-eaaaa-aaaab-qacaq-cai";
		loan.created = System.currentTimeMillis();
		
		LoanOffer[] loanArray = {loan};

		idlValue = IDLValue.create(loanArray, new PojoSerializer());

		args = new ArrayList<IDLValue>();
		args.add(idlValue);

		idlArgs = IDLArgs.create(args);

		buf = idlArgs.toBytes();

		queryResponse = agent.queryRaw(
				Principal.fromString(TestProperties.LOAN_CANISTER_ID),
				Principal.fromString(TestProperties.LOAN_CANISTER_ID), "echoOffers", buf, Optional.empty());

		try {
			byte[] queryOutput = queryResponse.get();

			LoanOffer[] loanArrayResult = IDLArgs.fromBytes(queryOutput).getArgs().get(0).getValue(new PojoDeserializer(), LoanOffer[].class);
			
			Assertions.assertArrayEquals(loanArray, loanArrayResult);	

		} catch (Throwable ex) {
			LOG.debug(ex.getLocalizedMessage(), ex);
			Assertions.fail(ex.getLocalizedMessage());
		}

		// Loan Applications	
		
		LoanApplication loanApplication = new LoanApplication();
		loanApplication.firstName = "John";
		loanApplication.lastName = "Doe";
		loanApplication.ssn = "111-11-1111";
		loanApplication.term = 48;
		loanApplication.zipcode = "95134";		
		loanApplication.amount = (double) 20000.00;
		loanApplication.id = new BigInteger("11");
		loanApplication.created = new BigInteger("0");
		
		LoanApplication[] loanApplicationArray = {loanApplication};

		idlValue = IDLValue.create(loanApplicationArray, new PojoSerializer());

		args = new ArrayList<IDLValue>();
		args.add(idlValue);

		idlArgs = IDLArgs.create(args);

		buf = idlArgs.toBytes();
		
		queryResponse = agent.queryRaw(
				Principal.fromString(TestProperties.LOAN_CANISTER_ID),
				Principal.fromString(TestProperties.LOAN_CANISTER_ID), "echoApplications", buf, Optional.empty());

		try {
			byte[] queryOutput = queryResponse.get();

			LoanApplication[] loanApplicationArrayResult = IDLArgs.fromBytes(queryOutput).getArgs().get(0).getValue(new PojoDeserializer(), LoanApplication[].class);
			
			Assertions.assertArrayEquals(loanApplicationArray, loanApplicationArrayResult);	

		} catch (Throwable ex) {
			LOG.debug(ex.getLocalizedMessage(), ex);
			Assertions.fail(ex.getLocalizedMessage());
		}
		
		ProxyBuilder proxyBuilder = ProxyBuilder.create(agent).loadIDL(true);
		
		Func funcValue = new Func(Principal.fromString(TestProperties.LOAN_CANISTER_ID), "echoApplications");
		
		FuncProxy<LoanApplication[]> funcProxy = proxyBuilder.getFuncProxy(funcValue);
		
		funcProxy.setResponseClass(LoanApplication[].class);
		
		LoanApplication[] loanApplicationArrayResult = funcProxy.call(Arrays.asList(loanApplicationArray));
		
		Assertions.assertArrayEquals(loanApplicationArray, loanApplicationArrayResult);	
		
		FuncProxy<List<LoanApplication>> listFuncProxy = proxyBuilder.getFuncProxy(funcValue);
		
		List<LoanApplication> list = new ArrayList<LoanApplication>() { }; // create a specific sub-class
		Class<? extends List> listClass = list.getClass();
						
		listFuncProxy.setResponseClass(listClass);
		
		List<LoanApplication> loanApplicationListResult =  listFuncProxy.call(Arrays.asList(loanApplicationArray));
		
		Assertions.assertArrayEquals(loanApplicationArray,loanApplicationListResult.toArray());
		
		agent.close();
		
	} catch (URISyntaxException e) {
		LOG.error(e.getLocalizedMessage(), e);
		Assertions.fail(e.getMessage());
	} catch (AgentError e) {
		LOG.error(e.getLocalizedMessage(), e);
		Assertions.fail(e.getMessage());
	} finally {

	}		
	}
}
