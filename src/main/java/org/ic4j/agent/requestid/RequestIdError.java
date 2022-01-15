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

import java.text.MessageFormat;
import java.util.ResourceBundle;

public final class RequestIdError extends Error {
	private static final long serialVersionUID = 1L;
	
	final static String RESOURCE_BUNDLE_FILE = "dfinity_requestid";
	static ResourceBundle properties;
	
	RequestIdErrorCode code;

	static {
		properties = ResourceBundle.getBundle(RESOURCE_BUNDLE_FILE);
	}
	
	public static RequestIdError create(RequestIdErrorCode code, Object... args) {

		String message = properties.getString(code.label);
		// set arguments
		message = MessageFormat.format(message, args);

		return new RequestIdError(code, message);
	}
	
	public static RequestIdError create(RequestIdErrorCode code,Throwable t, Object... args) {

		String message = properties.getString(code.label);
		// set arguments
		message = MessageFormat.format(message, args);

		return new RequestIdError(code,t, message);
	}	

	private RequestIdError(RequestIdErrorCode code, String message) {	
		super(message);
		this.code = code;
	}
	
	private RequestIdError(RequestIdErrorCode code, Throwable t, String message) {
		super(message, t);
		this.code = code;
	}
	
	public RequestIdErrorCode getCode() {
		return code;
	}
	
	public enum RequestIdErrorCode {
		CUSTOM_SERIALIZER_ERROR("CustomSerializerError"),
		EMPTY_SERIALIZER("EmptySerializer"),
		INVALID_STATE("InvalidState");
		
		public String label;

		RequestIdErrorCode(String label) {
			this.label = label;
		}
			
	}		
}
