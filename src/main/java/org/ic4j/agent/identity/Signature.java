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

import java.util.Optional;

public final class Signature {
	public Optional<byte[]> publicKey;
	public Optional<byte[]> signature;
	
	public Signature() {
		
	}
	
	public Signature(byte[] publicKey, byte[] signature)
	{
		this.publicKey = Optional.of(publicKey);
		this.signature = Optional.of(signature);
	}

}
