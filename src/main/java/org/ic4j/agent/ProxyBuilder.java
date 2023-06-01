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
import java.io.Reader;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.ic4j.agent.annotations.Canister;
import org.ic4j.agent.annotations.EffectiveCanister;
import org.ic4j.agent.annotations.Transport;
import org.ic4j.agent.annotations.QUERY;
import org.ic4j.agent.annotations.ResponseClass;
import org.ic4j.agent.annotations.UPDATE;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.identity.AnonymousIdentity;
import org.ic4j.agent.identity.BasicIdentity;
import org.ic4j.agent.identity.Identity;
import org.ic4j.agent.identity.PemError;
import org.ic4j.agent.identity.Secp256k1Identity;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.agent.annotations.Argument;
import org.ic4j.candid.ObjectDeserializer;
import org.ic4j.candid.ObjectSerializer;
import org.ic4j.candid.annotations.Ignore;
import org.ic4j.candid.annotations.Modes;
import org.ic4j.candid.annotations.Name;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLParser;
import org.ic4j.candid.parser.IDLType;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.candid.pojo.PojoDeserializer;
import org.ic4j.candid.pojo.PojoSerializer;
import org.ic4j.candid.types.Mode;
import org.ic4j.candid.types.Type;
import org.ic4j.types.Func;
import org.ic4j.types.Principal;
import org.ic4j.types.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProxyBuilder {

	static final Logger LOG = LoggerFactory.getLogger(ProxyBuilder.class);

	static int WAITER_TIMEOUT = 60;
	static int WAITER_SLEEP = 5;

	Agent agent;
	Principal effectiveCanisterId;
	Principal canisterId;
	Optional<Long> ingressExpiryDatetime;
	IDLType serviceType;
	
	Map<Principal,IDLType> serviceTypes = new WeakHashMap<Principal,IDLType>();
	
	Path idlFile;
	
	private boolean disableRangeCheck = false;
	
	private boolean loadIDL = false;

	private Waiter waiter;

	ProxyBuilder(Agent agent) {
		Security.addProvider(new BouncyCastleProvider());

		this.agent = agent;
		this.ingressExpiryDatetime = Optional.empty();
	}

	ProxyBuilder(Agent agent, Principal canisterId) {
		Security.addProvider(new BouncyCastleProvider());

		this.agent = agent;
		this.canisterId = canisterId;
		this.effectiveCanisterId = canisterId.clone();
		this.ingressExpiryDatetime = Optional.empty();
	}

	ProxyBuilder(Principal canisterId) {
		Security.addProvider(new BouncyCastleProvider());

		this.canisterId = canisterId;
		this.effectiveCanisterId = canisterId.clone();
		this.ingressExpiryDatetime = Optional.empty();
	}

	ProxyBuilder() {
		Security.addProvider(new BouncyCastleProvider());

		this.ingressExpiryDatetime = Optional.empty();
	}

	public static ProxyBuilder create(Agent agent) {
		return new ProxyBuilder(agent);
	}

	public static ProxyBuilder create(Agent agent, Principal canisterId) {
		return new ProxyBuilder(agent, canisterId);
	}

	public static ProxyBuilder create(Principal canisterId) {
		return new ProxyBuilder(canisterId);
	}

	public static ProxyBuilder create() {
		return new ProxyBuilder();
	}

	public ProxyBuilder effectiveCanisterId(Principal effectiveCanisterId) {
		this.effectiveCanisterId = effectiveCanisterId;
		return this;
	}

	/**
	 * Takes a SystemTime converts it to a Duration by calling
	 * duration_since(UNIX_EPOCH) to learn about where in time this SystemTime lies.
	 * The Duration is converted to nanoseconds and stored in ingressExpiryDatetime
	 */
	public ProxyBuilder expireAt(LocalDateTime time) {
		this.ingressExpiryDatetime = Optional.of(time.toEpochSecond(ZoneOffset.UTC));

		return this;
	}

	/**
	 * Takes a Duration (i.e. 30 sec/5 min 30 sec/1 h 30 min, etc.) and adds it to
	 * the Duration of the current SystemTime since the UNIX_EPOCH Subtracts a
	 * permitted drift from the sum to account for using system time and not block
	 * time. Converts the difference to nanoseconds and stores in
	 * ingressExpiryDatetime
	 */

	public ProxyBuilder expireAfter(Duration duration) {
		Duration permittedDrift = Duration.ofSeconds(Agent.DEFAULT_PERMITTED_DRIFT);

		this.ingressExpiryDatetime = Optional
				.of((Duration.ofMillis(System.currentTimeMillis()).plus(duration).minus(permittedDrift)).toNanos());

		return this;
	}

	public ProxyBuilder waiter(Waiter waiter) {
		this.waiter = waiter;

		return this;
	}
	
	
	/* Candid IDL file describing service
	 * 
	 */
	public ProxyBuilder idlFile(Path idlFile) {
		this.idlFile = idlFile;

		this.loadIDL = true;
		return this;
	}
	
	/* Disable Range Check
	 * 
	 */
	public ProxyBuilder disableRangeCheck(boolean disableRangeCheck) {
		this.disableRangeCheck = disableRangeCheck;

		return this;
	}
	
	/* Disable Range Check
	 * 
	 */
	public ProxyBuilder loadIDL(boolean loadIDL) {
		this.loadIDL = loadIDL;

		return this;
	}	
	
	public <T> FuncProxy<T> getFuncProxy(Func func) {
		if (func != null && func.getPrincipal() != null)
			this.canisterId = func.getPrincipal();
		
		Service service = new Service(this.canisterId);
		
		ServiceProxy serviceProxy = this.getServiceProxy(service);
		
		return serviceProxy.getFuncProxy(func);
	}
	
	public <T> FuncProxy<T> getFuncProxy(Func func, Class<?> interfaceClass) {
		if (func != null && func.getPrincipal() != null)
			this.canisterId = func.getPrincipal();
		
		AgentInvocationHandler agentInvocationHandler = this.getAgentInvocationHandler(interfaceClass);	
		
		Object proxy = this.getProxy(interfaceClass);
		
		Method[] methods = interfaceClass.getDeclaredMethods();
		
		for(Method method : methods)
		{
			String name = method.getName();
			
			if (method.isAnnotationPresent(Name.class)) {
				Name nameAnnotation = method.getAnnotation(Name.class);
				name = nameAnnotation.value();
			}
			
			if(name.equals(func.getMethod()))
				return new FuncProxy<T>(proxy , method, agentInvocationHandler);
		}
		
		throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Candid function %s not defined", func.getMethod());		
	}

	public <T> T getProxy(Service service, Class<T> interfaceClass) {
		if (service != null)
			this.canisterId = service.getPrincipal();
		

		return this.getProxy(interfaceClass);
	}
	
	public ServiceProxy getServiceProxy(Service service) {
		if (service != null)
			this.canisterId = service.getPrincipal();
		
		this.parseServiceType();

		return new ServiceProxy(this);
	}	
	

	public <T> T getProxy(Class<T> interfaceClass) {
		
		
		AgentInvocationHandler agentInvocationHandler = this.getAgentInvocationHandler(interfaceClass);		
		
		T proxy = (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(), new Class[] { interfaceClass },
				agentInvocationHandler);
		
		this.parseServiceType();

		return proxy;
	}
	
	<T> AgentInvocationHandler getAgentInvocationHandler(Class<T> interfaceClass) {

		Agent agent = this.agent;

		if (agent == null) {
			if (interfaceClass.isAnnotationPresent(org.ic4j.agent.annotations.Agent.class)) {
				org.ic4j.agent.annotations.Agent agentAnnotation = interfaceClass
						.getAnnotation(org.ic4j.agent.annotations.Agent.class);

				Transport transportAnnotation = agentAnnotation.transport();
				Identity identity = new AnonymousIdentity();

				org.ic4j.agent.annotations.Identity identityAnnotation = agentAnnotation.identity();

				try {
					ReplicaTransport transport = ReplicaApacheHttpTransport.create(transportAnnotation.url());

					switch (identityAnnotation.type()) {
					case ANONYMOUS:
						identity = new AnonymousIdentity();
						break;
					case BASIC:
						if ("".equals(identityAnnotation.pem_file())) {
							KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
							identity = BasicIdentity.fromKeyPair(keyPair);
						} else {
							Path path = Paths.get(identityAnnotation.pem_file());

							identity = BasicIdentity.fromPEMFile(path);
						}
						break;
					case SECP256K1:
						Path path = Paths.get(identityAnnotation.pem_file());

						identity = Secp256k1Identity.fromPEMFile(path);
						break;

					}

					agent = new AgentBuilder().transport(transport).identity(identity).build();
					
					if(agentAnnotation.fetchRootKey())
						agent.fetchRootKey();
					
				} catch (URISyntaxException e) {
					throw AgentError.create(AgentError.AgentErrorCode.INVALID_REPLICA_URL, e,
							transportAnnotation.url());
				} catch (NoSuchAlgorithmException e) {
					throw PemError.create(PemError.PemErrorCode.PEM_ERROR, e, identityAnnotation.pem_file());
				}
			} else
				throw AgentError.create(AgentError.AgentErrorCode.MISSING_REPLICA_TRANSPORT);
		}

		Principal canisterId = this.canisterId;
		Principal effectiveCanisterId = this.effectiveCanisterId;

		if (effectiveCanisterId == null) {
			if (interfaceClass.isAnnotationPresent(EffectiveCanister.class)) {
				EffectiveCanister effectiveCanister = interfaceClass.getAnnotation(EffectiveCanister.class);

				effectiveCanisterId = Principal.fromString(effectiveCanister.value());
			}
		}

		if (canisterId == null) {
			if (interfaceClass.isAnnotationPresent(Canister.class)) {
				Canister canister = interfaceClass.getAnnotation(Canister.class);

				canisterId = Principal.fromString(canister.value());

				if (effectiveCanisterId == null)
					effectiveCanisterId = canisterId.clone();
			}
		}
		
		if (interfaceClass.isAnnotationPresent(org.ic4j.agent.annotations.IDLFile.class))
		{
			String idlFileURL = interfaceClass.getAnnotation(org.ic4j.agent.annotations.IDLFile.class).value(); 
			this.idlFile = Paths.get(idlFileURL); 
		}
		if (interfaceClass.isAnnotationPresent(org.ic4j.agent.annotations.Properties.class))
		{
			org.ic4j.agent.annotations.Properties properties = interfaceClass.getAnnotation(org.ic4j.agent.annotations.Properties.class);
			
			if(properties.disableRangeCheck())
				this.disableRangeCheck = true;
			if(properties.loadIDL())
				this.loadIDL = true;
		}

		return new AgentInvocationHandler(agent, canisterId,
				effectiveCanisterId, this.ingressExpiryDatetime, waiter);
	}

	class AgentInvocationHandler implements InvocationHandler {
		Agent agent;
		Principal canisterId;
		Principal effectiveCanisterId;
		Optional<Long> ingressExpiryDatetime;
		Waiter waiter;

		AgentInvocationHandler(Agent agent, Principal canisterId, Principal effectiveCanisterId,
				Optional<Long> ingressExpiryDatetime, Waiter waiter) {
			this.agent = agent;
			this.canisterId = canisterId;
			this.effectiveCanisterId = effectiveCanisterId;
			this.ingressExpiryDatetime = ingressExpiryDatetime;
			this.waiter = waiter;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws AgentError {

			MethodType methodType = MethodType.UPDATE;

			String methodName = method.getName();

			if (method.isAnnotationPresent(QUERY.class))
				methodType = MethodType.QUERY;
			else if (method.isAnnotationPresent(UPDATE.class))
				methodType = MethodType.UPDATE;

			// the new way how to define operation type
			if (method.isAnnotationPresent(Modes.class)) {
				Mode[] modes = method.getAnnotation(Modes.class).value();

				if (modes.length > 0) {
					switch (modes[0]) {
					case QUERY:
						methodType = MethodType.QUERY;
						break;
					case ONEWAY:
						methodType = MethodType.ONEWAY;
						break;
					default:
						methodType = MethodType.UPDATE;
						break;
					}
				}
			}

			if (method.isAnnotationPresent(Name.class)) {
				Name nameAnnotation = method.getAnnotation(Name.class);
				methodName = nameAnnotation.value();
			}

			Parameter[] parameters = method.getParameters();

			ArrayList<IDLValue> candidArgs = new ArrayList<IDLValue>();

			if (args != null)
				for (int i = 0; i < args.length; i++) {
					Object arg = args[i];
					Argument argumentAnnotation = null;

					boolean skip = false;

					ObjectSerializer objectSerializer = new PojoSerializer();

					for (Annotation annotation : method.getParameterAnnotations()[i]) {
						if (Ignore.class.isInstance(annotation)) {
							skip = true;
							continue;
						}
						if (Argument.class.isInstance(annotation))
							argumentAnnotation = (Argument) annotation;
						if (org.ic4j.candid.annotations.Serializer.class.isInstance(annotation)) {
							Class<ObjectSerializer> serializerClass = (Class<ObjectSerializer>) ((org.ic4j.candid.annotations.Serializer) annotation)
									.value();
							try {
								objectSerializer = serializerClass.getConstructor().newInstance();
							} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
									| InvocationTargetException | NoSuchMethodException | SecurityException e) {
								throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e);
							}
						}
					}

					if (skip)
						continue;

					{
						if (argumentAnnotation != null) {
							Type type = argumentAnnotation.value();

							IDLType idlType;

							if (parameters[i].getType().isArray())
								idlType = IDLType.createType(Type.VEC, IDLType.createType(type));
							else
								idlType = IDLType.createType(type);

							IDLValue idlValue = IDLValue.create(arg, objectSerializer, idlType);

							candidArgs.add(idlValue);
						} else
							candidArgs.add(IDLValue.create(arg, objectSerializer));
					}
				}

			IDLArgs idlArgs = IDLArgs.create(candidArgs);

			byte[] buf = idlArgs.toBytes();

			ObjectDeserializer objectDeserializer;

			if (method.isAnnotationPresent(org.ic4j.candid.annotations.Deserializer.class)) {
				Class<ObjectDeserializer> deserializerClass = (Class<ObjectDeserializer>) method
						.getAnnotation(org.ic4j.candid.annotations.Deserializer.class).value();
				try {
					objectDeserializer = deserializerClass.getConstructor().newInstance();
				} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException | NoSuchMethodException | SecurityException e) {
					throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e);
				}
			} else
				objectDeserializer = new PojoDeserializer();
			switch (methodType) {
			case QUERY: {
				QueryBuilder queryBuilder = QueryBuilder.create(agent, this.canisterId, methodName);

				queryBuilder.effectiveCanisterId = this.effectiveCanisterId;
				queryBuilder.ingressExpiryDatetime = this.ingressExpiryDatetime;

				CompletableFuture<Response<byte[]>> builderResponse = queryBuilder.arg(buf).call(null);

				try {
					if (method.getReturnType().equals(CompletableFuture.class)) {
						CompletableFuture<Object> response = new CompletableFuture();

						builderResponse.whenComplete((input, ex) -> {
							if (ex == null) {
								if (input != null) {
									IDLArgs outArgs = IDLArgs.fromBytes(input.getPayload());

									if (outArgs.getArgs().isEmpty())
										response.completeExceptionally(AgentError.create(
												AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
									else {
										Class<?> responseClass = getMethodClass(method);

										if (responseClass != null) {
											if (responseClass.isAssignableFrom(IDLArgs.class))
												response.complete(outArgs);
											else if (responseClass.isAssignableFrom(Response.class))
												response.complete(input);
											else
												response.complete(outArgs.getArgs().get(0).getValue(objectDeserializer,
														responseClass));
										} else
											response.complete(outArgs.getArgs().get(0).getValue());
									}
								} else
									response.completeExceptionally(AgentError
											.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
							} else
								response.completeExceptionally(ex);
						});
						return response;
					} else {
						if (method.getReturnType().equals(Response.class))
							return builderResponse.get();

						byte[] output = builderResponse.get().getPayload();

						IDLArgs outArgs = IDLArgs.fromBytes(output);

						if (method.getReturnType().equals(IDLArgs.class))
							return outArgs;

						if (outArgs.getArgs().isEmpty())
							throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value");

						return outArgs.getArgs().get(0).getValue(objectDeserializer, method.getReturnType());
					}

				} catch (AgentError e) {
					throw e;
				} catch (Exception e) {
					throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e, e.getLocalizedMessage());
				}
			}
			case ONEWAY:
			case UPDATE: {
				boolean disableRangeCheck = false;
				UPDATE updateMethod = method.getAnnotation(UPDATE.class);

				if (updateMethod != null) {
					disableRangeCheck = updateMethod.disableRangeCheck();
				}

				UpdateBuilder updateBuilder = UpdateBuilder.create(this.agent, this.canisterId, methodName);

				updateBuilder.effectiveCanisterId = this.effectiveCanisterId;
				updateBuilder.ingressExpiryDatetime = this.ingressExpiryDatetime;

				CompletableFuture<Object> response = new CompletableFuture<Object>();

				CompletableFuture<Response<RequestId>> requestResponse = updateBuilder.arg(buf).call(null);

				if (methodType == MethodType.ONEWAY) {
					response.complete(null);

					return response;
				}

				RequestId requestId;
				try {
					requestId = requestResponse.get().getPayload();
				} catch (ExecutionException e) {
					if (e.getCause() != null && e.getCause() instanceof AgentError)
						throw (AgentError) e.getCause();
					else
						throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e, e.getLocalizedMessage());
				} catch (InterruptedException e) {
					throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e, e.getLocalizedMessage());
				}

				Waiter waiter = this.waiter;

				if (waiter == null) {
					if (method.isAnnotationPresent(org.ic4j.agent.annotations.Waiter.class)) {
						org.ic4j.agent.annotations.Waiter waiterAnnotation = method
								.getAnnotation(org.ic4j.agent.annotations.Waiter.class);
						waiter = Waiter.create(waiterAnnotation.timeout(), waiterAnnotation.sleep());
					} else
						waiter = Waiter.create(WAITER_TIMEOUT, WAITER_SLEEP);
				}

				CompletableFuture<Response<byte[]>> builderResponse = updateBuilder.getState(requestId, null,
						disableRangeCheck, waiter);

				builderResponse.whenComplete((input, ex) -> {
					if (ex == null) {
						if (input != null) {
							IDLArgs outArgs = IDLArgs.fromBytes(input.getPayload());

							if (outArgs.getArgs().isEmpty()) {
								if (method.getReturnType().equals(Void.TYPE))
									response.complete(null);
								else {
									Class<?> responseClass = getMethodClass(method);

									if (responseClass != null) {
										if (responseClass.isAssignableFrom(Void.class))
											response.complete(null);
										else
											response.completeExceptionally(AgentError.create(
													AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
									} else
										response.completeExceptionally(AgentError.create(
												AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
								}
							} else {
								Class<?> responseClass = getMethodClass(method);

								if (responseClass != null) {
									if (responseClass.isAssignableFrom(IDLArgs.class))
										response.complete(outArgs);
									else if (responseClass.isAssignableFrom(Response.class))
										response.complete(input);
									else
										response.complete(
												outArgs.getArgs().get(0).getValue(objectDeserializer, responseClass));
								} else
									response.complete(outArgs.getArgs().get(0).getValue());
							}
						} else if (method.getReturnType().equals(Void.TYPE))
							response.complete(null);
						else {
							Class<?> responseClass = getMethodClass(method);

							if (responseClass != null) {
								if (responseClass.isAssignableFrom(Void.class))
									response.complete(null);
								else
									response.completeExceptionally(AgentError
											.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
							} else
								response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR,
										"Missing return value"));
						}
					} else {
						if (ex instanceof AgentError)
							response.completeExceptionally(ex);
						else
							response.completeExceptionally(
									AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, ex));
					}

				});
				return response;
			}
			default:
				throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Invalid Candid method type");
			}
		}

	}
	
	
	
	<T> T invoke(Func func, IDLType funcType,Class<T> responseClass, Mode[] modes, ObjectSerializer[] serializers, ObjectDeserializer deserializer, Object[] args) throws AgentError {
		MethodType methodType = MethodType.UPDATE;

		String methodName = func.getMethod();
		
		if(modes == null && funcType != null)
			modes = funcType.modes.stream().toArray(Mode[]::new);

		// the new way how to define operation type
		if (modes != null) {

			if (modes.length > 0) {
				switch (modes[0]) {
				case QUERY:
					methodType = MethodType.QUERY;
					break;
				case ONEWAY:
					methodType = MethodType.ONEWAY;
					break;
				default:
					methodType = MethodType.UPDATE;
					break;
				}
			}
		}

		ArrayList<IDLValue> candidArgs = new ArrayList<IDLValue>();

		if (args != null)	
			for (int i = 0; i < args.length; i++) {
				Object arg = args[i];

				ObjectSerializer objectSerializer = new PojoSerializer();
				
				if(serializers != null && serializers.length > i)
					objectSerializer = serializers[i];			

				if(funcType != null && funcType.getArgs().size() > i){
						IDLType idlType = funcType.getArgs().get(i);
						
						objectSerializer.setIDLType(idlType);

						IDLValue idlValue = IDLValue.create(arg, objectSerializer, idlType);

						candidArgs.add(idlValue);
					} else
						candidArgs.add(IDLValue.create(arg, objectSerializer));
			}

		IDLArgs idlArgs = IDLArgs.create(candidArgs);

		byte[] buf = idlArgs.toBytes();

		ObjectDeserializer objectDeserializer;

		if (deserializer != null)
				objectDeserializer = deserializer;
		else
			objectDeserializer = new PojoDeserializer();
		
		switch (methodType) {
		case QUERY: {
			QueryBuilder queryBuilder = QueryBuilder.create(agent, this.canisterId, methodName);

			queryBuilder.effectiveCanisterId = this.effectiveCanisterId;
			
			if(queryBuilder.effectiveCanisterId == null)
				queryBuilder.effectiveCanisterId = this.canisterId;
			
			queryBuilder.ingressExpiryDatetime = this.ingressExpiryDatetime;

			CompletableFuture<Response<byte[]>> builderResponse = queryBuilder.arg(buf).call(null);

			try {			
				if (responseClass != null && responseClass.equals(CompletableFuture.class)) {
					CompletableFuture<Object> response = new CompletableFuture();

					builderResponse.whenComplete((input, ex) -> {
						if (ex == null) {
							if (input != null) {
								IDLArgs outArgs = IDLArgs.fromBytes(input.getPayload());

								if (outArgs.getArgs().isEmpty())
									response.completeExceptionally(AgentError.create(
											AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
								else {
										
									if (responseClass != null) {
										if (responseClass.isAssignableFrom(IDLArgs.class))
											response.complete(outArgs);
										else if (responseClass.isAssignableFrom(Response.class))
											response.complete(input);
										else
										{
											if(funcType != null && funcType.getRets().size() > 0) {
												objectDeserializer.setIDLType(funcType.getRets().get(0));
												response.complete(outArgs.getArgs().get(0).getValue(objectDeserializer,responseClass,
														funcType.getRets().get(0)));												
											}
											else
												response.complete(outArgs.getArgs().get(0).getValue(objectDeserializer,
													responseClass));
										}
									}										
								}
							} else
								response.completeExceptionally(AgentError
										.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
						} else
							response.completeExceptionally(ex);
					});
					return (T) response;
				} else {
					byte[] output = builderResponse.get().getPayload();

					IDLArgs outArgs = IDLArgs.fromBytes(output);
					if (responseClass != null) {
						if (responseClass.equals(Response.class))
							return (T) builderResponse.get();

						if (responseClass.equals(IDLArgs.class))
							return (T) outArgs;
	
						if (outArgs.getArgs().isEmpty())
							throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value");
	
						if(funcType != null && funcType.getRets().size() > 0){
						
							objectDeserializer.setIDLType(funcType.getRets().get(0));
						
							return outArgs.getArgs().get(0).getValue(objectDeserializer,responseClass,
									funcType.getRets().get(0));
						}
						else
							return outArgs.getArgs().get(0).getValue(objectDeserializer,
									responseClass);
					}else
						if(funcType != null && funcType.getRets().size() > 0)
							return outArgs.getArgs().get(0).getValue(funcType.getRets().get(0));
						else	
							return outArgs.getArgs().get(0).getValue();
				}

			} catch (AgentError e) {
				throw e;
			} catch (Exception e) {
				throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e, e.getLocalizedMessage());
			}
		}
		case ONEWAY:
		case UPDATE: {
			UpdateBuilder updateBuilder = UpdateBuilder.create(this.agent, this.canisterId, methodName);

			updateBuilder.effectiveCanisterId = this.effectiveCanisterId;
			
			if(updateBuilder.effectiveCanisterId == null)
				updateBuilder.effectiveCanisterId = this.canisterId;
			
			updateBuilder.ingressExpiryDatetime = this.ingressExpiryDatetime;

			CompletableFuture<Object> response = new CompletableFuture<Object>();

			CompletableFuture<Response<RequestId>> requestResponse = updateBuilder.arg(buf).call(null);

			if (methodType == MethodType.ONEWAY) {
				response.complete(null);

				return (T) response;
			}

			RequestId requestId;
			try {
				requestId = requestResponse.get().getPayload();
			} catch (ExecutionException e) {
				if (e.getCause() != null && e.getCause() instanceof AgentError)
					throw (AgentError) e.getCause();
				else
					throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e, e.getLocalizedMessage());
			} catch (InterruptedException e) {
				throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e, e.getLocalizedMessage());
			}

			Waiter waiter = this.waiter;
			boolean disableRangeCheck = this.disableRangeCheck;

			if (waiter == null) 
				waiter = Waiter.create(WAITER_TIMEOUT, WAITER_SLEEP);

			CompletableFuture<Response<byte[]>> builderResponse = updateBuilder.getState(requestId, null,
					this.disableRangeCheck, waiter);

			if (responseClass != null && responseClass.equals(CompletableFuture.class)) {
				builderResponse.whenComplete((input, ex) -> {
					if (ex == null) {
						if (input != null) {
							IDLArgs outArgs = IDLArgs.fromBytes(input.getPayload());
	
							if (outArgs.getArgs().isEmpty()) {
								if (funcType != null && funcType.getRets().size() == 0)
									response.complete(null);
								else {
	
									if (responseClass != null) {
										if (responseClass.isAssignableFrom(Void.class))
											response.complete(null);
										else
											response.completeExceptionally(AgentError.create(
													AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
									} else
										response.completeExceptionally(AgentError.create(
												AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
								}
							} else {
								if (responseClass != null) {
									if (responseClass.isAssignableFrom(IDLArgs.class))
										response.complete(outArgs);
									else if (responseClass.isAssignableFrom(Response.class))
										response.complete(input);
									else
										if(funcType != null && funcType.getRets().size() > 0)
										{
											objectDeserializer.setIDLType(funcType.getRets().get(0));
											
											response.complete(outArgs.getArgs().get(0).getValue(objectDeserializer,responseClass,
													funcType.getRets().get(0)));	
										}
										else
											response.complete(outArgs.getArgs().get(0).getValue(objectDeserializer,
												responseClass));
								} else 
									if(funcType != null && funcType.getRets().size() > 0)
										response.complete(outArgs.getArgs().get(0).getValue(funcType.getRets().get(0)));
									else	
										response.complete(outArgs.getArgs().get(0).getValue());
							}
						} else if (funcType != null && funcType.getRets().size() == 0)
							response.complete(null);
						else {
							if (responseClass != null) {
								if (responseClass.isAssignableFrom(Void.class))
									response.complete(null);
								else
									response.completeExceptionally(AgentError
											.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value"));
							} else
								response.completeExceptionally(AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR,
										"Missing return value"));
						}
					} else {
						if (ex instanceof AgentError)
							response.completeExceptionally(ex);
						else
							response.completeExceptionally(
									AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, ex));
					}
	
				});
				return (T) response;
			} else {
				try {
					byte[] output = builderResponse.get(WAITER_TIMEOUT + 10, TimeUnit.SECONDS).getPayload();

					IDLArgs outArgs = IDLArgs.fromBytes(output);
					if (responseClass != null) {
						if (responseClass.equals(Response.class))
							return (T) builderResponse.get();
	
						if (responseClass.equals(IDLArgs.class))
							return (T) outArgs;
	
						if (outArgs.getArgs().isEmpty())
							throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing return value");
	
						if(funcType != null && funcType.getRets().size() > 0)
							return outArgs.getArgs().get(0).getValue(objectDeserializer,responseClass,
									funcType.getRets().get(0));
						else
							return outArgs.getArgs().get(0).getValue(objectDeserializer,
									responseClass);
					}else
						if(funcType != null && funcType.getRets().size() > 0)
							return outArgs.getArgs().get(0).getValue(funcType.getRets().get(0));
						else	
							return outArgs.getArgs().get(0).getValue();
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, e);
				}				
			}
		}
		default:
			throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Invalid Candid method type");
		}
	}

	
	
	void parseServiceType()
	{
		if(!this.loadIDL)
			return;
		
		//get service type from cache
		this.serviceType = this.serviceTypes.get(this.canisterId);
		
		if(this.serviceType != null)
			return;
		
		Reader idlReader;
		
		if(this.idlFile != null)
		{
			try {
				idlReader = Files.newBufferedReader(this.idlFile);
			} catch (IOException e) {
				throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Invalid Candid IDL file %s", idlFile.getFileName());
			}
		}
		else
		{	
			if(this.canisterId == null)
				throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing canister Id");
			
			if(this.agent == null)
				throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing Agent");
			
			String serviceIDL = this.agent.getIDL(canisterId);
			
			LOG.debug(serviceIDL);
			
			idlReader = new StringReader(serviceIDL);
		}
		
		IDLParser idlParser = new IDLParser(idlReader);
		idlParser.parse();
		
		Map<String,IDLType> serviceTypes = idlParser.getServices();
		
		if(!serviceTypes.isEmpty())
			this.serviceType = serviceTypes.values().iterator().next();	
		
		this.serviceTypes.put(this.canisterId, this.serviceType);
	}

	static Class<?> getMethodClass(Method method) {
		Class<?> responseClass = null;

		if (method.isAnnotationPresent(ResponseClass.class))
			responseClass = method.getAnnotation(ResponseClass.class).value();
		else {
			java.lang.reflect.Type type = method.getGenericReturnType();
			ParameterizedType pType = (ParameterizedType) type;

			if (pType.getActualTypeArguments()[0] instanceof ParameterizedType) {
				pType = (ParameterizedType) pType.getActualTypeArguments()[0];

				responseClass = (Class<?>) pType.getRawType();
			} else
				responseClass = (Class<?>) pType.getActualTypeArguments()[0];
		}

		return responseClass;
	}

}
