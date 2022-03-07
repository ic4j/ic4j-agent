package org.ic4j.agent.test;


import org.ic4j.agent.annotations.Argument;
import org.ic4j.agent.annotations.QUERY;
import org.ic4j.candid.annotations.Name;
import org.ic4j.candid.types.Type;

public interface BinaryProxy {
	@QUERY
	@Name("echoBinary")
	public byte[] echoBinaryPrimitive(@Argument(Type.NAT8)byte[] value);
	
	@QUERY
	@Name("echoBinary")
	public Byte[] echoBinaryObject(@Argument(Type.NAT8)Byte[] value);	
}
