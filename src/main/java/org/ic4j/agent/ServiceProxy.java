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
import java.lang.reflect.ParameterizedType;

import org.ic4j.candid.ObjectDeserializer;
import org.ic4j.candid.ObjectSerializer;
import org.ic4j.candid.parser.IDLType;
import org.ic4j.candid.types.Mode;
import org.ic4j.types.Func;

public class ServiceProxy {
	ProxyBuilder proxyBuilder;

	ServiceProxy(ProxyBuilder proxyBuilder) {
		this.proxyBuilder = proxyBuilder;
	}
	
	public <T> FuncProxy<T>  getFuncProxy(Func func)
	{
		return this.getFuncProxy(func, null);
	}
	
	public <T> FuncProxy<T>  getFuncProxy(Func func, Mode[] modes)
	{
		FuncProxy<T> funcProxy = new FuncProxy<T>(this, func, modes);
		return funcProxy;
	}	
	

	<T> T invoke(Func func, Class<T> responseClass, Mode[] modes, ObjectSerializer[] serializers, ObjectDeserializer deserializer, Object... args) {
		if(proxyBuilder.serviceType != null)
		{
			IDLType funcType = this.proxyBuilder.serviceType.getMeths().get(func.getMethod());
			
			if(funcType == null)
				throw AgentError.create(AgentError.AgentErrorCode.CUSTOM_ERROR, "Missing function type");
			
			return this.proxyBuilder.invoke(func, funcType, responseClass, modes, serializers, deserializer, args);
		}
		else 
			return this.proxyBuilder.invoke(func, null, responseClass, modes, serializers, deserializer, args);					
	}
	
	Class<?> getFuncResponseClass(FuncProxy funcProxy)
	{
		Method method;
		try {
			method = funcProxy.getClass().getDeclaredMethod("call", Object[].class);
		} catch (NoSuchMethodException | SecurityException e) {
			return null;
		}

		java.lang.reflect.Type type = method.getGenericReturnType();
		if(type instanceof ParameterizedType){
			ParameterizedType pType = (ParameterizedType) type;
			if (pType.getActualTypeArguments()[0] instanceof ParameterizedType) {
				pType = (ParameterizedType) pType.getActualTypeArguments()[0];
	
				return (Class<?>) pType.getRawType();
			}
		}

		return null;
	}
}
