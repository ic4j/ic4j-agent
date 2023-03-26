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

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.ic4j.agent.hashtree.Label;
import org.ic4j.agent.identity.Identity;
import org.ic4j.agent.identity.Signature;
import org.ic4j.agent.replicaapi.CallRequestContent;
import org.ic4j.agent.replicaapi.Certificate;
import org.ic4j.agent.replicaapi.Delegation;
import org.ic4j.agent.replicaapi.Envelope;
import org.ic4j.agent.replicaapi.QueryContent;
import org.ic4j.agent.replicaapi.QueryResponse;
import org.ic4j.agent.replicaapi.ReadStateContent;
import org.ic4j.agent.replicaapi.ReadStateResponse;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.candid.ByteUtils;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.types.Principal;
import org.miracl.core.BLS12381.BLS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORParser;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

public final class Agent {
	static final byte[] IC_REQUEST_DOMAIN_SEPARATOR = "\nic-request".getBytes(StandardCharsets.UTF_8);
	static final byte[] IC_STATE_ROOT_DOMAIN_SEPARATOR = "\ric-state-root".getBytes(StandardCharsets.UTF_8);
	static final byte[] IC_ROOT_KEY;

	static final Integer DEFAULT_INGRESS_EXPIRY_DURATION = 300;
	static final Integer DEFAULT_PERMITTED_DRIFT = 60;

	static final Logger LOG = LoggerFactory.getLogger(Agent.class);

	ReplicaTransport transport;
	Duration ingressExpiryDuration;
	Identity identity;
	NonceFactory nonceFactory;
	Optional<byte[]> rootKey;
	
	boolean verify = true;
	
