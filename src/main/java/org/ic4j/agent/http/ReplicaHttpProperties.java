package org.ic4j.agent.http;

class ReplicaHttpProperties {
	static final String DFINITY_CONTENT_TYPE = "application/cbor";
	static final String API_VERSION_URL_PART = "/api/v2/";
	static final String STATUS_URL_PART = "status";
	static final String QUERY_URL_PART = "canister/%s/query";
	static final String CALL_URL_PART = "canister/%s/call";
	static final String READ_STATE_URL_PART = "canister/%s/read_state";
}
