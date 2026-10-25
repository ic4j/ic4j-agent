package org.ic4j.agent;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.ic4j.agent.annotations.QUERY;
import org.ic4j.agent.requestid.RequestId;
import org.ic4j.types.Principal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.miracl.core.RAND;
import org.miracl.core.BLS12381.ECDH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

class ProposedSecurityFixTest {
    private final Principal canister = Principal.fromString("rrkah-fqaaa-aaaaa-aaaaq-cai");

    @Test
    void malformedQueryStatusCompletesBothRawFuturesExceptionally() throws Exception {
        Agent agent = new AgentBuilder().transport(new ReplyTransport(new byte[] {(byte) 0xa0})).build();
        CompletableFuture<byte[]> bare = agent.queryRaw(canister, canister, "read",
                new byte[0], Optional.empty());
        CompletableFuture<Response<byte[]>> wrapped = agent.queryRaw(canister, canister, "read",
                new Request<byte[]>(new byte[0], Collections.emptyMap()), Optional.empty());
        Assertions.assertThrows(ExecutionException.class, () -> bare.get(2, TimeUnit.SECONDS));
        Assertions.assertThrows(ExecutionException.class, () -> wrapped.get(2, TimeUnit.SECONDS));
    }

    @Test
    void malformedCandidCompletesProxyFutureExceptionally() throws Exception {
        Map<String, Object> reply = new HashMap<>();
        reply.put("status", "replied");
        reply.put("reply", Collections.singletonMap("arg", new byte[] {1, 2, 3}));
        byte[] cbor = new ObjectMapper(new CBORFactory()).writeValueAsBytes(reply);
        Agent agent = new AgentBuilder().transport(new ReplyTransport(cbor)).build();
        BadReplyProxy proxy = ProxyBuilder.create(agent, canister).getProxy(BadReplyProxy.class);
        Assertions.assertThrows(ExecutionException.class, () -> proxy.read().get(2, TimeUnit.SECONDS));
    }

    @Test
    void eciesRequiresFullTagBeforeReturningPlaintext() {
        byte[] receiverSecret = new byte[ECDH.EGS];
        receiverSecret[receiverSecret.length - 1] = 3;
        byte[] receiverPublic = new byte[2 * ECDH.EFS + 1];
        Assertions.assertEquals(0, ECDH.KEY_PAIR_GENERATE(null, receiverSecret, receiverPublic));

        RAND random = new RAND();
        byte[] seed = new byte[100];
        new SecureRandom().nextBytes(seed);
        random.seed(seed.length, seed);
        byte[] ephemeralPublic = new byte[2 * ECDH.EFS + 1];
        byte[] tag = new byte[32];
        byte[] message = "A long message whose ciphertext spans several CBC blocks."
                .getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = ECDH.ECIES_ENCRYPT(32, new byte[0], new byte[0], random,
                receiverPublic, message, ephemeralPublic, tag);
        Assertions.assertEquals(0, ECDH.ECIES_ENCRYPT(32, new byte[0], new byte[0], random,
                receiverPublic, message, ephemeralPublic, new byte[0]).length);
        Assertions.assertArrayEquals(message, ECDH.ECIES_DECRYPT(32, new byte[0], new byte[0],
                ephemeralPublic, ciphertext, tag, receiverSecret));
        Assertions.assertEquals(0, ECDH.ECIES_DECRYPT(32, new byte[0], new byte[0],
                ephemeralPublic, ciphertext, new byte[0], receiverSecret).length);
        byte[] modified = Arrays.copyOf(ciphertext, ciphertext.length);
        modified[0] ^= 1;
        Assertions.assertEquals(0, ECDH.ECIES_DECRYPT(32, new byte[0], new byte[0],
                ephemeralPublic, modified, tag, receiverSecret).length);
    }

    interface BadReplyProxy {
        @QUERY
        CompletableFuture<String> read();
    }

    private static final class ReplyTransport implements ReplicaTransport {
        private final byte[] payload;

        private ReplyTransport(byte[] payload) {
            this.payload = payload;
        }

        @Override
        public CompletableFuture<ReplicaResponse> query(Principal id, byte[] envelope,
                Map<String, String> headers) {
            ReplicaResponse reply = new ReplicaResponse();
            reply.payload = payload;
            reply.headers = Collections.emptyMap();
            return CompletableFuture.completedFuture(reply);
        }

        @Override
        public CompletableFuture<ReplicaResponse> status() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<ReplicaResponse> call(Principal id, byte[] envelope,
                RequestId requestId, Map<String, String> headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<ReplicaResponse> readState(Principal id, byte[] envelope,
                Map<String, String> headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {}
    }
}
