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

import java.text.MessageFormat;
import java.util.ResourceBundle;

public final class AgentError extends Error {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	final static String RESOURCE_BUNDLE_FILE = "dfinity_agent";
	static ResourceBundle properties;
	
	AgentErrorCode code;
	
	Object response;

	static {
		properties = ResourceBundle.getBundle(RESOURCE_BUNDLE_FILE);
	}

	public static AgentError create(AgentErrorCode code, Object... args) {

		String message = properties.getString(code.label);
		// set arguments
		message = MessageFormat.format(message, args);

		return new AgentError(code, message);
	}
	
	public static AgentError create(AgentErrorCode code,Throwable t, Object... args) {

		String message = properties.getString(code.label);
		// set arguments
		message = MessageFormat.format(message, args);

		return new AgentError(code,t, message);
	}	

	private AgentError(AgentErrorCode code, String message) {
		super(message);
		this.code = code;
	}
	
	private AgentError(AgentErrorCode code, Throwable t, String message) {
		super(message, t);
		this.code = code;
	}
	
	public AgentErrorCode getCode() {
		return code;
	}
	
	/**
	 * @return the response
	 */
	public Object getResponse() {
		return response;
	}

	/**
	 * @param response the response to set
	 */
	public void setResponse(Object response) {
		this.response = response;
	}

	public enum AgentErrorCode {
		INVALID_REPLICA_URL("InvalidReplicaUrl"),
		TIMEOUT_WAITING_FOR_RESPONSE("TimeoutWaitingForResponse"),
		URL_SYNTAX_ERROR("UrlSyntaxError"),	
		URL_PARSE_ERROR("UrlParseError"),
		PRINCIPAL_ERROR("PrincipalError"),
		REPLICA_ERROR("ReplicaError"),
		INVALID_CBOR_DATA("InvalidCborData"),
		HTTP_ERROR("HttpError"),
		CANNOT_USE_AUTHENTICATION_ON_NONSECURE_URL("CannotUseAuthenticationOnNonSecureUrl"),
		AUTHENTICATION_ERROR("AuthenticationError"),
		INVALID_REPLICA_STATUS("InvalidReplicaStatus"),
		REQUEST_STATUS_DONE_NO_REPLY("RequestStatusDoneNoReply"),
		MESSAGE_ERROR("MessageError"),
		CUSTOM_ERROR("CustomError"),
		LEB128_READ_ERROR("Leb128ReadError"),
		UTF8_READ_ERROR("Utf8ReadError"),
		LOOKUP_PATH_ABSENT("LookupPathAbsent"),
		LOOKUP_PATH_UNKNOWN("LookupPathUnknown"),
		LOOKUP_PATH_ERROR("LookupPathError"),
		INVALID_REQUEST_STATUS("InvalidRequestStatus"),
		CERTIFICATE_VERIFICATION_FAILED("CertificateVerificationFailed"),
		CERTIFICATE_NOT_AUTHORIZED("CertificateNotAuthorized"),
		CERTIFICATE_OUTDATED("CertificateOutdated"),
		DER_KEY_LENGTH_MISMATCH("DerKeyLengthMismatch"),
		DER_PREFIX_MISMATCH("DerKeyLengthMismatch"),
		NO_ROOT_KEY_IN_STATUS("NoRootKeyInStatus"),
		COULD_NOT_READ_ROOT_KEY("CouldNotReadRootKey"),
		MISSING_REPLICA_TRANSPORT("MissingReplicaTransport"),
		TRANSPORT_ERROR("TransportError"),
		MISSING_SIGNATURE("MissingSignature"),
		MALFORMED_SIGNATURE("MalformedSignature"),
		MALFORMED_PUBLIC_KEY("MalformedSignature"),
		TOO_MANY_SIGNATURES("TooManySignatures"),
		QUERY_SIGNATURE_VERIFICATION_FAILED("QuerySignatureVerificationFailed")
		;
		
		public String label;

		AgentErrorCode(String label) {
			this.label = label;
		}
			
	}	

}
