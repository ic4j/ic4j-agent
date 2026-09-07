/*
 * Copyright 2021 Exilor Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.ic4j.agent.identity;

import java.io.IOException;
import java.io.Reader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.Security;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.replicaapi.Delegation;
import org.ic4j.types.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Secp256k1Identity extends Identity {
	static final Logger LOG = LoggerFactory.getLogger(Secp256k1Identity.class);
	static final BigInteger CURVE_ORDER = new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16);
	static final BigInteger HALF_CURVE_ORDER = CURVE_ORDER.shiftRight(1);

	static JcaPEMKeyConverter jcaPemKeyConverter = new JcaPEMKeyConverter();

	KeyPair keyPair;
	public byte[] derEncodedPublickey;

	static {
		Security.addProvider(new BouncyCastleProvider());
		jcaPemKeyConverter.setProvider(BouncyCastleProvider.PROVIDER_NAME);
	}

	Secp256k1Identity(KeyPair keyPair) {
		this.keyPair = keyPair;
		this.derEncodedPublickey = keyPair.getPublic().getEncoded();
	}

	public static Secp256k1Identity fromPEMFile(Reader reader) {
		try {

			PEMParser pemParser = new PEMParser(reader);

			Object pemObject = pemParser.readObject();
			
			pemParser.close();

			if (pemObject instanceof PEMKeyPair) {
				KeyPair keyPair = jcaPemKeyConverter.getKeyPair((PEMKeyPair) pemObject);

				return new Secp256k1Identity(keyPair);
			}
			else if(pemObject instanceof PrivateKeyInfo)
		    {	    	
		    	BCECPrivateKey privateKey = (BCECPrivateKey) jcaPemKeyConverter.getPrivateKey((PrivateKeyInfo) pemObject);    
				
				KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
			    BigInteger d = privateKey.getD();
			    ECParameterSpec ecSpec = 
			    		privateKey.getParameters();
			    ECPoint Q = privateKey.getParameters().getG().multiply(d);

			    ECPublicKeySpec pubSpec = new ECPublicKeySpec(Q, ecSpec);		

				PublicKey publicKey = keyFactory.generatePublic(pubSpec);	
		    	
		    	KeyPair keyPair = new KeyPair(publicKey, privateKey);
		    	return new Secp256k1Identity(keyPair);
		    }else
				throw PemError.create(PemError.PemErrorCode.PEM_ERROR);

		} catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
			throw PemError.create(PemError.PemErrorCode.PEM_ERROR, e);
		}
	}
	
	public static Secp256k1Identity fromPEMFile(Path path) {
		try {
			Reader reader = Files.newBufferedReader(path);
			return fromPEMFile(reader);
		} catch (IOException e) {
			throw PemError.create(PemError.PemErrorCode.PEM_ERROR, e);
		}
	}

	/// Create a Secp256k1Identity from a KeyPair
	public static Secp256k1Identity fromKeyPair(KeyPair keyPair) {
		return new Secp256k1Identity(keyPair);
	}

	@Override
	public Principal sender() {
		return Principal.selfAuthenticating(derEncodedPublickey);
	}

	
	@Override
	public Signature sign(byte[] content) {
		return this.signArbitrary(content);
	}
	
	@Override
	public Signature signDelegation(Delegation delegation) throws AgentError {
		return this.signArbitrary(delegation.signable());
	}

	@Override
	public Signature signArbitrary(byte[] content) {
		try {

			// Generate new signature
			java.security.Signature dsa = java.security.Signature.getInstance("SHA256withPLAIN-ECDSA", BouncyCastleProvider.PROVIDER_NAME);
			// ECDSA digital signature algorithm
			dsa.initSign(this.keyPair.getPrivate());

			dsa.update(content);

			byte[] signature = dsa.sign();
			BigInteger s = new BigInteger(1, Arrays.copyOfRange(signature, 32, 64));
			if (s.compareTo(HALF_CURVE_ORDER) > 0) {
				byte[] normalizedS = CURVE_ORDER.subtract(s).toByteArray();
				int sourceOffset = Math.max(0, normalizedS.length - 32);
				int length = normalizedS.length - sourceOffset;
				Arrays.fill(signature, 32, 64, (byte) 0);
				System.arraycopy(normalizedS, sourceOffset, signature, 64 - length, length);
			}

			return new Signature(this.derEncodedPublickey, signature, null);

		} catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException e) {
			throw PemError.create(PemError.PemErrorCode.ERROR_STACK, e);
		}

	}	
	
	public byte[] getPublicKey()
	{
		return this.derEncodedPublickey;
	}

}
