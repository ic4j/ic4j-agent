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

package org.ic4j.agent.requestid;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.codec.digest.DigestUtils;
import org.ic4j.agent.Serializer;
import org.ic4j.candid.ByteUtils;
import org.ic4j.candid.Leb128;
import org.ic4j.types.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RequestIdSerializer implements Serializer {
	static final Logger LOG = LoggerFactory.getLogger(RequestIdSerializer.class);
	 
	MessageDigest messageDigest = DigestUtils.getSha256Digest();
	 
	TreeMap<ByteBuffer,byte[]> fields = new TreeMap<ByteBuffer, byte[]>();
	

	@Override
	public <T> void serializeField(String key, T value) {
		byte[] keyHash = this.hashValue(key);
		byte[] valueHash = this.hashValue(value);
		
		// TODO remove in the future, it's just for diagnostic purposes
		//LOG.debug("Key Hash for " + key + ":" + Arrays.toString(ByteUtils.toUnsignedIntegerArray(keyHash)));
		//LOG.debug("Value Hash for " + value + ":" + Arrays.toString(ByteUtils.toUnsignedIntegerArray(valueHash)));
		
		fields.put(ByteBuffer.wrap(keyHash), valueHash);
		
	}
	
	/*
	 * Hash a single value, returning its sha256_hash.
	 */
	<T> byte[] hashValue(T value)
	{			
        byte[] bytes;
        if(value instanceof List)
        	return this.hashList((List<?>) value);
		if(value instanceof Long)
			bytes = this.serializeLong((Long) value);
		else if(value instanceof Principal)
			bytes = ((Principal) value).getValue();
		else if(value instanceof byte[])
			bytes = (byte[]) value;
		else	
			bytes = value.toString().getBytes();
				
		return DigestUtils.sha256(bytes);
	}
	
	byte[] hashList(List<?> value)
	{
		MessageDigest messageDigest;
		try {
			messageDigest = (MessageDigest) this.messageDigest.clone();
			
			for(Object item : value)
			{
				byte bytes[] = this.hashValue(item);
				
				messageDigest.update(bytes);
			}
			
			return messageDigest.digest();			
		} catch (CloneNotSupportedException e) {
			throw RequestIdError.create(RequestIdError.RequestIdErrorCode.CUSTOM_SERIALIZER_ERROR, e, e.getLocalizedMessage());
		}
		

	}
	
	
	byte[] serializeLong(Long value)
	{
		// 10 bytes is enough for a 64-bit number in leb128.
		byte[] buffer = new byte[10];
		
		ByteBuffer writeable = ByteBuffer.wrap(buffer);
		int nBytes = Leb128.writeUnsigned(writeable, value);
			
		
		return Arrays.copyOf(buffer, nBytes);
	}
	
	void hashFields()
	{
		
		ArrayList<ByteBuffer> keyValues = new ArrayList<ByteBuffer>();
		
		for(Map.Entry<ByteBuffer,byte[]> entry : fields.entrySet())
		{
			ByteBuffer key = entry.getKey();
						
			byte[] value = entry.getValue();
						
			ByteBuffer keyValue = (ByteBuffer) ByteBuffer.allocate(key.limit() + value.length).put(key).put(value).rewind();
			
			LOG.debug("KeyValue :" + Arrays.toString(ByteUtils.toUnsignedIntegerArray(keyValue.array())));
			
			keyValues.add(keyValue);
		}
		
		// Have to use custom comparator. Rust implementation is sorting using unsigned values
		// while ByteBuffer comparator is using signed bye array. Sort result is different there
		// so would be hash result
		keyValues.sort(new Comparator<ByteBuffer>(){
	        public int compare(ByteBuffer bytes1, ByteBuffer bytes2) {
	        	int result = 0;
	        	
	        	while(bytes1.limit() > 0 && bytes2.limit() > 0)
	        	{
	        		result = Long.compare(Byte.toUnsignedLong(bytes1.get()), Byte.toUnsignedLong(bytes2.get()));
	        		
	        		if(result != 0)
	        			break;
	        	}
	        	
	        	bytes1.rewind();
	        	bytes2.rewind();
	            
	        	return result;
	        }			
		});
		
		for(ByteBuffer value : keyValues)
		{			
			//LOG.debug("KeyValue Unsigned Sorted :" + Arrays.toString(ByteUtils.toUnsignedIntegerArray(value.array())));
			//LOG.debug("KeyValue Sorted :" + Arrays.toString(value.array()));
			
			messageDigest.update(value);
		}
	}

	/*
    Finish the hashing and returns the RequestId for the structure that was
    serialized.
    */
	RequestId finish() {
		return new RequestId(messageDigest.digest());
	}
	

}
