package com.bebefish.erp.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QiniuFileAccessUrlResolverTest {
    @Test
    void returnsSameOriginProxyUrlForHttpQiniuImage() {
        var resolver = new QiniuFileAccessUrlResolver("test-ak", "test-sk");

        var resolved = resolver.resolve("http://img.example.com/image.jpg");

        assertThat(resolved).isEqualTo("/api/files/content/image.jpg");
    }
}
