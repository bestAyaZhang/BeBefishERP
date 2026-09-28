package com.bebefish.erp.shipping.logistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AneOrderClientTest {
    @Test
    void signsHexMd5AsBase64RatherThanRawDigestBytes() {
        // MD5("1") = c4ca4238a0b923820dcc509a6f75849b.
        assertThat(AneOrderClient.digest("1", "", "")).isEqualTo("YzRjYTQyMzhhMGI5MjM4MjBkY2M1MDlhNmY3NTg0OWI=");
    }

    @Test
    void postsStringParamsAndParsesNestedResultInfoWithoutAutoRetries() throws Exception {
        var request = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/new", exchange -> {
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"result\":true,\"resultCode\":\"1000\",\"resultInfo\":\"{\\\"matchResult\\\":\\\"Y\\\",\\\"orderNo\\\":\\\"BF000000000001\\\",\\\"ewbNo\\\":\\\"620240314001\\\",\\\"childEwbNo\\\":\\\"620240314001001\\\"}\"}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var config = new AneProperties();
            config.setOrderUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/new"));
            config.setCode("test-code"); config.setAppKey("test-key");
            var client = new AneOrderClient(config, new ObjectMapper());
            var response = client.createOrder(Map.of("orderNo", "BF000000000001", "cargoName", "测试箱"));
            assertThat(response.state()).isEqualTo("succeeded");
            assertThat(response.trackingNo()).isEqualTo("620240314001");
            var json = new ObjectMapper().readTree(request.get());
            assertThat(json.get("params").isTextual()).isTrue();
            assertThat(json.get("code").asText()).isEqualTo("test-code");
            assertThat(json.get("digest").asText()).isEqualTo(AneOrderClient.digest(json.get("params").asText(), "test-code", "test-key"));
        } finally { server.stop(0); }
    }

    @Test
    void doesNotTreatBlindAreaOrMismatchedOrderAsSuccessfulWaybill() throws Exception {
        var client = new AneOrderClient(new AneProperties(), new ObjectMapper());
        assertThat(client.parse("{\"result\":true,\"resultCode\":\"1000\",\"resultInfo\":{\"matchResult\":\"N\",\"orderNo\":\"BF1\"}}", "BF1").state()).isEqualTo("unknown");
        assertThat(client.parse("{\"result\":true,\"resultCode\":\"1000\",\"resultInfo\":{\"matchResult\":\"Y\",\"orderNo\":\"OTHER\",\"ewbNo\":\"620240314001\"}}", "BF1").state()).isEqualTo("unknown");
        assertThat(client.parse("{\"result\":false,\"resultCode\":\"2002\",\"reason\":\"摘要验证失败\"}", "BF1").state()).isEqualTo("rejected");
    }

    @Test
    void preservesTheUpstreamFailureCodeAndReasonForDiagnosis() throws Exception {
        var client = new AneOrderClient(new AneProperties(), new ObjectMapper());

        var response = client.parse("{\"result\":false,\"resultCode\":\"1001\",\"reason\":\"该客户没有库存\"}", "BF1");

        assertThat(response.state()).isEqualTo("unknown");
        assertThat(response.message()).contains("1001").contains("该客户没有库存");
    }
}
