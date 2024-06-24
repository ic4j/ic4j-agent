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

package org.ic4j.agent.certification.hashtree;


public final class SubtreeLookupResult {
	public SubtreeLookupResultStatus status;
	
	public HashTree value;
	
	SubtreeLookupResult(SubtreeLookupResultStatus status)
	{		
		this.status = status;
	}
	
	SubtreeLookupResult(SubtreeLookupResultStatus status, HashTree value)
	{		
		this.status = status;
		this.value = value;
	}	
	
	public enum SubtreeLookupResultStatus{
		// The value is guaranteed to be absent in the original state tree.
		ABSENT,
		// This partial view does not include information about this path, and the original
	    // tree may or may note include this value.
		UNKNOWN,
		// The subtree was found at the provided path.
		FOUND
		;
		
	}

}
