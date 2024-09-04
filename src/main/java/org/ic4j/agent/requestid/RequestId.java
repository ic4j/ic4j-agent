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

import org.ic4j.agent.Hex;
import org.ic4j.agent.Serialize;

public final class RequestId {
	byte[] value;
	
	
	RequestId(byte[] value)
	{
		this.value = value;
	}
	
	/*
	Derive the request ID from a serializable data structure.
	
	See https://hydra.dfinity.systems//build/268411/download/1/dfinity/spec/public/index.html#api-request-id
	
	# Warnings

	The argument type simply needs to be serializable; the function
	does NOT sift between fields to include them or not and assumes
	the passed value only includes fields that are not part of the
	envelope and should be included in the calculation of the request
	id.
	*/
	
	public static <T extends Serialize> RequestId toRequestId(T value) throws RequestIdError
	{
		RequestIdSerializer serializer = new RequestIdSerializer();
		value.serialize(serializer);
		
		serializer.hashFields();
		return serializer.finish();
	}
	
	public static RequestId fromHex(byte[] value) 
	{

		if(value == null)
			throw RequestIdError.create(RequestIdError.RequestIdErrorCode.EMPTY_SERIALIZER);
			
		return new RequestId(value);

	}	
	
	public static RequestId fromHexString(String hexValue) 
	{

			if(hexValue == null)
				throw RequestIdError.create(RequestIdError.RequestIdErrorCode.EMPTY_SERIALIZER);
			
			return new RequestId((Hex.decodeHex(hexValue)));
	}	
	
	public byte[] get()
	{
		return this.value;
	}
	
	public String toString()
	{
		return this.value.toString();
	}
	
	public String toHexString()
	{
		return Hex.encodeHexString(this.value);
	}	

}
