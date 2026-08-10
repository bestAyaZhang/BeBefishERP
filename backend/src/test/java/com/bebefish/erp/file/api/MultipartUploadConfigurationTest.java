package com.bebefish.erp.file.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MultipartUploadConfigurationTest {
    @Value("${spring.servlet.multipart.max-file-size}")
    String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size}")
    String maxRequestSize;

    @Test
    void allowsProductImagesUpToTenMegabytes() {
        assertThat(maxFileSize).isEqualTo("10MB");
        assertThat(maxRequestSize).isEqualTo("10MB");
    }
}
