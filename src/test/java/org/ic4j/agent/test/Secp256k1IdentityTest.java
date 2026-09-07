package org.ic4j.agent.test;

import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ic4j.agent.identity.Secp256k1Identity;
import org.ic4j.agent.identity.Signature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Secp256k1IdentityTest {
	static final BigInteger HALF_CURVE_ORDER = new BigInteger(
			"7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16);

	@Test
	public void signsWithCanonicalLowS() throws Exception {
		URL pem = getClass().getClassLoader().getResource(TestProperties.SECP256K1_IDENTITY_PRIVATE_FILE);
		Assertions.assertNotNull(pem);
		Secp256k1Identity identity = Secp256k1Identity.fromPEMFile(Paths.get(pem.toURI()));
		byte[] content = "replica ingress message".getBytes(StandardCharsets.UTF_8);

		for (int i = 0; i < 32; i++) {
			Signature signed = identity.sign(content);
			byte[] signature = signed.signature.get();
			Assertions.assertEquals(64, signature.length);
			Assertions.assertTrue(new BigInteger(1, Arrays.copyOfRange(signature, 32, 64))
					.compareTo(HALF_CURVE_ORDER) <= 0);

			KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
			PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(signed.publicKey.get()));
			java.security.Signature verifier = java.security.Signature.getInstance(
					"SHA256withPLAIN-ECDSA", BouncyCastleProvider.PROVIDER_NAME);
			verifier.initVerify(publicKey);
			verifier.update(content);
			Assertions.assertTrue(verifier.verify(signature));
		}
	}
}
