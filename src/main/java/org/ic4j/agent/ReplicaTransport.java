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

import java.util.concurrent.CompletableFuture;

import org.ic4j.agent.requestid.RequestId;
import org.ic4j.types.Principal;


public interface ReplicaTransport {
	
	public CompletableFuture<byte[]> status();
	
	public CompletableFuture<byte[]> query(Principal canisterId, byte[] envelope);
	
	public CompletableFuture<byte[]> call(Principal canisterId, byte[] envelope, RequestId requestId);
	
	public CompletableFuture<byte[]> readState(Principal canisterId, byte[] envelope);

}
