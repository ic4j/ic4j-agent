package org.ic4j.agent.test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ic4j.agent.identity.BasicIdentity;
import org.ic4j.agent.identity.Identity;
import org.ic4j.agent.identity.Secp256k1Identity;
import org.ic4j.agent.identity.Signature;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IdentityTest {
	static final Logger LOG = LoggerFactory.getLogger(IdentityTest.class);

	@Test
	public void test() {
		Security.addProvider(new BouncyCastleProvider());

		KeyPair keyPair;
		try {
			keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();

			KeyFactory kf = KeyFactory.getInstance("Ed25519");

			Identity identity = BasicIdentity.fromKeyPair(keyPair);

			Signature signature = identity.sign("Hello".getBytes());

			java.security.Signature sig = java.security.Signature.getInstance("EdDSA");

			PublicKey publicKey = kf.generatePublic(new X509EncodedKeySpec(signature.publicKey.get()));

			sig.initVerify(publicKey);

			sig.update("Hello".getBytes());

			boolean verifies = sig.verify(signature.signature.get());

			LOG.debug(Boolean.toString(verifies));

			assert (verifies);

			Path path = Paths
					.get(getClass().getClassLoader().getResource(TestProperties.ED25519_IDENTITY_FILE).getPath());

			identity = BasicIdentity.fromPEMFile(path);

			signature = identity.sign("Hello".getBytes());

			publicKey = kf.generatePublic(new X509EncodedKeySpec(signature.publicKey.get()));

			sig.initVerify(publicKey);

			sig.update("Hello".getBytes());

			verifies = sig.verify(signature.signature.get());

			LOG.debug(Boolean.toString(verifies));

			assert (verifies);

			path = Paths.get(getClass().getClassLoader().getResource(TestProperties.SECP256K1_IDENTITY_FILE).getPath());

			identity = Secp256k1Identity.fromPEMFile(path);

			signature = identity.sign("Hello".getBytes());

			sig = java.security.Signature.getInstance("SHA256withPLAIN-ECDSA", "BC");

			kf = KeyFactory.getInstance("ECDSA", "BC");

			publicKey = kf.generatePublic(new X509EncodedKeySpec(signature.publicKey.get()));

			sig.initVerify(publicKey);

			sig.update("Hello".getBytes());

			verifies = sig.verify(signature.signature.get());

			LOG.debug(Boolean.toString(verifies));

		} catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | SignatureException
				| NoSuchProviderException e) {
			LOG.info(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());
		}
	}

}
