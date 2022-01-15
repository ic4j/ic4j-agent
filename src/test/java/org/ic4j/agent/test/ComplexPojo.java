package org.ic4j.agent.test;

import java.math.BigInteger;

import org.ic4j.candid.annotations.Field;
import org.ic4j.candid.annotations.Name;
import org.ic4j.candid.types.Type;

public class ComplexPojo {
	@Field(Type.BOOL)
	@Name("bar")
	public Boolean bar;
	
	@Field(Type.INT)
	@Name("foo")
	public BigInteger foo;
	
	@Field(Type.RECORD)
	@Name("pojo")
	public Pojo pojo;

	// Just for testing purposes, JUnit uses equals
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ComplexPojo other = (ComplexPojo) obj;
		if (bar == null) {
			if (other.bar != null)
				return false;
		} else if (!bar.equals(other.bar))
			return false;
		if (foo == null) {
			if (other.foo != null)
				return false;
		} else if (!foo.equals(other.foo))
			return false;
		if (pojo == null) {
			if (other.pojo != null)
				return false;
		} else if (!pojo.equals(other.pojo))
			return false;
		return true;
	}
	
	
}
