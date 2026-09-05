package com.bebefish.erp.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class QiniuImageProxyControllerTest {
    @Test
    void servesQiniuImageThroughSameOriginEndpoint() {
        QiniuImageReader reader = storageName -> new QiniuImage(
                ("content:" + storageName).getBytes(StandardCharsets.UTF_8),
                MediaType.IMAGE_JPEG_VALUE
        );
        var controller = new QiniuImageProxyController(reader);

        var response = controller.image("image.jpg");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(response.getBody()).isEqualTo("content:image.jpg".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsStorageNamesThatCouldEscapeTheQiniuBucket() {
        var called = new AtomicBoolean();
        QiniuImageReader reader = storageName -> {
            called.set(true);
            return new QiniuImage(new byte[0], MediaType.IMAGE_JPEG_VALUE);
        };
        var controller = new QiniuImageProxyController(reader);

        var response = controller.image("../secret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(called).isFalse();
    }
}
