package com.bebefish.erp.shipping.logistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AneAccountContextTest {
    @Test
    void selectedAccountSignsBothOperationsWithoutChangingGlobalConfiguration() throws Exception {
        var mapper = new ObjectMapper();
        var calls = new AtomicInteger();
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                var body = mapper.readTree(exchange.getRequestBody());
                assertThat(body.path("code").asText()).isEqualTo("selected-code");
                assertThat(body.path("digest").asText()).isEqualTo(AneOrderClient.digest(body.path("params").asText(), "selected-code", "selected-key"));
                calls.incrementAndGet();
                var response = "{\"result\":true,\"resultCode\":\"1000\",\"resultInfo\":{\"matchResult\":\"Y\",\"orderNo\":\"BF-SELECT\",\"ewbNo\":\"620000000001\"}}";
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (Throwable error) { failure.set(error); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            var global = new AneProperties();
            global.setCode("global-code"); global.setAppKey("global-key");
            global.setOrderUrl(URI.create("http://127.0.0.1:1/new"));
            var selected = new AneProperties();
            selected.setCode("selected-code"); selected.setAppKey("selected-key");
            selected.setOrderUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/new"));
            var client = new AneOrderClient(global, mapper);
            var params = Map.<String,Object>of("orderNo", "BF-SELECT");
            var created = (AneOrderResult) AneOrderClient.class.getMethod("createOrder", Map.class, AneProperties.class).invoke(client, params, selected);
            var cancelled = (AneCancellationResult) AneOrderClient.class.getMethod("cancelOrder", Map.class, AneProperties.class).invoke(client, params, selected);
            assertThat(failure.get()).isNull();
            assertThat(created.state()).isEqualTo("succeeded");
            assertThat(cancelled.state()).isEqualTo("cancelled");
            assertThat(calls.get()).isEqualTo(2);
            assertThat(global.getCode()).isEqualTo("global-code");
            assertThat(global.getAppKey()).isEqualTo("global-key");
            assertThat(global.getOrderUrl()).isEqualTo(URI.create("http://127.0.0.1:1/new"));
        } finally { server.stop(0); }
    }
}
