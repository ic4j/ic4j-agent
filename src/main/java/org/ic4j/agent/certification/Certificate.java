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

package org.ic4j.agent.certification;

import java.util.Optional;

import org.ic4j.agent.certification.hashtree.HashTree;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Certificate {
	
	@JsonProperty("tree")
	public HashTree tree;
	
	@JsonProperty("signature")
	public byte[] signature;
	
	@JsonProperty("delegation")
	@JsonInclude(JsonInclude.Include.NON_ABSENT)
	public Optional<Delegation> delegation;
	
}
