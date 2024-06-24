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

package org.ic4j.agent.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.ic4j.agent.AgentError;
import org.ic4j.agent.replicaapi.Delegation;
import org.ic4j.agent.replicaapi.SignedDelegation;
import org.ic4j.types.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DelegatedIdentity extends Identity {
	static final Logger LOG = LoggerFactory.getLogger(DelegatedIdentity.class);

	Identity to;
	public byte[] derEncodedPublickey;
	
	public List<SignedDelegation> chain;



	public DelegatedIdentity(Identity identity, byte[] derEncodedPublickey, List<SignedDelegation> chain) {
		this.to = identity;
		this.derEncodedPublickey = derEncodedPublickey;
		this.chain = chain;
	}
	
	Signature chainSignature(Signature sig)
	{
		sig.publicKey = Optional.of(this.derEncodedPublickey);
		
		List<SignedDelegation> chain;
		
		if(sig.delegations == null || !sig.delegations.isPresent())
			chain = new ArrayList<SignedDelegation>();
		else
			chain = sig.delegations.get();
		
		for(SignedDelegation delegation : this.chain)
			chain.add(delegation);

		sig.delegations = Optional.of(chain);
		
		return sig;
	}
	
	@Override
	public Principal sender() {
		return Principal.selfAuthenticating(derEncodedPublickey);
	}	
	
	public Signature sign(byte[] content) {
		Signature sig = this.to.sign(content);
		
		return this.chainSignature(sig);
	}
	
	@Override
	public Signature signDelegation(Delegation delegation) throws AgentError {
		Signature sig = this.to.signDelegation(delegation);
		
		return this.chainSignature(sig);
	}	
	
	@Override
	public Signature signArbitrary(byte[] content) throws AgentError {
		Signature sig = this.to.signArbitrary(content);
		
		return this.chainSignature(sig);
	}

	
	@Override
	public List<SignedDelegation> delegationChain() {
		List<SignedDelegation> chain;
		
		if(to.delegationChain() == null)
			chain = new ArrayList<SignedDelegation>();
		else
			chain = to.delegationChain();
		
		for(SignedDelegation delegation : this.chain)
			chain.add(delegation);

		return chain;
	}

	public byte[] getPublicKey()
	{
		return this.derEncodedPublickey;
	}

}
