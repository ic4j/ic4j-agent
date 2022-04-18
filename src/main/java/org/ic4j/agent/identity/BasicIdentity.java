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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.math.ec.rfc8032.Ed25519;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.ic4j.types.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BasicIdentity implements Identity {
	static final Logger LOG = LoggerFactory.getLogger(BasicIdentity.class);
    static JcaPEMKeyConverter pkcs8pemKeyConverter = new JcaPEMKeyConverter();
	
	KeyPair keyPair;
	public byte[] derEncodedPublickey;

	static {
		Security.addProvider(new BouncyCastleProvider());
		pkcs8pemKeyConverter.setProvider(BouncyCastleProvider.PROVIDER_NAME);
	}

	BasicIdentity(KeyPair keyPair, byte[] derEncodedPublickey) {
		this.keyPair = keyPair;
		this.derEncodedPublickey = derEncodedPublickey;
	}
	
	public static BasicIdentity fromPEMFile(Reader reader) {
		try {			
			
			PEMParser pemParser = new PEMParser(reader);

		    Object pemObject = pemParser.readObject();
	        
		    if(pemObject instanceof PrivateKeyInfo)
		    {	    	
		    	PrivateKey privateKey = pkcs8pemKeyConverter.getPrivateKey((PrivateKeyInfo) pemObject);
		    
				KeyFactory keyFactory = KeyFactory.getInstance("Ed25519"); 
				
				byte[] publicKeyBytes = ((PrivateKeyInfo) pemObject).getPublicKeyData().getBytes();
				// Wrap public key in ASN.1 format so we can use X509EncodedKeySpec to read it
				SubjectPublicKeyInfo pubKeyInfo = new SubjectPublicKeyInfo(
						new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519), publicKeyBytes);
				X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(pubKeyInfo.getEncoded());

				PublicKey publicKey = keyFactory.generatePublic(x509KeySpec);			
		    	
		    	KeyPair keyPair = new KeyPair(publicKey, privateKey);
		    	return new BasicIdentity(keyPair, publicKey.getEncoded());
		    }
		    else
		    	throw PemError.create(PemError.PemErrorCode.PEM_ERROR);
			
		} catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw PemError.create(PemError.PemErrorCode.PEM_ERROR, e);
		}
	}	

	public static BasicIdentity fromPEMFile(Path path) {
		try {
			Reader reader = Files.newBufferedReader(path);
			return fromPEMFile(reader);
		} catch (IOException e) {
			throw PemError.create(PemError.PemErrorCode.PEM_ERROR, e);
		}
	}
	
	// Create a BasicIdentity from reading byte array 
	public static BasicIdentity fromPEM(byte[] keyBytes) {
		if (keyBytes.length == Ed25519.SECRET_KEY_SIZE + Ed25519.PUBLIC_KEY_SIZE) {
			try {
				KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
				// some legacy code delivers raw private and public key pairs concatted together

				// this is how we read only the first 32 bytes
				byte[] privateKeyBytes = new byte[Ed25519.SECRET_KEY_SIZE];
				System.arraycopy(keyBytes, 0, privateKeyBytes, 0, Ed25519.SECRET_KEY_SIZE);

				// read the remaining 32 bytes as the public key
				byte[] publicKeyBytes = new byte[Ed25519.PUBLIC_KEY_SIZE];
				System.arraycopy(keyBytes, Ed25519.SECRET_KEY_SIZE, publicKeyBytes, 0, Ed25519.PUBLIC_KEY_SIZE);

				// Wrap public key in ASN.1 format so we can use X509EncodedKeySpec to read it
				SubjectPublicKeyInfo pubKeyInfo = new SubjectPublicKeyInfo(
						new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519), publicKeyBytes);
				X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(pubKeyInfo.getEncoded());

				PublicKey publicKey = keyFactory.generatePublic(x509KeySpec);
				// Wrap private key in ASN.1 format so we can use
				PrivateKeyInfo privKeyInfo = new PrivateKeyInfo(
						new AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519), new DEROctetString(privateKeyBytes));
				PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(privKeyInfo.getEncoded());

				PrivateKey privateKey = keyFactory.generatePrivate(pkcs8KeySpec);

				KeyPair keyPair = new KeyPair(publicKey, privateKey);

				return fromKeyPair(keyPair);
			} catch (InvalidKeySpecException | IOException | NoSuchAlgorithmException e) {
				throw PemError.create(PemError.PemErrorCode.PEM_ERROR, e);
			}
		} else
			throw PemError.create(PemError.PemErrorCode.PEM_ERROR);
	}

	/// Create a BasicIdentity from a KeyPair
	public static BasicIdentity fromKeyPair(KeyPair keyPair) {
		PublicKey publicKey = keyPair.getPublic();

		return new BasicIdentity(keyPair, publicKey.getEncoded());
	}

	@Override
	public Principal sender() {
		return Principal.selfAuthenticating(derEncodedPublickey);
	}

	@Override
	public Signature sign(byte[] msg) {
		try {
			// Generate new signature
			java.security.Signature dsa;
			dsa = java.security.Signature.getInstance("EdDSA");
			// Edwards digital signature algorithm
			dsa.initSign(this.keyPair.getPrivate());
			dsa.update(msg, 0, msg.length);
			byte[] signature = dsa.sign();

			return new Signature(this.derEncodedPublickey, signature);

		} catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
			throw PemError.create(PemError.PemErrorCode.ERROR_STACK, e);
		}

	}

}
