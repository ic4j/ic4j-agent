package org.ic4j.agent.test;

import java.io.IOException;
import java.net.URISyntaxException;
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
import org.ic4j.agent.Agent;
import org.ic4j.agent.AgentBuilder;
import org.ic4j.agent.AgentError;
import org.ic4j.agent.NonceFactory;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.identity.BasicIdentity;
import org.ic4j.agent.identity.Identity;
import org.ic4j.agent.identity.Secp256k1Identity;
import org.ic4j.agent.identity.Signature;
import org.ic4j.agent.replicaapi.Certificate;
import org.ic4j.agent.replicaapi.ReadStateResponse;
import org.ic4j.candid.ByteUtils;
import org.ic4j.types.Principal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.miracl.core.RAND;
import org.miracl.core.BLS12381.BLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

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
			
			testBLS();

		} catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException | SignatureException
				| NoSuchProviderException e) {
			LOG.info(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());
		}
	}
	
	public void testBLS()
	{
		
		RAND rng=new RAND();
		int BGS=BLS.BGS;
		int BFS=BLS.BFS;
		int G1S=BFS+1; /* Group 1 Size - compressed */
		int G2S=2*BFS+1; /* Group 2 Size - compressed */

		
		byte[] S = new byte[BGS];
		byte[] W = new byte[G2S];
		byte[] SIG = new byte[G1S];
		byte[] RAW=new byte[100];
        byte[] IKM=new byte[32];

		rng.clean();
		for (int i=0;i<100;i++) RAW[i]=(byte)(i);
		rng.seed(100,RAW);

        for (int i=0;i<IKM.length;i++)
            //IKM[i]=(byte)(i+1);
            IKM[i]=(byte)rng.getByte();

		System.out.println("\nTesting BLS code");
		
		

		int res=BLS.init();
		
		Assertions.assertFalse(res!=0);

		String mess=new String("This is a test message");

		res=BLS.KeyPairGenerate(IKM,S,W);
		
		Assertions.assertFalse(res!=0);
		
		System.out.print("Private key : 0x");  printBinary(S);
		System.out.print("Public  key : 0x");  printBinary(W);

		BLS.core_sign(SIG,mess.getBytes(),S);
		System.out.print("Signature : 0x");  printBinary(SIG);

		res=BLS.core_verify(SIG,mess.getBytes(),W);

		if (res==0)
			LOG.info("Signature is OK");
		else
			Assertions.fail("Signature is *NOT* OK");
			
			
		
		try {
			ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());
			objectMapper.registerModule(new Jdk8Module());		
			
	        int[] data = { 217, 217, 247, 161, 107, 99, 101, 114, 116, 105, 102, 105, 99, 97, 116, 101, 89, 4, 31, 217,
	        	    217, 247, 163, 100, 116, 114, 101, 101, 131, 1, 131, 1, 130, 4, 88, 32, 37, 15, 94, 38, 134,
	        	    141, 156, 30, 167, 171, 41, 203, 233, 193, 91, 241, 196, 124, 13, 118, 5, 232, 3, 227, 158, 55,
	        	    90, 127, 224, 156, 110, 187, 131, 1, 131, 2, 78, 114, 101, 113, 117, 101, 115, 116, 95, 115,
	        	    116, 97, 116, 117, 115, 131, 1, 130, 4, 88, 32, 75, 38, 130, 39, 119, 78, 199, 127, 242, 179,
	        	    126, 203, 18, 21, 115, 41, 213, 76, 243, 118, 105, 75, 221, 89, 222, 215, 128, 62, 253, 130,
	        	    56, 111, 131, 2, 88, 32, 237, 173, 81, 14, 170, 160, 142, 210, 172, 212, 120, 19, 36, 230, 68,
	        	    98, 105, 218, 103, 83, 236, 23, 118, 15, 32, 107, 190, 129, 196, 101, 255, 82, 131, 1, 131, 1,
	        	    131, 2, 75, 114, 101, 106, 101, 99, 116, 95, 99, 111, 100, 101, 130, 3, 65, 3, 131, 2, 78, 114,
	        	    101, 106, 101, 99, 116, 95, 109, 101, 115, 115, 97, 103, 101, 130, 3, 88, 68, 67, 97, 110, 105,
	        	    115, 116, 101, 114, 32, 105, 118, 103, 51, 55, 45, 113, 105, 97, 97, 97, 45, 97, 97, 97, 97,
	        	    98, 45, 97, 97, 97, 103, 97, 45, 99, 97, 105, 32, 104, 97, 115, 32, 110, 111, 32, 117, 112,
	        	    100, 97, 116, 101, 32, 109, 101, 116, 104, 111, 100, 32, 39, 114, 101, 103, 105, 115, 116, 101,
	        	    114, 39, 131, 2, 70, 115, 116, 97, 116, 117, 115, 130, 3, 72, 114, 101, 106, 101, 99, 116, 101,
	        	    100, 130, 4, 88, 32, 151, 35, 47, 49, 246, 171, 124, 164, 254, 83, 235, 101, 104, 252, 62, 2,
	        	    188, 34, 254, 148, 171, 49, 208, 16, 229, 251, 60, 100, 35, 1, 241, 96, 131, 1, 130, 4, 88, 32,
	        	    58, 72, 209, 252, 33, 61, 73, 48, 113, 3, 16, 79, 125, 114, 194, 181, 147, 14, 219, 168, 120,
	        	    123, 144, 99, 31, 52, 59, 58, 166, 138, 95, 10, 131, 2, 68, 116, 105, 109, 101, 130, 3, 73,
	        	    226, 220, 147, 144, 145, 198, 150, 235, 22, 105, 115, 105, 103, 110, 97, 116, 117, 114, 101,
	        	    88, 48, 137, 162, 190, 33, 181, 250, 138, 201, 250, 177, 82, 126, 4, 19, 39, 206, 137, 157,
	        	    125, 169, 113, 67, 106, 31, 33, 101, 57, 57, 71, 180, 217, 66, 54, 91, 254, 84, 136, 113, 14,
	        	    97, 166, 25, 186, 72, 56, 138, 33, 177, 106, 100, 101, 108, 101, 103, 97, 116, 105, 111, 110,
	        	    162, 105, 115, 117, 98, 110, 101, 116, 95, 105, 100, 88, 29, 215, 123, 42, 47, 113, 153, 185,
	        	    168, 174, 201, 63, 230, 251, 88, 134, 97, 53, 140, 241, 34, 35, 233, 163, 175, 123, 78, 186,
	        	    196, 2, 107, 99, 101, 114, 116, 105, 102, 105, 99, 97, 116, 101, 89, 2, 49, 217, 217, 247, 162,
	        	    100, 116, 114, 101, 101, 131, 1, 130, 4, 88, 32, 174, 2, 63, 40, 195, 185, 217, 102, 200, 251,
	        	    9, 249, 237, 117, 92, 130, 138, 173, 181, 21, 46, 0, 170, 247, 0, 177, 140, 156, 6, 114, 148,
	        	    180, 131, 1, 131, 2, 70, 115, 117, 98, 110, 101, 116, 131, 1, 130, 4, 88, 32, 232, 59, 176, 37,
	        	    246, 87, 76, 143, 49, 35, 61, 192, 254, 40, 159, 245, 70, 223, 161, 228, 155, 214, 17, 109,
	        	    214, 232, 137, 109, 144, 164, 148, 110, 131, 1, 130, 4, 88, 32, 231, 130, 97, 144, 146, 214,
	        	    157, 91, 235, 240, 146, 65, 56, 189, 65, 22, 176, 21, 107, 90, 149, 226, 92, 53, 142, 168, 207,
	        	    126, 113, 97, 166, 97, 131, 1, 131, 1, 130, 4, 88, 32, 98, 81, 63, 169, 38, 201, 169, 239, 128,
	        	    58, 194, 132, 214, 32, 243, 3, 24, 149, 136, 225, 211, 144, 67, 73, 171, 99, 182, 71, 8, 86,
	        	    252, 72, 131, 1, 130, 4, 88, 32, 96, 233, 163, 68, 206, 210, 201, 196, 169, 106, 1, 151, 253,
	        	    88, 95, 45, 37, 157, 189, 25, 62, 78, 173, 165, 98, 57, 202, 194, 96, 135, 249, 197, 131, 2,
	        	    88, 29, 215, 123, 42, 47, 113, 153, 185, 168, 174, 201, 63, 230, 251, 88, 134, 97, 53, 140,
	        	    241, 34, 35, 233, 163, 175, 123, 78, 186, 196, 2, 131, 1, 131, 2, 79, 99, 97, 110, 105, 115,
	        	    116, 101, 114, 95, 114, 97, 110, 103, 101, 115, 130, 3, 88, 27, 217, 217, 247, 129, 130, 74, 0,
	        	    0, 0, 0, 0, 32, 0, 0, 1, 1, 74, 0, 0, 0, 0, 0, 47, 255, 255, 1, 1, 131, 2, 74, 112, 117, 98,
	        	    108, 105, 99, 95, 107, 101, 121, 130, 3, 88, 133, 48, 129, 130, 48, 29, 6, 13, 43, 6, 1, 4, 1,
	        	    130, 220, 124, 5, 3, 1, 2, 1, 6, 12, 43, 6, 1, 4, 1, 130, 220, 124, 5, 3, 2, 1, 3, 97, 0, 153,
	        	    51, 225, 248, 158, 138, 60, 77, 127, 220, 204, 219, 213, 24, 8, 158, 43, 212, 216, 24, 10, 38,
	        	    31, 24, 217, 194, 71, 165, 39, 104, 235, 206, 152, 220, 115, 40, 163, 152, 20, 168, 249, 17, 8,
	        	    106, 29, 213, 12, 190, 1, 94, 42, 83, 183, 191, 120, 181, 82, 136, 137, 61, 170, 21, 195, 70,
	        	    100, 14, 136, 49, 215, 42, 18, 189, 237, 217, 121, 210, 132, 112, 195, 72, 35, 184, 209, 195,
	        	    244, 121, 93, 156, 57, 132, 162, 71, 19, 46, 148, 254, 130, 4, 88, 32, 153, 111, 23, 187, 146,
	        	    107, 227, 49, 87, 69, 222, 167, 40, 32, 5, 167, 147, 181, 142, 118, 175, 235, 93, 67, 209, 162,
	        	    140, 226, 157, 45, 21, 133, 131, 2, 68, 116, 105, 109, 101, 130, 3, 73, 149, 184, 170, 192,
	        	    228, 237, 162, 234, 22, 105, 115, 105, 103, 110, 97, 116, 117, 114, 101, 88, 48, 172, 233, 252,
	        	    221, 155, 201, 119, 224, 93, 99, 40, 248, 137, 220, 78, 124, 153, 17, 76, 115, 122, 73, 70, 83,
	        	    203, 39, 161, 245, 92, 6, 244, 85, 94, 15, 22, 9, 128, 175, 94, 173, 9, 138, 204, 25, 80, 16,
	        	    178, 247};


	        byte[] array = ByteUtils.toSignedByteArray(data);
		
			ReadStateResponse readStateResponse = objectMapper.readValue(array, ReadStateResponse.class);
			
			Agent agent = new AgentBuilder().transport(ReplicaApacheHttpTransport.create(TestProperties.IC_URL)).nonceFactory(new NonceFactory())
					.build();
						
			Certificate certificate = objectMapper.readValue(readStateResponse.certificate, Certificate.class);
			
			agent.verify(certificate, Principal.fromString("ivg37-qiaaa-aaaab-aaaga-cai"), false);
			
			try {
				agent.verify(certificate, Principal.fromString("ryjl3-tyaaa-aaaaa-aaaba-cai"), false);
				Assertions.fail();
				
			}catch(AgentError error)
			{
				LOG.info(error.getLocalizedMessage());
			}
		} catch (AgentError | IOException | URISyntaxException e) {
			LOG.info(e.getLocalizedMessage(), e);
			Assertions.fail(e.getMessage());
		}
	}
	

	

	
	private static void printBinary(byte[] array)
	{
		int i;
		for (i=0;i<array.length;i++)
		{
			System.out.printf("%02x", array[i]);
		}
		System.out.println();
	}	

}
