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

import java.lang.reflect.Method;

import org.ic4j.agent.ProxyBuilder.AgentInvocationHandler;
import org.ic4j.candid.ObjectDeserializer;
import org.ic4j.candid.ObjectSerializer;
import org.ic4j.candid.types.Mode;
import org.ic4j.types.Func;

public class FuncProxy<T> {
	Object proxy;
	Method method;
	AgentInvocationHandler agentInvocationHandler;
	ObjectSerializer[] serializers;
	ObjectDeserializer deserializer;
	ServiceProxy serviceProxy;
	Func func;
	Mode[] modes;
	Class<?> responseClass;
	
	FuncProxy(Object proxy, Method method, AgentInvocationHandler agentInvocationHandler) {
		this.proxy = proxy;
		this.method = method;
		this.agentInvocationHandler = agentInvocationHandler;
	}
	
	FuncProxy(ServiceProxy serviceProxy, Func func, Mode[] modes) {
		this.serviceProxy = serviceProxy;
		this.func = func;
		this.modes = modes;
	}	


	public T call(Object... args)
	{
		if(agentInvocationHandler != null)
			return (T) agentInvocationHandler.invoke(proxy, method, args);
		else if(serviceProxy != null)
			return (T) serviceProxy.invoke(this.func, this.responseClass, this.modes, this.serializers, this.deserializer, args);
		
		throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing service");
			
	}
	
	public void setSerializers(ObjectSerializer... serializers)
	{
		this.serializers = serializers;
	}
	
	public void setDeserializer(ObjectDeserializer deserializer)
	{
		this.deserializer = deserializer;
	}

	/**
	 * @param modes the modes to set
	 */
	public void setModes(Mode[] modes) {
		this.modes = modes;
	}

	/**
	 * @param responseClass the responseClass to set
	 */
	public void setResponseClass(Class<?> responseClass) {
		this.responseClass = responseClass;
	}	
}
