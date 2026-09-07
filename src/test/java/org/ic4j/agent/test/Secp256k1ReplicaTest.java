package org.ic4j.agent.test;

import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.ic4j.agent.Agent;
import org.ic4j.agent.AgentBuilder;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.http.ReplicaOkHttpTransport;
import org.ic4j.agent.identity.Secp256k1Identity;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.types.Principal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class Secp256k1ReplicaTest {
	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "apache", "okhttp" })
	public void queriesLocalReplicaWithSecp256k1Identity(String transportType) throws Exception {
		Assertions.assertTrue(TestProperties.isLocalReplicaUrl(TestProperties.IC_URL));
		URL pem = getClass().getClassLoader().getResource(TestProperties.SECP256K1_IDENTITY_PRIVATE_FILE);
		Assertions.assertNotNull(pem);

		Secp256k1Identity identity = Secp256k1Identity.fromPEMFile(Paths.get(pem.toURI()));
		Agent agent = new AgentBuilder()
				.transport(createTransport(transportType))
				.identity(identity)
				.ingresExpiry(Duration.ofMinutes(4))
				.build();
		agent.fetchRootKey();

		List<IDLValue> values = new ArrayList<IDLValue>();
		values.add(IDLValue.create("secp256k1"));
		values.add(IDLValue.create(BigInteger.ONE));
		byte[] args = IDLArgs.create(values).toBytes();
		Principal canisterId = Principal.fromString(TestProperties.IC_CANISTER_ID);

		byte[] response = agent.queryRaw(canisterId, canisterId, "peek", args, Optional.empty()).get();
		IDLArgs responseArgs = IDLArgs.fromBytes(response);
		Assertions.assertEquals("Hello, secp256k1!", responseArgs.getArgs().get(0).getValue());
	}

	private ReplicaTransport createTransport(String transportType) throws URISyntaxException {
		if ("okhttp".equals(transportType))
			return ReplicaOkHttpTransport.create(TestProperties.IC_URL);

		return ReplicaApacheHttpTransport.create(TestProperties.IC_URL);
	}
}
