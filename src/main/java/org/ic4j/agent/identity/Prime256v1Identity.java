/*
 * Copyright 2024 Exilor Inc.
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

public final class Prime256v1Identity extends Identity {
	static final Logger LOG = LoggerFactory.getLogger(Prime256v1Identity.class);

	static JcaPEMKeyConverter jcaPemKeyConverter = new JcaPEMKeyConverter();
	

	KeyPair keyPair;
	public byte[] derEncodedPublickey;

	static {
		Security.addProvider(new BouncyCastleProvider());
		jcaPemKeyConverter.setProvider(BouncyCastleProvider.PROVIDER_NAME);
	}

	Prime256v1Identity(KeyPair keyPair) {
		this.keyPair = keyPair;
		this.derEncodedPublickey = keyPair.getPublic().getEncoded();
	}

	public static Prime256v1Identity fromPEMFile(Reader reader) {
		try {

			PEMParser pemParser = new PEMParser(reader);

			Object pemObject = pemParser.readObject();
			
			pemParser.close();
			
			if (pemObject instanceof PEMKeyPair) {
				KeyPair keyPair = jcaPemKeyConverter.getKeyPair((PEMKeyPair) pemObject);
				
				return new Prime256v1Identity(keyPair);
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
		    	return new Prime256v1Identity(keyPair);
		    }
		    else
		    	throw PemError.create(PemError.PemErrorCode.PEM_ERROR);			
	

		} catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
			throw PemError.create(PemError.PemErrorCode.PEM_ERROR, e);
		}
	}
	
	public static Prime256v1Identity fromPEMFile(Path path) {
		try {
			Reader reader = Files.newBufferedReader(path);
			return fromPEMFile(reader);
		} catch (IOException e) {
			throw PemError.create(PemError.PemErrorCode.PEM_ERROR, e);
		}
	}

	/// Create a Prime256v1Identity from a KeyPair
	public static Prime256v1Identity fromKeyPair(KeyPair keyPair) {
		return new Prime256v1Identity(keyPair);
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
			java.security.Signature dsa = java.security.Signature.getInstance("SHA512withECDSA", BouncyCastleProvider.PROVIDER_NAME);
			// ECDSA digital signature algorithm
			dsa.initSign(this.keyPair.getPrivate());

			dsa.update(content);

			byte[] signature = dsa.sign();

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
