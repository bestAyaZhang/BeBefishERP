package com.bebefish.erp.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QiniuFileAccessUrlResolverTest {
    @Test
    void signsPrivateHttpUrlForDefaultQiniuDomain() {
        var resolver = new QiniuFileAccessUrlResolver("test-ak", "test-sk");

        var resolved = resolver.resolve("https://img.example.com/image.jpg");

        assertThat(resolved)
                .startsWith("http://img.example.com/image.jpg?")
                .contains("e=")
                .contains("&token=test-ak:");
    }
}
