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

package org.ic4j.agent;

import java.util.HashMap;
import java.util.Map;

public final class Response<T> {
	public final static String X_IC_NODE_ID_HEADER =  "x-ic-node-id";
	public final static String X_IC_SUBNET_ID_HEADER =  "x-ic-subnet-id";
	public final static String X_IC_CANISTER_ID_HEADER =  "x-ic-canister-id";
	
	Map<String,String> headers;
	
	T payload;
	
	public Response(T payload, Map<String, String> headers) {
		this.headers = headers;
		this.payload = payload;
	}
	
	public Response( T payload) {
		this.headers = new HashMap<String, String>();
		this.payload = payload;
	}	

	/**
	 * @return the headers
	 */
	public Map<String, String> getHeaders() {
		return headers;
	}

	/**
	 * @return the payload
	 */
	public T getPayload() {
		return payload;
	}


	


}
