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

package org.ic4j.agent.hashtree;

import java.util.List;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using = HashTreeDeserializer.class)
public final class HashTree {
	HashTreeNode rootNode;
	
	HashTree(HashTreeNode rootNode)
	{
		this.rootNode = rootNode;
	}
	
	// Recomputes root hash of the full tree that this hash tree was constructed from.
	
	public byte[] digest()
	{
		return this.rootNode.digest();
	}
	
    // Given a (verified) tree, the client can fetch the value at a given path, which is a
    // sequence of labels (blobs).
	public LookupResult lookupPath(List<Label> path)
	{
		return this.rootNode.lookupPath(path);
	}
	
}
