package org.openstreetmap.josm.plugins.dawarich;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DawarichTransportContractTest {
    private static final String POINT = "[{\"id\":1,\"latitude\":1.25,\"longitude\":3.5,\"timestamp\":1577836800}]";
    private HttpServer server;
    private ExecutorService serverExecutor;
    private final AtomicInteger hits = new AtomicInteger();
    private volatile String authorization;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; });
        server.setExecutor(serverExecutor);
        server.createContext("/prefix/api/v1/points", this::validResponse);
        server.start();
    }

    @AfterEach void stop() {
        if (server != null) server.stop(0);
        if (serverExecutor != null) serverExecutor.shutdownNow();
    }

    private void validResponse(HttpExchange x) throws IOException {
        hits.incrementAndGet(); authorization = x.getRequestHeaders().getFirst("Authorization");
        respond(x, 200, "application/json", "1", "1", POINT);
    }

    private static void respond(HttpExchange x, int status, String contentType, String page, String total, String body) throws IOException {
        if (contentType != null) x.getResponseHeaders().set("Content-Type", contentType);
        if (page != null) x.getResponseHeaders().set("X-Current-Page", page);
        if (total != null) x.getResponseHeaders().set("X-Total-Pages", total);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = x.getResponseBody()) { out.write(bytes); }
    }

    private DawarichClient client() {
        return new DawarichClient(ConnectionSettings.parse("http://127.0.0.1:" + server.getAddress().getPort() + "/prefix/", true));
    }
    private QueryBounds bounds() {
        return new QueryBounds(1, 2, 3, 4, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2),
                ZoneOffset.UTC, Instant.parse("2020-02-01T00:00:00Z"));
    }
    private BatchBudget budget() { return new BatchBudget(8, Duration.ofSeconds(3), 10000, 10000); }
    private DawarichException fetchError(BatchBudget b) {
        return assertThrows(DawarichException.class, () -> client().fetchPage("test-key", bounds(), 1, 2, b));
    }
    @Test void redirectNeverContactsLocationTarget() throws Exception {
        AtomicInteger targetHits = new AtomicInteger();
        server.createContext("/capture", x -> { targetHits.incrementAndGet(); x.close(); });
        server.removeContext("/prefix/api/v1/points");
        server.createContext("/prefix/api/v1/points", x -> {
            hits.incrementAndGet(); authorization = x.getRequestHeaders().getFirst("Authorization");
            x.getResponseHeaders().set("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/capture");
            x.sendResponseHeaders(302, -1); x.close();
        });
        assertEquals(DawarichException.Category.REDIRECT, fetchError(budget()).category());
        assertEquals(0, targetHits.get());
    }

    @Test void rejectsHtmlContentAndMissingPaginationHeaders() {
        for (String[] scenario : new String[][]{
                {"text/html", "1", "1", "<html>error</html>"},
                {"application/json", "1", "1", "<html>error</html>"},
                {"application/json", null, "1", POINT}}) {
            server.removeContext("/prefix/api/v1/points");
            server.createContext("/prefix/api/v1/points", x -> {
                hits.incrementAndGet();
                respond(x, 200, scenario[0], scenario[1], scenario[2], scenario[3]);
            });
            assertEquals(DawarichException.Category.PROTOCOL, fetchError(budget()).category());
        }
    }

    @Test void authAndRateLimitErrorsAreSafeAndClassified() {
        for (int status : new int[]{401, 429}) {
            server.removeContext("/prefix/api/v1/points");
            server.createContext("/prefix/api/v1/points", x -> {
                hits.incrementAndGet();
                if (status == 429) x.getResponseHeaders().set("Retry-After", "30");
                respond(x, status, "application/json", "1", "1", "{\"detail\":\"test-key leaked?\"}");
            });
            DawarichException e = fetchError(budget());
            assertEquals(status == 401 ? DawarichException.Category.AUTH : DawarichException.Category.RATE_LIMIT, e.category());
            assertEquals(status != 401, e.stopsBatch());
            assertFalse(e.getMessage().contains("test-key"));
            assertFalse(e.toString().contains("test-key"));
            assertNull(e.getCause());
            assertEquals(status == 429 ? 30 : null, e.retryAfterSeconds());
        }
    }

    @Test void perResponseByteCapStopsOversizedBody() {
        server.removeContext("/prefix/api/v1/points");
        server.createContext("/prefix/api/v1/points", x -> {
            hits.incrementAndGet(); respond(x, 200, "application/json", "1", "1", " ".repeat(128));
        });
        BatchBudget capped = new BatchBudget(2, Duration.ofSeconds(3), 10000, 32);
        assertEquals(DawarichException.Category.LIMIT, fetchError(capped).category());
        assertEquals(1, hits.get());
    }

    @Test void cancellationInterruptsStalledResponseBody() throws Exception {
        CountDownLatch bodyStarted = new CountDownLatch(1), release = new CountDownLatch(1);
        server.removeContext("/prefix/api/v1/points");
        server.createContext("/prefix/api/v1/points", x -> {
            hits.incrementAndGet();
            x.getResponseHeaders().set("Content-Type", "application/json");
            x.getResponseHeaders().set("X-Current-Page", "1");
            x.getResponseHeaders().set("X-Total-Pages", "1");
            x.sendResponseHeaders(200, 0);
            try (OutputStream out = x.getResponseBody()) { out.write('['); out.flush(); bodyStarted.countDown(); release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (IOException ignored) { }
        });
        BatchBudget b = budget(); ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<DawarichException> result = caller.submit(() -> {
                try { client().fetchPage("test-key", bounds(), 1, 2, b); return null; }
                catch (DawarichException e) { return e; }
            });
            assertTrue(bodyStarted.await(2, TimeUnit.SECONDS));
            long began = System.nanoTime(); b.cancel();
            DawarichException e = result.get(1, TimeUnit.SECONDS);
            assertEquals(DawarichException.Category.CANCELLED, e.category());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began) < 1000);
            assertEquals(1, hits.get());
        } finally { release.countDown(); caller.shutdownNow(); }
    }

    @Test void rejectsInvalidAndContradictoryPagination() {
        for (String[] scenario : new String[][]{{null, "1", POINT}, {"-1", "1", POINT}, {"2", "2", POINT}}) {
            server.removeContext("/prefix/api/v1/points");
            server.createContext("/prefix/api/v1/points", x -> {
                hits.incrementAndGet(); respond(x, 200, "application/json", scenario[0], scenario[1], scenario[2]);
            });
            assertEquals(DawarichException.Category.PROTOCOL, fetchError(budget()).category());
        }
    }

    @Test void sequentialKeysAreIsolatedAndNeverSentInUri() throws Exception {
        List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        server.removeContext("/prefix/api/v1/points");
        server.createContext("/prefix/api/v1/points", x -> {
            hits.incrementAndGet();
            String auth = x.getRequestHeaders().getFirst("Authorization"); seen.add(auth);
            String id = "Bearer key-B".equals(auth) ? "2" : "1";
            String body = "[{\"id\":" + id + ",\"latitude\":1.25,\"longitude\":3.5,\"timestamp\":1577836800}]";
            respond(x, 200, "application/json", "1", "1", body);
        });
        DawarichClient c = client(); BatchBudget b = budget();
        assertEquals(1, c.fetchPage("key-A", bounds(), 1, 2, b).points().get(0).id());
        assertEquals(2, c.fetchPage("key-B", bounds(), 1, 2, b).points().get(0).id());
        assertEquals(1, c.fetchPage("key-A", bounds(), 1, 2, b).points().get(0).id());
        assertEquals(List.of("Bearer key-A", "Bearer key-B", "Bearer key-A"), List.copyOf(seen));
        assertEquals(3, hits.get());
    }
}
