package org.ic4j.agent.test;


import java.io.IOException;
import java.net.URI;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.NottableString;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = {8777})
public abstract class MockTest{
	ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());

	MockServerClient mockServerClient;

	@BeforeEach
	public  void setUpBeforeClass(MockServerClient mockServerClient) throws Exception {
		this.mockServerClient = mockServerClient;
	}

	protected byte[] forwardRequest(HttpRequest httpRequest) throws IOException {
		HttpUriRequestBase forwardRequest = new HttpUriRequestBase(httpRequest.getMethod().getValue(),
				URI.create("http://" + TestProperties.FORWARD_HOST + ":" + TestProperties.FORWARD_PORT
						+ httpRequest.getPath().getValue()));

		for (Header header : httpRequest.getHeaderList()) {
			String headerName = header.getName().getValue();

			if ("host".equalsIgnoreCase(headerName) || "content-length".equalsIgnoreCase(headerName))
				continue;

			for (NottableString headerValue : header.getValues())
				forwardRequest.addHeader(headerName, headerValue.getValue());
		}

		byte[] requestBody = httpRequest.getBodyAsRawBytes();
		if (requestBody != null && requestBody.length > 0)
			forwardRequest.setEntity(new ByteArrayEntity(requestBody, ContentType.APPLICATION_OCTET_STREAM));

		try (CloseableHttpClient client = HttpClients.createDefault()) {
			return client.execute(forwardRequest, response -> {
				HttpEntity entity = response.getEntity();
				return entity == null ? new byte[0] : EntityUtils.toByteArray(entity);
			});
		}
	}
}
