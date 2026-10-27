/*
 * Copyright 2026 Exilor Inc.
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

import java.time.Duration;
import java.time.Instant;

/** Helpers for converting the IC's nanosecond UNIX timestamps without losing precision. */
final class IngressExpiry {
	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	private IngressExpiry() {
	}

	static long toEpochNanos(Instant instant) {
		return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), NANOS_PER_SECOND), instant.getNano());
	}

	static long after(Duration duration, Duration permittedDrift) {
		if (duration == null || permittedDrift == null)
			throw new IllegalArgumentException("Expiry duration and permitted drift are required");
		return after(Instant.now(), duration, permittedDrift);
	}

	static long after(Instant now, Duration duration, Duration permittedDrift) {
		if (now == null || duration == null || permittedDrift == null)
			throw new IllegalArgumentException("Current time, expiry duration, and permitted drift are required");
		return toEpochNanos(now.plus(duration).minus(permittedDrift));
	}
}
