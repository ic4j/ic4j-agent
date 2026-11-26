package org.ic4j.ci;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.HttpVersion;
import org.ic4j.agent.Agent;
import org.ic4j.agent.AgentBuilder;
import org.ic4j.agent.NonceFactory;
import org.ic4j.agent.ReplicaTransport;
import org.ic4j.agent.UpdateBuilder;
import org.ic4j.agent.Waiter;
import org.ic4j.agent.http.ReplicaApacheHttpTransport;
import org.ic4j.agent.http.ReplicaJavaHttpTransport;
import org.ic4j.candid.parser.IDLArgs;
import org.ic4j.candid.parser.IDLValue;
import org.ic4j.types.Principal;

public final class LocalReplicaSmoke {
    // No configurable network address: this smoke must never issue a public UPDATE.
    private static final String URL = "http://127.0.0.1:4943/";

    private LocalReplicaSmoke() {}

    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 11) {
            throw new IllegalStateException("This smoke expects a Java 11 process");
        }
        requireLocalJar(Agent.class, "ic4j-agent");
        requireLocalJar(IDLArgs.class, "ic4j-candid");
        boolean apacheOnly = Boolean.getBoolean("ic4j.ci.apacheOnly");
        if (!apacheOnly) {
            requireLocalJar(ReplicaJavaHttpTransport.class, "ic4j-java11transport");
        }
        String apacheVersion = System.getProperty("ic4j.ci.apacheVersion");
        String apacheJar = loadedPath(CloseableHttpAsyncClient.class).getFileName().toString();
        if (!apacheJar.equals("httpclient5-" + apacheVersion + ".jar")) {
            throw new IllegalStateException("Unexpected Apache HttpClient JAR: " + apacheJar);
        }
        System.out.println("Apache runtime: " + apacheJar + "; " + loadedPath(HttpVersion.class).getFileName());

        String id = System.getenv("IC4J_CI_CANISTER");
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Local fixture ID is missing");
        }
        Principal canister = Principal.fromString(id);
        check("Agent/Apache HTTP " + apacheVersion, ReplicaApacheHttpTransport.create(URL), canister);
        if (!apacheOnly) {
            check("Java HTTP Transport", ReplicaJavaHttpTransport.create(URL), canister);
        }
    }

    private static Path loadedPath(Class<?> type) throws Exception {
        return Paths.get(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
    }

    private static void requireLocalJar(Class<?> type, String artifact) throws Exception {
        Path expected = Paths.get(System.getProperty("ic4j.ci.repo"), "org", "ic4j",
                artifact, "0.8.5", artifact + "-0.8.5.jar").toRealPath();
        Path loaded = loadedPath(type);
        if (!Files.isRegularFile(loaded) ||
                !Arrays.equals(Files.readAllBytes(expected), Files.readAllBytes(loaded))) {
            throw new IllegalStateException("Unexpected origin for " + artifact + ": " + loaded);
        }
        System.out.println("Loaded " + loaded);
    }

    private static void check(String name, ReplicaTransport transport, Principal canister) throws Exception {
        Agent agent = new AgentBuilder().transport(transport).nonceFactory(new NonceFactory()).build();
        try {
            agent.fetchRootKey();
            byte[] input = IDLArgs.create(Collections.singletonList(IDLValue.create(name))).toBytes();
            byte[] update = UpdateBuilder.create(agent, canister, "greet").arg(input)
                    .callAndWait(Waiter.create(30, 1)).get(40, TimeUnit.SECONDS);
            assertText("UPDATE", name, update);

            byte[] empty = IDLArgs.create(Collections.emptyList()).toBytes();
            byte[] query = agent.queryRaw(canister, canister, "getName", empty, Optional.empty())
                    .get(20, TimeUnit.SECONDS);
            assertText("QUERY", name, query);
            System.out.println("PASS: " + name + " UPDATE + QUERY (Java 11)");
        } finally {
            agent.close();
        }
    }

    private static void assertText(String operation, String expected, byte[] bytes) {
        Object actual = IDLArgs.fromBytes(bytes).getArgs().get(0).getValue();
        if (!expected.equals(actual)) {
            throw new AssertionError(operation + ": expected " + expected + ", got " + actual);
        }
    }
}
