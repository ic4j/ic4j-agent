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

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import org.ic4j.agent.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class HashTreeNode {
	protected static final Logger LOG = LoggerFactory.getLogger(HashTreeNode.class);
	
	
	NodeType type;
	
	static HashTreeNode deserialize(JsonNode node) {
		
		if(node.isArray())
		{
			int tag = node.get(0).intValue();
			
			switch( tag)
			{
			case 0: 
				if(node.size() > 1)
					throw new Error("Invalid Length");
				return new EmptyHashTreeNode();
			case 1: 
				if(node.size() != 3)
					throw new Error("Invalid Length");
				HashTreeNode left = HashTreeNode.deserialize(node.get(1));
				HashTreeNode right = HashTreeNode.deserialize(node.get(2));
				return new ForkHashTreeNode(left,right);	
			case 2: 
				if(node.size() != 3)
					throw new Error("Invalid Length");
				try {
					// Incompatible with Android, using custom function instead
					Label label = new Label(Base64.getDecoder().decode(node.get(1).asText()));	
					
					//Label label1 = new Label(Base64.decodeBase64(node.get(1).asText()));
					//Label label = new Label(Base64.decodeBase64(node.get(1).binaryValue()));
					
					HashTreeNode subtree = HashTreeNode.deserialize(node.get(2));
					return new LabeledHashTreeNode(label,subtree);	
				} catch (Exception e) {
					throw new Error(String.format("Invalid Node Type %s", node.getNodeType().name()));
				}
			case 3: 
				if(node.size() != 2)
					throw new Error("Invalid Length");
				try {
					byte[] value = node.get(1).binaryValue();
					return new LeafHashTreeNode(value);	
				} catch (IOException e) {
					throw new Error(String.format("Invalid Node Type %s", node.getNodeType().name()));
				}
			case 4: 
				if(node.size() != 2)
					throw new Error("Invalid Length");
				try {
					byte[] value = node.get(1).binaryValue();
					
					if(value.length != 32)
						throw new Error("Invalid Length");
					
					return new PrunedHashTreeNode(value);	
				} catch (IOException e) {
					throw new Error(String.format("Invalid Node Type %s", node.getNodeType().name()));
				}				
			default:
				throw new Error(String.format("Unknown tag: %d, expected the tag to be one of {{0, 1, 2, 3, 4}}",tag));
			}
		}
		else
			throw new Error(String.format("Invalid Node Type %s", node.getNodeType().name()));
			
	}

	/*
	* Calculate the digest of this node only.
	*/
	public byte[] digest() {
		MessageDigest messageDigest = DigestUtils.getSha256Digest();
		
		this.domainSep(messageDigest);
		
		switch(this.type)
		{
			case EMPTY: break;
			case FORK: 
				messageDigest.update(((ForkHashTreeNode)this).left.digest());
				messageDigest.update(((ForkHashTreeNode)this).right.digest());
				break;
			case LABELED: 
				messageDigest.update(((LabeledHashTreeNode)this).label.value);
				messageDigest.update(((LabeledHashTreeNode)this).subtree.digest());
				break;
			case LEAF: 
				messageDigest.update(((LeafHashTreeNode)this).value);
				break;
			case PRUNED: 
				int[] unsignedDigest = org.ic4j.candid.ByteUtils.toUnsignedIntegerArray(((PrunedHashTreeNode)this).digest);
				return ((PrunedHashTreeNode)this).digest;
		}		
		
		return messageDigest.digest();
	}
	
	
	/* Update a hasher with the domain separator (byte(|s|) . s).
	 */
	void domainSep(MessageDigest messageDigest)
	{
		String domainSep;
		
		switch(this.type)
		{
			case EMPTY: domainSep = "ic-hashtree-empty";break;
			case FORK: domainSep = "ic-hashtree-fork";break;
			case LABELED: domainSep = "ic-hashtree-labeled";break;
			case LEAF: domainSep = "ic-hashtree-leaf";break;
			default: return;
		}
		
		messageDigest.update((byte)domainSep.length());
		messageDigest.update(domainSep.getBytes());
		
	}

	/*
	 Lookup the path for the current node only. If the node does not contain the label,
    this will return [None], signifying that whatever process is recursively walking the
    tree should continue with siblings of this node (if possible). If it returns
    [Some] value, then it found an actual result and this may be propagated to the
    original process doing the lookup.
    
    This assumes a sorted hash tree, which is what the spec says the system should return.
    It will stop when it finds a label that's greater than the one being looked for.
	*/
	
	public LookupResult lookupPath(List<Label> path) {
		
		if(path == null || path.isEmpty())
		{
			switch(this.type)
			{
				case EMPTY: 
					return new LookupResult(LookupResult.LookupResultStatus.ABSENT);
				case FORK: 
					return new LookupResult(LookupResult.LookupResultStatus.ERROR);
				case LABELED:
					return new LookupResult(LookupResult.LookupResultStatus.ERROR);
				case LEAF: 
					return new LookupResult(LookupResult.LookupResultStatus.FOUND, ((LeafHashTreeNode)this).value);
				case PRUNED: 
					return new LookupResult(LookupResult.LookupResultStatus.UNKNOWN);
			}			
		}
		else
		{
			LookupLabelResult result = this.lookupLabel(path.get(0));
			
			switch(result.status)
			{
				case UNKNOWN: 
					return new LookupResult(LookupResult.LookupResultStatus.UNKNOWN);
				case ABSENT:
				case CONTINUE: 
					if(Arrays.asList(NodeType.EMPTY,NodeType.PRUNED, NodeType.LEAF).contains(this.type) )
						return new LookupResult(LookupResult.LookupResultStatus.UNKNOWN);
					else
						return new LookupResult(LookupResult.LookupResultStatus.ABSENT);
				case FOUND: 
					path.remove(0);
					return result.value.lookupPath(path);			
			}
		}
		
		throw new Error("Invalid Path " + path);
	}
	
	/*
    Lookup a subtree at the provided path.
    If the tree definitely does not contain the label, this will return [SubtreeLookupResult::Absent].
    If the tree has pruned sections that might contain the path, this will return [SubtreeLookupResult::Unknown].
    If the provided path is found, this will return [SubtreeLookupResult::Found] with the node that was found at that path.
    This assumes a sorted hash tree, which is what the spec says the system should return.
    It will stop when it finds a label that's greater than the one being looked for.
     */
	public SubtreeLookupResult lookupSubtree(List<Label> path) {
		
		if(path == null || path.isEmpty())
		{
			switch(this.type)
			{
				case EMPTY: 
					return new SubtreeLookupResult(SubtreeLookupResult.SubtreeLookupResultStatus.ABSENT);
				default: 
					return new SubtreeLookupResult(SubtreeLookupResult.SubtreeLookupResultStatus.FOUND, new HashTree(((HashTreeNode)this)));
			}			
		}
		else
		{
			LookupLabelResult result = this.lookupLabel(path.get(0));
			
			switch(result.status)
			{
				case UNKNOWN: 
					return new SubtreeLookupResult(SubtreeLookupResult.SubtreeLookupResultStatus.UNKNOWN);
				case ABSENT:
				case CONTINUE: 
					if(Arrays.asList(NodeType.EMPTY, NodeType.LEAF).contains(this.type) )
						return new SubtreeLookupResult(SubtreeLookupResult.SubtreeLookupResultStatus.UNKNOWN);
					else
						return new SubtreeLookupResult(SubtreeLookupResult.SubtreeLookupResultStatus.ABSENT);
				case FOUND: 
					path.remove(0);
					return result.value.lookupSubtree(path);			
			}
		}
		
		throw new Error("Invalid Path " + path);
	}
	
	List<List<Label>> listPaths(List<Label> path){
		List<List<Label>> result = new ArrayList<List<Label>>();
		switch(this.type) {
			case FORK:
				HashTreeNode leftNode = ((ForkHashTreeNode)this).left;
				HashTreeNode rightNode = ((ForkHashTreeNode)this).right; 
				List<List<Label>> leftPaths = leftNode.listPaths(path);
				List<List<Label>> rightPaths = rightNode.listPaths(path);
				result.addAll(leftPaths);
				result.addAll(rightPaths);
				break;
			case LEAF:
				List<Label> clonePath = new ArrayList<Label>(path);
				result.add(clonePath);
				break;
			case LABELED:
				HashTreeNode labeledNode = ((LabeledHashTreeNode)this).subtree;
				clonePath = new ArrayList<Label>(path);
				clonePath.add(((LabeledHashTreeNode)this).label);
				
				List<List<Label>> labeledPaths = labeledNode.listPaths(clonePath);
				result.addAll(labeledPaths);
				break;
			case PRUNED:	
			case EMPTY:
				break;
		}
		
		return result;
	}
	
    /*
	Lookup a single label, returning a reference to the labeled [HashTreeNode] node if found.
    
    This assumes a sorted hash tree, which is what the spec says the system should
    return. It will stop when it finds a label that's greater than the one being looked
    for.
    
     This function is implemented with flattening in mind, ie. flattening the forks
     is not necessary.	
     */
	
	 LookupLabelResult lookupLabel(Label label)
	 {
			switch(this.type)
			{

				case LABELED:
					// If this node is a labeled node, check for the name. This assume a
					int i = label.compareTo(((LabeledHashTreeNode)this).label);
					
					if(i > 0)
						return new LookupLabelResult(LookupLabelResultStatus.CONTINUE);
					else if (i == 0)
						return new LookupLabelResult(LookupLabelResultStatus.FOUND, ((LabeledHashTreeNode)this).subtree);					
					else
						// If this node has a smaller label than the one we're looking for, shortcut
		                // out of this search (sorted tree), we looked too far.
						return new LookupLabelResult(LookupLabelResultStatus.ABSENT);
						
				case FORK: 
				{
					LookupLabelResult leftResult = ((ForkHashTreeNode)this).left.lookupLabel(label);
					
					switch(leftResult.status)
					{
                     	// On continue or unknown, look on the right side of the fork.
                    	// If it cannot be found on the right, return Unknown though.
						case CONTINUE:
						case UNKNOWN:
						{
							LookupLabelResult rightResult = ((ForkHashTreeNode)this).right.lookupLabel(label);
							
							if(rightResult.status == LookupLabelResultStatus.ABSENT)
							{
								if(leftResult.status == LookupLabelResultStatus.ABSENT)
									return new LookupLabelResult(LookupLabelResultStatus.UNKNOWN);
								else
									return new LookupLabelResult(LookupLabelResultStatus.ABSENT);
									
							}
							else
								return rightResult;
						}
						default: return leftResult;	
					}
				}
				case PRUNED: 
					return new LookupLabelResult(LookupLabelResultStatus.UNKNOWN);
				default: 
					// Any other type of node and we need to look for more forks.
					return new LookupLabelResult(LookupLabelResultStatus.CONTINUE);
			}			 
	 }
	 
	 
	class LookupLabelResult
	{
		LookupLabelResultStatus status;
			
		HashTreeNode value;
			
		
		LookupLabelResult(LookupLabelResultStatus status)
		{		
			this.status = status;
		}
		
		LookupLabelResult(LookupLabelResultStatus status, HashTreeNode value)
		{		
			this.status = status;
			this.value = value;
		}
		
	}	 
	
	
	enum LookupLabelResultStatus{
		// The label is not part of this node's tree.
		ABSENT,
		// This partial view does not include information about this path, and the original
	    // tree may or may note include this value.
		UNKNOWN,
		// The label was not found, but could still be to the left.
		LESS,
		// The label was not found, but could still be to the right.
		GREATER,
		// The label was not found, but could still be somewhere else.
		CONTINUE,
		// The value was found at the referenced node.
		FOUND
		;
		
	}
	
	
	
}
