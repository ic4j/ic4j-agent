package org.ic4j.agent.replicaapi;

import java.util.Map;

abstract class Response {
	public Map<String,String> headers;

	/**
	 * @return the headers
	 */
	public Map<String, String> getHeaders() {
		return headers;
	}

	/**
	 * @param headers the headers to set
	 */
	public void setHeaders(Map<String, String> headers) {
		this.headers = headers;
	}
}
