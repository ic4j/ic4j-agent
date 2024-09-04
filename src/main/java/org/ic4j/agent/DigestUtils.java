package org.ic4j.agent;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.xml.bind.DatatypeConverter;

public class DigestUtils {
	
	public static MessageDigest getSha256Digest() {
		MessageDigest messageDigest;
		try {
			messageDigest = MessageDigest.getInstance("SHA-256");
			
			return messageDigest;
		} catch (NoSuchAlgorithmException e) {
			throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e);
		}
    }
	
    // Compute SHA-256 hash
    public static byte[] sha256(byte[] bytes)  {
        MessageDigest digest = getSha256Digest();
        return digest.digest(bytes);
    }
       
}
