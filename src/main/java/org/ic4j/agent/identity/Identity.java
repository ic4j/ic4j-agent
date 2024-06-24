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

package org.ic4j.agent.identity;

import java.util.List;
import java.util.ArrayList;

import org.ic4j.agent.AgentError;
import org.ic4j.agent.AgentError.AgentErrorCode;
import org.ic4j.agent.replicaapi.Delegation;
import org.ic4j.agent.replicaapi.SignedDelegation;
import org.ic4j.types.Principal;

public abstract class Identity {
    // Returns a sender, ie. the Principal ID that is used to sign a request.
    // Only one sender can be used per request.
	public abstract  Principal sender();
	
    // Sign a request ID derived from a content map.
	public abstract  Signature sign(byte[] content);
	
    // Produce the public key commonly returned in [`Signature`].
	public abstract byte[] getPublicKey();
	
    // Sign arbitrary bytes.
    // Not all `Identity` implementations support this operation, though all `ic-agent` implementations do.
	public  Signature signArbitrary(byte[] content) throws AgentError{
		throw AgentError.create(AgentErrorCode.AUTHENTICATION_ERROR, "unsupported");
	}
	
    // Sign a delegation to let another key be used to authenticate [`sender`](Identity::sender).
    // Not all `Identity` implementations support this operation, though all `Agent` implementations other than `AnonymousIdentity` do. 
	public  Signature signDelegation(Delegation delegation) throws AgentError{
		throw AgentError.create(AgentErrorCode.AUTHENTICATION_ERROR, "unsupported");
	}
	
    // A list of signed delegations connecting [`sender`](Identity::sender)
    // to [`public_key`](Identity::public_key), and in that order.
    public List<SignedDelegation> delegationChain() {
    	return new ArrayList<SignedDelegation>();
    }	

}
