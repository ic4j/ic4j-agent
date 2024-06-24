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

package org.ic4j.agent.certification.hashtree;


public final class LookupResult {
	public LookupResultStatus status;
	
	public byte[] value;
	
	LookupResult(LookupResultStatus status)
	{		
		this.status = status;
	}
	
	LookupResult(LookupResultStatus status, byte[] value)
	{		
		this.status = status;
		this.value = value;
	}	
	
	public enum LookupResultStatus{
		// The value is guaranteed to be absent in the original state tree.
		ABSENT,
		// This partial view does not include information about this path, and the original
	    // tree may or may note include this value.
		UNKNOWN,
		// The value was found at the referenced node.
		FOUND,
		// The path does not make sense for this certificate.
		ERROR
		;
		
	}

}
