package org.ic4j.agent.test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ic4j.agent.identity.Prime256v1Identity;
import org.ic4j.agent.identity.Signature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Prime256v1IdentityTest {
	@Test
	public void signsWithPrime256v1Identity() throws Exception {
		URL pem = getClass().getClassLoader().getResource(TestProperties.PRIME256V1_IDENTITY_PRIVATE_FILE);
		Assertions.assertNotNull(pem);
		Prime256v1Identity identity = Prime256v1Identity.fromPEMFile(Paths.get(pem.toURI()));
		byte[] content = "replica ingress message".getBytes(StandardCharsets.UTF_8);

		Signature signed = identity.sign(content);
		Assertions.assertEquals(64, signed.signature.get().length);
		KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
		PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(signed.publicKey.get()));
		java.security.Signature verifier = java.security.Signature.getInstance(
				"SHA256withPLAIN-ECDSA", BouncyCastleProvider.PROVIDER_NAME);
		verifier.initVerify(publicKey);
		verifier.update(content);
		Assertions.assertTrue(verifier.verify(signed.signature.get()));
	}
}