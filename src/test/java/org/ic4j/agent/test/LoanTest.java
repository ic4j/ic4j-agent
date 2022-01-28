package org.ic4j.agent.test;

import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.ic4j.agent.Agent;
import org.ic4j.agent.AgentBuilder;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.NonceFactory;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.http.ReplicaOkHttpTransport;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.candid.pojo.PojoDeserializer;
import org.ic4j.candid.pojo.PojoSerializer;
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

		List<IDLValue> args = new ArrayList<IDLValue>();
		args.add(idlValue);

		IDLArgs idlArgs = IDLArgs.create(args);

		byte[] buf = idlArgs.toBytes();

		CompletableFuture<byte[]> queryResponse = agent.queryRaw(
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
		
		// Loan Offer Request		
		
		LoanOffer loan = new LoanOffer();
		
		loan.userId = Principal.fromString("ubgwl-msd3g-gr5yh-cwpic-elony-lnexo-5f3wf-atisx-hxeyt-ffmfu-tqe");
		loan.apr = (double) 3.4;
		loan.applicationId = new BigInteger("11");
		loan.providerName = "United Loan";
		loan.providerId = Principal.fromString("zrakb-eaaaa-aaaab-qacaq-cai");
		loan.created = new BigInteger("0");
		
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
