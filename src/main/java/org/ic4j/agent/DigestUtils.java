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