	static {
		try {
			IC_ROOT_KEY = Hex.decodeHex("308182301d060d2b0601040182dc7c0503010201060c2b0601040182dc7c05030201036100814c0e6ec71fab583b08bd81373c255c3c371b2e84863c98a4f1e08b74235d14fb5d9c0cd546d9685f913a0c0b2cc5341583bf4b4392e467db96d65b9bb4cb717112f8472e0d5a4d14505ffd7484b01291091c5f87b98883463f98091a0baaae".toCharArray());
		} catch (DecoderException e) {
			throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e);
		}
	}

	Agent(AgentBuilder builder) {
		int verifyResponse = BLS.init();
		
		if (verifyResponse!=0)
			throw AgentError.create(AgentError.AgentErrorCode.CERTIFICATE_VERIFICATION_FAILED);
		
		this.transport = builder.config.transport.get();

		if (builder.config.ingressExpiryDuration.isPresent())
			ingressExpiryDuration = builder.config.ingressExpiryDuration.get();
		else
			ingressExpiryDuration = Duration.ofSeconds(DEFAULT_INGRESS_EXPIRY_DURATION);

		this.identity = builder.config.identity;

		this.nonceFactory = builder.config.nonceFactory;	

		this.rootKey = Optional.of(IC_ROOT_KEY);

	}
	
	public void setVerify(boolean verify)
	{
		this.verify = verify;
	}

	Long getExpiryDate() {
		// TODO: evaluate if we need this on the agent side
		Duration permittedDrift = Duration.ofSeconds(DEFAULT_PERMITTED_DRIFT);

		return ((this.ingressExpiryDuration.plus(Duration.ofMillis(System.currentTimeMillis()))).minus(permittedDrift))
				.toNanos();
	}

	/*
	 * By default, the agent is configured to talk to the main Internet Computer,
	 * and verifies responses using a hard-coded public key.
	 * 
	 * This function will instruct the agent to ask the endpoint for its public key,
	 * and use that instead. This is required when talking to a local test instance,
	 * for example.
	 * 
	 * Only use this when you are _not_ talking to the main Internet Computer,
	 * otherwise you are prone to man-in-the-middle attacks! Do not call this
	 * function by default.*
	 */

	public void fetchRootKey() throws AgentError {
		Status status;
		try {
			if(this.getRootKey() != null && !Arrays.equals(this.getRootKey(),IC_ROOT_KEY))
				return;
			
			status = this.status().get();

			if (status.rootKey.isPresent())
				this.setRootKey(status.rootKey.get());
			else
				throw AgentError.create(AgentError.AgentErrorCode.NO_ROOT_KEY_IN_STATUS, status);
		} catch (InterruptedException | ExecutionException e) {
			LOG.error(e.getLocalizedMessage(), e);
			throw AgentError.create(AgentError.AgentErrorCode.TRANSPORT_ERROR, e);
		}

	}

	/*
	 * By default, the agent is configured to talk to the main Internet Computer,
	 * and verifies responses using a hard-coded public key.
	 * 
	 * Using this function you can set the root key to a known one if you know if
	 * beforehand.
	 */

	public synchronized void setRootKey(byte[] rootKey) throws AgentError {
		this.rootKey = Optional.of(rootKey);
	}

	byte[] getRootKey() throws AgentError {
		if (rootKey.isPresent())
			return rootKey.get();
		else
			throw AgentError.create(AgentError.AgentErrorCode.COULD_NOT_READ_ROOT_KEY);
	}

	byte[] constructMessage(RequestId requestId) {
		return ArrayUtils.addAll(IC_REQUEST_DOMAIN_SEPARATOR, requestId.get());
	}

	/*
	 * Calls and returns the information returned by the status endpoint of a
	 * replica.
	 */

	public CompletableFuture<Status> status() throws AgentError {
		ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());
		objectMapper.registerModule(new Jdk8Module());

		CompletableFuture<Status> response = new CompletableFuture<Status>();

		transport.status().whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {
					try {
						Status status = objectMapper.readValue(input.payload, Status.class);
						response.complete(status);
					}
					catch ( AgentError e) {
						response.completeExceptionally(e);
					}
					catch (Exception e) {
						LOG.debug(e.getLocalizedMessage());
						response.completeExceptionally(
								AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e, input));
					}

				} else {
					response.completeExceptionally(
							AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, input));
				}
			} else {
				response.completeExceptionally(ex);
			}

		});

		return response;
	}
	
	public String getIDL(Principal canisterId) throws AgentError
	{
		List<IDLValue> args = new ArrayList<IDLValue>();

		IDLArgs idlArgs = IDLArgs.create(args);

		byte[] buf = idlArgs.toBytes();

		CompletableFuture<byte[]> queryResponse = this.queryRaw(
				canisterId,
				canisterId, "__get_candid_interface_tmp_hack", buf, Optional.empty());

		try {
			byte[] queryOutput = queryResponse.get();

			IDLArgs outArgs = IDLArgs.fromBytes(queryOutput);
			
			return outArgs.getArgs().get(0).getValue();
		}catch(AgentError e)
		{
			throw e;
			
		} catch (InterruptedException e) {
			throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e);
		} catch (ExecutionException e) {
			throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e);
		}
	}
	
	public CompletableFuture<Response<byte[]>> queryRaw(Principal canisterId, Principal effectiveCanisterId, String method, Request<byte[]> request
			, Optional<Long> ingressExpiryDatetime) throws AgentError {
		QueryContent queryContent = new QueryContent();

		queryContent.queryRequest.methodName = method;
		queryContent.queryRequest.canisterId = canisterId;
		queryContent.queryRequest.arg = request.getPayload();
		queryContent.queryRequest.sender = this.identity.sender();

		if (ingressExpiryDatetime.isPresent())
			queryContent.queryRequest.ingressExpiry = ingressExpiryDatetime.get();
		else
			queryContent.queryRequest.ingressExpiry = this.getExpiryDate();

		CompletableFuture<Response<byte[]>> response = new CompletableFuture<Response<byte[]>>();

		this.queryEndpoint(effectiveCanisterId, queryContent, request.getHeaders()).whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {
					if (input.replied.isPresent()) {
											
						byte[] out = input.replied.get().arg;
						
						Response<byte[]> queryResponse = new Response<byte[]>(out, input.headers);
						response.complete(queryResponse);
					} else if (input.rejected.isPresent()) {
						response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.REPLICA_ERROR,
								input.rejected.get().rejectCode, input.rejected.get().rejectMessage));
					} else
						response.completeExceptionally(
								AgentError.create(AgentError.AgentErrorCode.INVALID_REPLICA_STATUS));

				} else {
					response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.INVALID_REPLICA_STATUS));
				}
			} else {
				response.completeExceptionally(ex);
			}
		});
		return response;
	}	

	public CompletableFuture<byte[]> queryRaw(Principal canisterId, Principal effectiveCanisterId, String method,
			byte[] arg, Optional<Long> ingressExpiryDatetime) throws AgentError {
		QueryContent queryContent = new QueryContent();

		queryContent.queryRequest.methodName = method;
		queryContent.queryRequest.canisterId = canisterId;
		queryContent.queryRequest.arg = arg;
		queryContent.queryRequest.sender = this.identity.sender();

		if (ingressExpiryDatetime.isPresent())
			queryContent.queryRequest.ingressExpiry = ingressExpiryDatetime.get();
		else
			queryContent.queryRequest.ingressExpiry = this.getExpiryDate();

		CompletableFuture<byte[]> response = new CompletableFuture<byte[]>();

		this.queryEndpoint(effectiveCanisterId, queryContent, null).whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {
					if (input.replied.isPresent()) {
						byte[] out = input.replied.get().arg;
						response.complete(out);
					} else if (input.rejected.isPresent()) {
						response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.REPLICA_ERROR,
								input.rejected.get().rejectCode, input.rejected.get().rejectMessage));
					} else
						response.completeExceptionally(
								AgentError.create(AgentError.AgentErrorCode.INVALID_REPLICA_STATUS));

				} else {
					response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.INVALID_REPLICA_STATUS));
				}
			} else {
				response.completeExceptionally(ex);
			}
		});
		return response;
	}

	public CompletableFuture<QueryResponse> queryEndpoint(Principal effectiveCanisterId, QueryContent request, Map<String,String> headers)
			throws AgentError {

		RequestId requestId = RequestId.toRequestId(request);

		byte[] msg = this.constructMessage(requestId);

		Signature signature = this.identity.sign(msg);

		ObjectMapper objectMapper = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());

		ObjectWriter objectWriter = objectMapper.writerFor(Envelope.class).withAttribute("request_type", "query");

		Envelope<QueryContent> envelope = new Envelope<QueryContent>();

		envelope.content = request;
		envelope.senderPubkey = signature.publicKey;
		envelope.senderSig = signature.signature;

		byte[] bytes = null;
		try {
			bytes = objectWriter.writeValueAsBytes(envelope);
		} catch (JsonProcessingException e) {
			throw AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e, envelope);
			// normally, rethrow exception here - or don't catch it at all.
		}

		CompletableFuture<QueryResponse> response = new CompletableFuture<QueryResponse>();

		transport.query(effectiveCanisterId, bytes, headers).whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {
					try {
						QueryResponse queryResponse = objectMapper.readValue(input.payload, QueryResponse.class);
						queryResponse.headers = input.headers;
						response.complete(queryResponse);
					}
					catch ( AgentError e) {
						response.completeExceptionally(e);
					}
					catch (Exception e) {
						LOG.debug(e.getLocalizedMessage(), e);
						response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.MESSAGE_ERROR, e,
								new String(input.payload, StandardCharsets.UTF_8)));
					}

				} else {
					response.completeExceptionally(
							AgentError.create(AgentError.AgentErrorCode.TRANSPORT_ERROR, "Payload is empty"));
				}
			} else {
				response.completeExceptionally(ex);
			}

		});

		return response;
	}

	
	/*
	 * The simplest way to do an update call; sends a byte array and will return a
	 * RequestId. The RequestId should then be used for request_status (most likely
	 * in a loop).
	 */

	public CompletableFuture<Response<RequestId>> updateRaw(Principal canisterId, Principal effectiveCanisterId, String method, 
			Request<byte[]> request,
			 Optional<Long> ingressExpiryDatetime) throws AgentError {
		CallRequestContent callRequestContent = new CallRequestContent();

		callRequestContent.callRequest.methodName = method;
		callRequestContent.callRequest.canisterId = canisterId;
		callRequestContent.callRequest.arg = request.getPayload();
		callRequestContent.callRequest.sender = this.identity.sender();

		if (this.nonceFactory != null)
			callRequestContent.callRequest.nonce = Optional.of(nonceFactory.generate());

		if (ingressExpiryDatetime.isPresent())
			callRequestContent.callRequest.ingressExpiry = ingressExpiryDatetime.get();
		else
			callRequestContent.callRequest.ingressExpiry = this.getExpiryDate();

		CompletableFuture<Response<RequestId>> response = new CompletableFuture<Response<RequestId>>();

		this.callEndpoint(effectiveCanisterId, callRequestContent, request.getHeaders()).whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {
					Response<RequestId> updateResponse = new Response<RequestId>(input.requestId,input.headers);
					response.complete(updateResponse);
				} else {
					response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.INVALID_REPLICA_STATUS));
				}
			} else {
				response.completeExceptionally(ex);
			}
		});
		return response;
	}
	
	/*
	 * The simplest way to do an update call; sends a byte array and will return a
	 * RequestId. The RequestId should then be used for request_status (most likely
	 * in a loop).
	 */

	public CompletableFuture<RequestId> updateRaw(Principal canisterId, Principal effectiveCanisterId, String method,
			byte[] arg, Optional<Long> ingressExpiryDatetime) throws AgentError {
		CallRequestContent callRequestContent = new CallRequestContent();

		callRequestContent.callRequest.methodName = method;
		callRequestContent.callRequest.canisterId = canisterId;
		callRequestContent.callRequest.arg = arg;
		callRequestContent.callRequest.sender = this.identity.sender();

		if (this.nonceFactory != null)
			callRequestContent.callRequest.nonce = Optional.of(nonceFactory.generate());

		if (ingressExpiryDatetime.isPresent())
			callRequestContent.callRequest.ingressExpiry = ingressExpiryDatetime.get();
		else
			callRequestContent.callRequest.ingressExpiry = this.getExpiryDate();

		CompletableFuture<RequestId> response = new CompletableFuture<RequestId>();

		this.callEndpoint(effectiveCanisterId, callRequestContent, null).whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {
					response.complete(input.requestId);
				} else {
					response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.INVALID_REPLICA_STATUS));
				}
			} else {
				response.completeExceptionally(ex);
			}
		});
		return response;
	}

	public CompletableFuture<UpdateResponse> callEndpoint(Principal effectiveCanisterId, CallRequestContent request, Map<String,String> headers)
			throws AgentError {
		RequestId requestId = RequestId.toRequestId(request);
		byte[] msg = this.constructMessage(requestId);

		Signature signature = this.identity.sign(msg);

		ObjectMapper objectMapper = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());

		ObjectWriter objectWriter = objectMapper.writerFor(Envelope.class).withAttribute("request_type", "call");

		Envelope<CallRequestContent> envelope = new Envelope<CallRequestContent>();

		envelope.content = request;
		envelope.senderPubkey = signature.publicKey;
		envelope.senderSig = signature.signature;

		byte[] bytes = null;
		try {
			bytes = objectWriter.writeValueAsBytes(envelope);
		} catch (JsonProcessingException e) {
			throw AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e, envelope);
			// normally, rethrow exception here - or don't catch it at all.
		}

		CompletableFuture<UpdateResponse> response = new CompletableFuture<UpdateResponse>();

		transport.call(effectiveCanisterId, bytes, requestId, headers).whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {
					UpdateResponse updateResponse = new UpdateResponse();
					updateResponse.requestId = requestId;
					updateResponse.headers = input.headers;
					
					response.complete(updateResponse);
				} else {
					response.completeExceptionally(
							AgentError.create(AgentError.AgentErrorCode.TRANSPORT_ERROR, "Payload is empty"));
				}
			} else {
				response.completeExceptionally(ex);
			}

		});

		return response;
	}

	public CompletableFuture<Response<RequestStatusResponse>> requestStatusRaw(RequestId requestId, Principal effectiveCanisterId, Request<Void> request)
			throws AgentError {
		return this.requestStatusRaw(requestId, effectiveCanisterId, false, request);
	}
	
	public CompletableFuture<Response<RequestStatusResponse>> requestStatusRaw(RequestId requestId, Principal effectiveCanisterId, boolean disableRangeCheck, Request<Void> request)
			throws AgentError {
		List<List<byte[]>> paths = new ArrayList<List<byte[]>>();

		List<byte[]> path = new ArrayList<byte[]>();
		path.add("request_status".getBytes());
		path.add(requestId.get());

		paths.add(path);

		CompletableFuture<Response<RequestStatusResponse>> response = new CompletableFuture<Response<RequestStatusResponse>>();

		this.readStateRaw(effectiveCanisterId, paths,disableRangeCheck, request.getHeaders()).whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {

					try {
						RequestStatusResponse requestStatusResponse = ResponseAuthentication.lookupRequestStatus(input.certificate,
								requestId);
						
						Response<RequestStatusResponse> stateResponse = new Response<RequestStatusResponse>(requestStatusResponse, input.headers);
						
						response.complete(stateResponse);
					} catch (AgentError e) {
						response.completeExceptionally(e);
					}
					catch (Exception e) {						
						response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR,e));
					}
				} else {
					response.completeExceptionally(
							AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, input));
				}
			} else {
				if(ex instanceof AgentError)
				{			
					AgentError e = (AgentError) ex;
					if((e.code == AgentError.AgentErrorCode.CERTIFICATE_VERIFICATION_FAILED || e.code == AgentError.AgentErrorCode.CERTIFICATE_NOT_AUTHORIZED) && e.getResponse() != null)
					{
						if(e.getResponse() instanceof CertificateResponse)
						{
							CertificateResponse certificateResponse = (CertificateResponse) e.getResponse();

							try {
								RequestStatusResponse requestStatusResponse = ResponseAuthentication.lookupRequestStatus(certificateResponse.certificate,
										requestId);
								
								Response<RequestStatusResponse> stateResponse = new Response<RequestStatusResponse>(requestStatusResponse, certificateResponse.headers);
								
								e.setResponse(stateResponse);
							}
							 catch (AgentError e1) {
								e1.initCause(e);
								response.completeExceptionally(e1);
							}
							catch (Exception e1) {	
								e1.initCause(e);
								response.completeExceptionally(e1);
							}						
						}
					}
					response.completeExceptionally(e);
				}
				else
					response.completeExceptionally(ex);
			}
		});

		return response;
	}
	
	public CompletableFuture<RequestStatusResponse> requestStatusRaw(RequestId requestId, Principal effectiveCanisterId)
			throws AgentError {
		return this.requestStatusRaw(requestId, effectiveCanisterId, false);
	}
	
	public CompletableFuture<RequestStatusResponse> requestStatusRaw(RequestId requestId, Principal effectiveCanisterId, boolean disableRangeCheck)
			throws AgentError {
		List<List<byte[]>> paths = new ArrayList<List<byte[]>>();

		List<byte[]> path = new ArrayList<byte[]>();
		path.add("request_status".getBytes());
		path.add(requestId.get());

		paths.add(path);

		CompletableFuture<RequestStatusResponse> response = new CompletableFuture<RequestStatusResponse>();

		this.readStateRaw(effectiveCanisterId, paths,disableRangeCheck, null).whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {

					try {
						RequestStatusResponse requestStatusResponse = ResponseAuthentication.lookupRequestStatus(input.certificate,
								requestId);
						response.complete(requestStatusResponse);
					} catch (AgentError e) {						
						response.completeExceptionally(e);
					}
					catch (Exception e) {						
						response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR,e));
					}					
				} else {
					response.completeExceptionally(
							AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, input));
				}
			} else {
				if(ex instanceof AgentError)
				{			
					AgentError e = (AgentError) ex;
					if((e.code == AgentError.AgentErrorCode.CERTIFICATE_VERIFICATION_FAILED || e.code == AgentError.AgentErrorCode.CERTIFICATE_NOT_AUTHORIZED) && e.getResponse() != null)
					{
						if(e.getResponse() instanceof CertificateResponse)
						{
							CertificateResponse certificateResponse = (CertificateResponse) e.getResponse();
							try {
								RequestStatusResponse requestStatusResponse = ResponseAuthentication.lookupRequestStatus(certificateResponse.certificate,
										requestId);
								
								Response<RequestStatusResponse> stateResponse = new Response<RequestStatusResponse>(requestStatusResponse, certificateResponse.headers);
								
								e.setResponse(stateResponse);
							}
							 catch (AgentError e1) {
								e.initCause(e1);
								response.completeExceptionally(e);
							}
							catch (Exception e1) {	
								e.initCause(e1);
								response.completeExceptionally(e);
							}
						}
					}
					response.completeExceptionally(e);
				}
				else
					response.completeExceptionally(ex);
			}
		});

		return response;
	}
	
	public CompletableFuture<CertificateResponse> readStateRaw(Principal effectiveCanisterId, List<List<byte[]>> paths, Map<String,String> headers)
			throws AgentError {
		
		return this.readStateRaw(effectiveCanisterId, paths, false, headers);
		
	}

	public CompletableFuture<CertificateResponse> readStateRaw(Principal effectiveCanisterId, List<List<byte[]>> paths, boolean disableRangeCheck,  Map<String,String> headers)
			throws AgentError {
		ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());
		objectMapper.registerModule(new Jdk8Module());

		ReadStateContent readStateContent = new ReadStateContent();

		readStateContent.readStateRequest.paths = paths;
		readStateContent.readStateRequest.sender = this.identity.sender();
		readStateContent.readStateRequest.ingressExpiry = this.getExpiryDate();

		CompletableFuture<CertificateResponse> response = new CompletableFuture<CertificateResponse>();

		this.readStateEndpoint(effectiveCanisterId, readStateContent, headers, ReadStateResponse.class)
				.whenComplete((input, ex) -> {
					if (ex == null) {
						if (input != null) {
							try {
								Certificate cert = objectMapper.readValue(input.state.certificate, Certificate.class);
													
								CertificateResponse certificateResponse = new CertificateResponse();
								certificateResponse.certificate = cert;
								certificateResponse.headers = input.headers;	
								
								try {
									if(this.verify)
										this.verify(cert,effectiveCanisterId,disableRangeCheck );
									
									response.complete(certificateResponse);									
								}catch ( AgentError e) {
									if(e.code == AgentError.AgentErrorCode.CERTIFICATE_VERIFICATION_FAILED || e.code == AgentError.AgentErrorCode.CERTIFICATE_NOT_AUTHORIZED)
										e.setResponse(certificateResponse);
									response.completeExceptionally(e);
								}
								
							}catch ( AgentError e) {
								response.completeExceptionally(e);
							}
							catch (IOException  e) {
								LOG.debug(e.getLocalizedMessage());
								response.completeExceptionally(
										AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e, input));
							}
							catch (Exception  e) {
								LOG.debug(e.getLocalizedMessage());
								response.completeExceptionally(
										AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e));
							}							

						} else {
							response.completeExceptionally(
									AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, input));
						}
					} else {
						response.completeExceptionally(ex);
					}
				});
		return response;
	}

	public void verify(Certificate certificate, Principal effectiveCanisterId, boolean disableRangeCheck) throws AgentError {
		
		byte[] sig = certificate.signature;
		
		byte[] rootHash = certificate.tree.digest();
		
		byte[] msg = ArrayUtils.addAll(IC_STATE_ROOT_DOMAIN_SEPARATOR, rootHash);
		
		byte[] derKey = this.checkDelegation(certificate.delegation,effectiveCanisterId,disableRangeCheck);
		
		byte[] key = ResponseAuthentication.extractDer(derKey);		
		
		int[] unsignedSig = ByteUtils.toUnsignedIntegerArray(sig);
		int[] unsignedMsg = ByteUtils.toUnsignedIntegerArray(msg);
		int[] unsignedKey = ByteUtils.toUnsignedIntegerArray(key);
		
		int verifyResponse = BLS.core_verify(sig,msg,key);
		
		if (verifyResponse==0)
			return;
		else
			throw AgentError.create(AgentError.AgentErrorCode.CERTIFICATE_VERIFICATION_FAILED);
		
	}

	private byte[] checkDelegation(Optional<Delegation> delegation, Principal effectiveCanisterId, boolean disableRangeCheck) {
		if(delegation != null && delegation.isPresent())
		{
			ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());
			objectMapper.registerModule(new Jdk8Module());
			
			Certificate certificate;
			try {
				certificate = objectMapper.readValue(delegation.get().certificate, Certificate.class);					
			} catch (Exception e) {
				throw AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e, delegation.get().certificate); 
			}
			
			this.verify(certificate, effectiveCanisterId, disableRangeCheck);
			
			List<Label> path = new ArrayList<Label>();
			path.add(new Label("subnet"));			
			path.add(new Label(delegation.get().subnetId));
			path.add(new Label("canister_ranges"));
			
			byte[] canisterRange = ResponseAuthentication.lookupValue(certificate,path);
			
			try {	
				
				TypeReference<HashMap<Principal, Principal>> typeRef 
				  = new TypeReference<HashMap<Principal, Principal>>() {};
				
				List<PrincipalRange> ranges =  new ArrayList <> ();
				
				List<List<byte[]>> rangesJson = new ArrayList <> ();
						
				rangesJson = objectMapper.readValue(canisterRange, List.class);
				
				for (Iterator <List<byte[]>> iterator = rangesJson.iterator(); iterator.hasNext();) {
					List<byte[]> rangeJson = (List<byte[]>) iterator.next();
					
					PrincipalRange range = new PrincipalRange();
					
					range.low = Principal.from(rangeJson.get(0));
					range.high = Principal.from(rangeJson.get(1));
					ranges.add(range);
			    }
				
				if(!disableRangeCheck && ! this.principalIsWithinRanges(effectiveCanisterId, ranges))
					throw AgentError.create(AgentError.AgentErrorCode.CERTIFICATE_NOT_AUTHORIZED); 
						
			} catch (Exception e) {
				throw AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e, canisterRange.toString()); 
			}
			
			path = new ArrayList<Label>();
			path.add(new Label("subnet"));			
			path.add(new Label(delegation.get().subnetId));
			path.add(new Label("public_key"));
			
			return ResponseAuthentication.lookupValue(certificate,path);			
		}
		else	
			return this.getRootKey();
	}
	
	boolean principalIsWithinRanges(Principal principal, List<PrincipalRange> ranges)
	{	
		for (Iterator<PrincipalRange> iterator = ranges.iterator(); iterator.hasNext();) {
			PrincipalRange range = iterator.next();	

			if(ByteUtils.compareByteArrays(principal.getValue(), range.low.getValue()) >= 0 && ByteUtils.compareByteArrays(principal.getValue(), range.high.getValue()) <= 0)
				return true;
		}
		
		return false;		
	}
	

	public <T> CompletableFuture<StateResponse<T>> readStateEndpoint(Principal effectiveCanisterId, ReadStateContent request, Map<String,String> headers,
			Class<T> clazz) throws AgentError {

		RequestId requestId = RequestId.toRequestId(request);

		byte[] msg = this.constructMessage(requestId);

		Signature signature = this.identity.sign(msg);

		ObjectMapper objectMapper = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());

		ObjectWriter objectWriter = objectMapper.writerFor(Envelope.class).withAttribute("request_type", "read_state");

		Envelope<ReadStateContent> envelope = new Envelope<ReadStateContent>();

		envelope.content = request;
		envelope.senderPubkey = signature.publicKey;
		envelope.senderSig = signature.signature;

		byte[] bytes = null;
		try {
			bytes = objectWriter.writeValueAsBytes(envelope);
		} catch (JsonProcessingException e) {
			throw AgentError.create(AgentError.AgentErrorCode.INVALID_CBOR_DATA, e, envelope);
			// normally, rethrow exception here - or don't catch it at all.
		}

		CompletableFuture<StateResponse<T>> response = new CompletableFuture<StateResponse<T>>();

		transport.readState(effectiveCanisterId, bytes, headers).whenComplete((input, ex) -> {
			if (ex == null) {
				if (input != null) {
					try {
						T readStateResponse = objectMapper.readValue(input.payload, clazz);
						
						StateResponse<T> stateResponse = new StateResponse<T>();
						
						stateResponse.state = readStateResponse;
						stateResponse.headers = input.headers;
						response.complete(stateResponse);
					} catch (Exception e) {
						LOG.debug(e.getLocalizedMessage(), e);
						response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.MESSAGE_ERROR, e,
								new String(input.payload, StandardCharsets.UTF_8)));
					}

				} else {
					response.completeExceptionally(
							AgentError.create(AgentError.AgentErrorCode.TRANSPORT_ERROR, "Payload is empty"));
				}
			} else {
				response.completeExceptionally(ex);
			}

		});

		return response;
	}
	
	public static String cborToJson(byte[] input) throws IOException {
		CBORFactory cborFactory = new CBORFactory();
		CBORParser cborParser = cborFactory.createParser(input);
		JsonFactory jsonFactory = new JsonFactory();
		StringWriter stringWriter = new StringWriter();
		JsonGenerator jsonGenerator = jsonFactory.createGenerator(stringWriter);
	while (cborParser.nextToken() != null) {
		jsonGenerator.copyCurrentEvent(cborParser);
	}
	jsonGenerator.flush();
	return stringWriter.toString();
}	
	
	
	
	public void close()
	{
		if(this.transport != null)
			this.transport.close();
	}
	
	public class UpdateResponse{
		public RequestId requestId;
		public Map<String,String> headers;
	}
	
	public class StateResponse<T>{
		public T state;
		public Map<String,String> headers;
	}	
	
	public class CertificateResponse{
		public Certificate certificate;
		public Map<String,String> headers;
	}
	
	class PrincipalRange{
		Principal low;
		Principal high;
	}

}
