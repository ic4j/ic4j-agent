package org.ic4j.agent.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class ReplicaResponseLimit {
    static final String PROPERTY_NAME = "org.ic4j.agent.http.maxResponseBytes";
    static final int DEFAULT_MAX_BYTES = 16 * 1024 * 1024;

    private ReplicaResponseLimit() {}

    static int configuredMaxBytes() {
        String value = System.getProperty(PROPERTY_NAME);
        if (value == null) return DEFAULT_MAX_BYTES;
        try {
            int maxBytes = Integer.parseInt(value);
            if (maxBytes > 0) return maxBytes;
        } catch (NumberFormatException ignored) {
            // Reject invalid values below so a typo cannot disable the limit.
        }
        throw new IllegalArgumentException(PROPERTY_NAME + " must be a positive number of bytes: " + value);
    }

    static void check(long consumed, long incoming, int maxBytes) throws IOException {
        if (incoming > maxBytes - consumed)
            throw new IOException("Replica response exceeds " + maxBytes + " bytes");
    }

    static byte[] read(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            check(output.size(), count, maxBytes);
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
