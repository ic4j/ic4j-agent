package org.ic4j.agent.test;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.scheduler.Scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

@ExtendWith(MockServerExtension.class)
@MockServerSettings(ports = {8777})
public abstract class MockTest{
	ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());

	MockServerClient mockServerClient;
	
	static final EventLoopGroup clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(UpdateTest.class.getSimpleName() + "-eventLoop"));

	@BeforeEach
	public  void setUpBeforeClass(MockServerClient mockServerClient) throws Exception {
		this.mockServerClient = mockServerClient;
	}
}
