package com.bebefish.erp.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class QiniuSdkImageReaderTest {
    @Test
    void downloadsImageBytesFromQiniuSourceWhenTestDomainIsOffline() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        var sourceRequestSigned = new AtomicBoolean();
        server.createContext("/v4/query", exchange -> {
            var query = exchange.getRequestURI().getRawQuery();
            assertThat(query).contains("ak=test-ak", "bucket=test-bucket");
            var body = ("""
                    {"hosts":[{"io_src":{"domains":["127.0.0.1:%d"]}}]}
                    """).formatted(server.getAddress().getPort()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/image.jpg", exchange -> {
            var query = exchange.getRequestURI().getRawQuery();
            sourceRequestSigned.set(query.contains("e=") && query.contains("token=test-ak:"));
            var content = "qiniu-image".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "image/jpeg");
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        server.start();
        try {
            var reader = new QiniuSdkImageReader(
                    "test-ak",
                    "test-sk",
                    "test-bucket",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v4/query",
                    false
            );

            var image = reader.read("image.jpg");

            assertThat(image.content()).isEqualTo("qiniu-image".getBytes(StandardCharsets.UTF_8));
            assertThat(image.contentType()).isEqualTo("image/jpeg");
            assertThat(sourceRequestSigned).isTrue();
        } finally {
            server.stop(0);
        }
    }
}
