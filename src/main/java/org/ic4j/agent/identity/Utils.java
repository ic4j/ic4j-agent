package org.ic4j.agent.identity;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.KeyPair;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

public class Utils {
	
	public static byte[] getPublicKeyBytes(KeyPair keyPair) throws IOException
	{
		PemObject pemObject = new PemObject("PRIVATE KEY", keyPair.getPrivate().getEncoded());				
		
		StringWriter stringWriter = new StringWriter();
		PemWriter pemWriter = new PemWriter(stringWriter);
		pemWriter.writeObject(pemObject);
		pemWriter.close();
		
		String buf = stringWriter.getBuffer().toString();
		
		PEMParser pemParser = new PEMParser(new StringReader(buf));

	    Object pemObject2 = pemParser.readObject();
	    
	    return ((PrivateKeyInfo) pemObject2).getPublicKeyData().getBytes();
	}

}
