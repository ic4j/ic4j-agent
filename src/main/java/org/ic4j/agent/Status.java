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

import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
* The structure returned by [`ic_agent::Agent::status`], containing the information returned
* by the status endpoint of a replica.
*/
@JsonIgnoreProperties(ignoreUnknown = true)
public class Status {
	
	/**
    * Identifies the interface version supported, i.e. the version of the present document that
    * the internet computer aims to support, e.g. 0.8.1. The implementation may also return
    * unversioned to indicate that it does not comply to a particular version, e.g. in between
    * releases.
    */
	@JsonProperty("ic_api_version")
    public String icAPIVersion;

    /**
    * Optional. Identifies the implementation of the Internet Computer, by convention with the
    * canonical location of the source code.
    */
	@JsonProperty("impl_source")
    public Optional<String> implSource;

    /**
    * Optional. If the user is talking to a released version of an Internet Computer
    * implementation, this is the version number. For non-released versions, output of
    * `git describe` like 0.1.13-13-g2414721 would also be very suitable.
    */
	@JsonProperty("impl_version")
    public Optional<String> implVersion;

    /**
    * Optional. The precise git revision of the Internet Computer implementation.
    */
	@JsonProperty("impl_revision")
    public Optional<String> implRevision;
	
    /**
    * Optional.  The health status of the replica.  One hopes it's "healthy".
    */
	@JsonProperty("replica_health_status")
    public Optional<String> replicaHealthStatus;	
	
    /**
    * Optional. 
    */
	@JsonProperty("certified_height")
    public Optional<Long>certifiedHeight;

    /**
    * Optional.  The root (public) key used to verify certificates.
    */
	@JsonProperty("root_key")
    public Optional<byte[]>rootKey;

    /**
    * Contains any additional values that the replica gave as status.
    /*
     * 
     */
    public Map<String, ?> values;
}
