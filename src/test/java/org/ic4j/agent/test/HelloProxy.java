package org.ic4j.agent.test;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.ic4j.agent.Response;
import org.ic4j.agent.annotations.Agent;
import org.ic4j.agent.annotations.Identity;
import org.ic4j.agent.annotations.IdentityType;
import org.ic4j.agent.annotations.Canister;
import org.ic4j.agent.annotations.EffectiveCanister;
import org.ic4j.agent.annotations.Transport;
import org.ic4j.agent.annotations.Waiter;
import org.ic4j.agent.annotations.QUERY;
import org.ic4j.agent.annotations.UPDATE;
import org.ic4j.agent.annotations.Verify;
import org.ic4j.agent.annotations.Argument;
import org.ic4j.candid.annotations.Modes;
import org.ic4j.candid.annotations.Name;
import org.ic4j.candid.types.Mode;
import org.ic4j.candid.types.Type;
import org.ic4j.types.Func;

@Agent(identity = @Identity(type = IdentityType.BASIC, pem_file = "./src/test/resources/Ed25519_identity.pem"), transport = @Transport(url = "https://icp-api.io/"))
@Canister("zwbmv-jyaaa-aaaab-qacaa-cai")
@EffectiveCanister("zwbmv-jyaaa-aaaab-qacaa-cai")
public interface HelloProxy {
	
	@Modes(Mode.QUERY)
	public String peek(@Argument(Type.TEXT)String name, @Argument(Type.INT) BigInteger value);
	
	@Modes(Mode.QUERY)
	@Verify
	@Name("peek")
	public String peekSigned(@Argument(Type.TEXT)String name, @Argument(Type.INT) BigInteger value);	
	
	@QUERY
	@Name("echoInt")
	public BigInteger getInt(BigInteger value);	
	
	@QUERY
	@Name("echoPojo")
	public Pojo getPojo(Pojo value);
	
	@QUERY
	@Name("echoOptionPojo")
	public Pojo echoOptionPojo(Optional<Pojo> value);	
	
	@QUERY
	public ComplexPojo echoComplexPojo(ComplexPojo value);
	
	@Modes(Mode.QUERY)
	@Name("echoFunc0")
	public Func echoFunc(Func value);
	
	@UPDATE
	@Waiter(timeout = 30)
	public CompletableFuture<ComplexPojo> updateComplexPojo(ComplexPojo value);	
	
	@UPDATE
	@Name("void")
	@Waiter(timeout = 30)
	public CompletableFuture<Void> noReturn(String value);	
	
	@QUERY
	@Name("subComplexPojo")
	public Pojo subComplexPojo(ComplexPojo value);
	
	@QUERY
	@Name("echoComplexPojoVec")
	public ComplexPojo[] echoComplexPojo(ComplexPojo[] value);	
	
	@QUERY
	@Name("echoComplexPojoVec")
	public Response<byte[]> echoComplexPojoWithHeader(ComplexPojo[] value);	
	
	@QUERY
	public CompletableFuture<Double> getFloat(Double value);
	
//	@UPDATE
	@Name("greet")
	@Waiter(timeout = 30)
	public CompletableFuture<String> greet(@Argument(Type.TEXT)String name);
	
	@UPDATE
	@Name("greet")
	@Waiter(timeout = 30)
	public CompletableFuture<Response<byte[]>> greetWithHeader(@Argument(Type.TEXT)String name);	

}
