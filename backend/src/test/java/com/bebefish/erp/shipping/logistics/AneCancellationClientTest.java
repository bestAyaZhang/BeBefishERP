package com.bebefish.erp.shipping.logistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.*;

class AneCancellationClientTest {
    @Test void usesTheSameEnvironmentAndSignsTheDocumentedCancelAction() throws Exception {
        var calls = new AtomicInteger();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/update", exchange -> {
            calls.incrementAndGet();
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"result\":true,\"resultCode\":\"1000\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var config = new AneProperties();
            config.setOrderUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/new"));
            config.setCode("test-code"); config.setAppKey("test-key");
            var response = new AneOrderClient(config, new ObjectMapper()).cancelOrder(Map.of("orderNo", "BF1", "action", 10, "ewbNo", ""));
            assertThat(response.state()).isEqualTo("cancelled");
            assertThat(calls.get()).isEqualTo(1);
            var envelope = new ObjectMapper().readTree(body.get());
            String params = envelope.path("params").asText();
            assertThat(new ObjectMapper().readTree(params).path("action").asInt()).isEqualTo(10);
            assertThat(envelope.path("digest").asText()).isEqualTo(AneOrderClient.digest(params, "test-code", "test-key"));
            config.setOrderUrl(URI.create("https://opc.ane56.com/aneop/opwb/lb/new"));
            assertThat(config.getUpdateUrl().toString()).isEqualTo("https://opc.ane56.com/aneop/opwb/lb/update");
            config.setOrderUrl(URI.create("https://opc.test.ane56.com/aneop/opwb/lb/new"));
            assertThat(config.getUpdateUrl().toString()).isEqualTo("https://opc.test.ane56.com/aneop/opwb/lb/update");
        } finally { server.stop(0); }
    }

    @Test void distinguishesExplicitRefusalFromUncertainAndContradictoryResponses() throws Exception {
        var client = new AneOrderClient(new AneProperties(), new ObjectMapper());
        assertThat(client.parseCancellation("{\"result\":false,\"resultCode\":\"1001\",\"reason\":\"订单已揽收\"}", "BF1"))
                .satisfies(result -> { assertThat(result.state()).isEqualTo("cancel_rejected"); assertThat(result.message()).contains("订单已揽收"); });
        assertThat(client.parseCancellation("{\"result\":false,\"resultCode\":\"2002\"}", "BF1").state()).isEqualTo("cancel_rejected");
        for (String response : new String[] {
                "{\"result\":false,\"resultCode\":\"9999\"}",
                "{\"result\":false,\"resultCode\":\"1000\"}",
                "{\"result\":true,\"resultCode\":\"1001\"}",
                "{\"result\":false,\"resultCode\":\"1001\",\"resultInfo\":{\"orderNo\":\"OTHER\"}}",
                "{\"result\":true,\"resultCode\":\"1000\",\"resultInfo\":{\"orderNo\":\"OTHER\"}}" }) {
            assertThat(client.parseCancellation(response, "BF1").state()).isEqualTo("cancel_unknown");
        }
    }

    @Test void anHttpFailureStaysUncertainWithoutRetrying() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/update", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            var config = new AneProperties();
            config.setOrderUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/new"));
            var result = new AneOrderClient(config, new ObjectMapper()).cancelOrder(Map.of("orderNo", "BF1", "action", 10));
            assertThat(result.state()).isEqualTo("cancel_unknown");
            assertThat(result.message()).contains("HTTP 503", "勿重复取消");
            assertThat(calls.get()).isEqualTo(1);
        } finally { server.stop(0); }
    }

    @Test void malformedResponsesExplainTheFailureWithoutLeakingPayloads() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(AneOrderClient.class);
        var logs = new ListAppender<ILoggingEvent>();
        logs.start(); logger.addAppender(logs);
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/update", exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            byte[] response = "<html>secret-response-contact</html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            var config = new AneProperties();
            config.setOrderUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/new"));
            config.setCode("private-code"); config.setAppKey("private-key");
            var result = new AneOrderClient(config, new ObjectMapper()).cancelOrder(Map.of("orderNo", "BF1", "action", 10,
                    "customerPass", "private-pass", "receivePhone", "private-phone"));
            assertThat(result.state()).isEqualTo("cancel_unknown");
            assertThat(result.message()).contains("JSON", "勿重复取消")
                    .doesNotContain("secret-response-contact", "private-pass", "private-phone", "private-key");
            assertThat(calls.get()).isEqualTo(1);
            assertThat(logs.list).isNotEmpty();
            String recorded = logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(java.util.stream.Collectors.joining("\n"));
            assertThat(recorded).contains("HTTP", "200", "JSON").doesNotContain("secret-response-contact", "private-code", "private-key", "private-pass", "private-phone");
            assertThat(logs.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        } finally { server.stop(0); logger.detachAppender(logs); logs.stop(); }
    }

    @Test void uncertainResponsesRetainSafeResultCodesAndIdentifyOrderMismatch() throws Exception {
        var client = new AneOrderClient(new AneProperties(), new ObjectMapper());
        assertThat(client.parseCancellation("{\"result\":false,\"resultCode\":\"9999\",\"reason\":\"private-contact\"}", "BF1"))
                .satisfies(result -> {
                    assertThat(result.state()).isEqualTo("cancel_unknown");
                    assertThat(result.message()).contains("9999").doesNotContain("private-contact");
                });
        assertThat(client.parseCancellation("{\"result\":true,\"resultCode\":\"1000\",\"resultInfo\":{\"orderNo\":\"private-other-order\"}}", "BF1").message())
                .contains("订单号不匹配", "1000").doesNotContain("private-other-order");
        assertThat(client.parseCancellation("{\"result\":true,\"resultCode\":\"1000\",\"resultInfo\":\"private-invalid-info\"}", "BF1"))
                .satisfies(result -> {
                    assertThat(result.state()).isEqualTo("cancel_unknown");
                    assertThat(result.message()).contains("resultInfo", "JSON", "1000").doesNotContain("private-invalid-info");
                });
        assertThat(client.parseCancellation("{\"result\":true,\"resultCode\":\"private-code\"}", "BF1").message())
                .contains("resultCode").doesNotContain("private-code");
        assertThat(client.parseCancellation("null", "BF1").message()).contains("响应结构");
    }
}
