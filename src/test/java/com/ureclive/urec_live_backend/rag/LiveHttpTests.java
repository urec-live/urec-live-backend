package com.ureclive.urec_live_backend.rag;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LiveHttpTests {
    @Test void boundsResponseSizeAndBodyDeliveryTime() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", exchange -> {
            byte[] bytes = new byte[4096]; exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        });
        server.createContext("/slow", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        try {
            var http = new LiveHttp(); String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThrows(Exception.class, () -> http.request(URI.create(origin + "/large"), null, Map.of(),
                    System.nanoTime() + Duration.ofSeconds(5).toNanos(), 100));
            long started = System.nanoTime();
            assertThrows(Exception.class, () -> http.request(URI.create(origin + "/slow"), null, Map.of(),
                    started + Duration.ofMillis(250).toNanos(), 100));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1500);
        } finally { server.stop(0); }
    }
}
