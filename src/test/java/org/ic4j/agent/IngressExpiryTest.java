package org.ic4j.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class IngressExpiryTest {
	@Test
	void convertsInstantToExactNanoseconds() {
		assertEquals(1_685_570_400_123_456_789L,
				IngressExpiry.toEpochNanos(Instant.ofEpochSecond(1_685_570_400L, 123_456_789L)));
	}

	@Test
	void appliesRequestedDurationAndDriftFromTheSameInstant() {
		Instant now = Instant.ofEpochSecond(1_685_570_400L, 987_654_321L);
		assertEquals(IngressExpiry.toEpochNanos(now.plusSeconds(29)),
				IngressExpiry.after(now, Duration.ofSeconds(30), Duration.ofSeconds(1)));
	}

	@Test
	void rejectsNanosecondOverflowInsteadOfWrapping() {
		assertThrows(ArithmeticException.class,
				() -> IngressExpiry.toEpochNanos(Instant.ofEpochSecond(10_000_000_000L)));
	}
}
