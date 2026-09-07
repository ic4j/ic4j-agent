package org.ic4j.agent.test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ic4j.agent.identity.BasicIdentity;
import org.ic4j.agent.identity.Signature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BasicIdentityTest {
	@Test
	public void signsWithEd25519Identity() throws Exception {
		URL pem = getClass().getClassLoader().getResource(TestProperties.ED25519_IDENTITY_FILE);
		Assertions.assertNotNull(pem);
		BasicIdentity identity = BasicIdentity.fromPEMFile(Paths.get(pem.toURI()));
		byte[] content = "replica ingress message".getBytes(StandardCharsets.UTF_8);

		Signature signed = identity.sign(content);
		KeyFactory keyFactory = KeyFactory.getInstance("Ed25519", BouncyCastleProvider.PROVIDER_NAME);
		PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(signed.publicKey.get()));
		java.security.Signature verifier = java.security.Signature.getInstance(
				"EdDSA", BouncyCastleProvider.PROVIDER_NAME);
		verifier.initVerify(publicKey);
		verifier.update(content);
		Assertions.assertTrue(verifier.verify(signed.signature.get()));
	}
}