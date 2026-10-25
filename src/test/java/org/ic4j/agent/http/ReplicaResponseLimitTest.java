package org.ic4j.agent.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ReplicaResponseLimitTest {
    @Test
    void acceptsResponsesUpToTheLimit() throws Exception {
        byte[] body = new byte[4];
        Assertions.assertArrayEquals(body, ReplicaResponseLimit.read(new ByteArrayInputStream(body), 4));
    }

    @Test
    void rejectsStreamingResponseAboveTheLimit() {
        InputStream oversized = new InputStream() {
            private long remaining = 5;

            @Override
            public int read() {
                if (remaining == 0) return -1;
                remaining--;
                return 0;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                if (remaining == 0) return -1;
                int count = (int) Math.min(remaining, length);
                remaining -= count;
                return count;
            }
        };
        Assertions.assertThrows(IOException.class, () -> ReplicaResponseLimit.read(oversized, 4));
    }

    @Test
    void usesDefaultAndConfiguredByteLimit() throws Exception {
        String previous = System.getProperty(ReplicaResponseLimit.PROPERTY_NAME);
        try {
            System.clearProperty(ReplicaResponseLimit.PROPERTY_NAME);
            Assertions.assertEquals(16 * 1024 * 1024, ReplicaResponseLimit.configuredMaxBytes());
            System.setProperty(ReplicaResponseLimit.PROPERTY_NAME, "4");
            int configured = ReplicaResponseLimit.configuredMaxBytes();
            Assertions.assertEquals(4, configured);
            Assertions.assertThrows(IOException.class,
                    () -> ReplicaResponseLimit.read(new ByteArrayInputStream(new byte[5]), configured));
        } finally {
            restoreProperty(previous);
        }
    }

    @Test
    void rejectsInvalidPropertyInsteadOfDisablingLimit() {
        String previous = System.getProperty(ReplicaResponseLimit.PROPERTY_NAME);
        try {
            for (String value : new String[] {"0", "-1", "unlimited", "2147483648"}) {
                System.setProperty(ReplicaResponseLimit.PROPERTY_NAME, value);
                Assertions.assertThrows(IllegalArgumentException.class, ReplicaResponseLimit::configuredMaxBytes);
            }
        } finally {
            restoreProperty(previous);
        }
    }

    private static void restoreProperty(String previous) {
        if (previous == null) System.clearProperty(ReplicaResponseLimit.PROPERTY_NAME);
        else System.setProperty(ReplicaResponseLimit.PROPERTY_NAME, previous);
    }
}
