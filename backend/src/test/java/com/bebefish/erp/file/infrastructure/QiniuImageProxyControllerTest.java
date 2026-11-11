package com.bebefish.erp.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class QiniuImageProxyControllerTest {
    @TempDir Path cache;
    @Test
    void servesQiniuImageThroughSameOriginEndpoint() {
        QiniuImageReader reader = storageName -> new QiniuImage(
                ("content:" + storageName).getBytes(StandardCharsets.UTF_8),
                MediaType.IMAGE_JPEG_VALUE
        );
        var controller = new QiniuImageProxyController(reader, new QiniuImagePreviewService(reader, cache, "", "", "", Clock.systemUTC()));

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
        var controller = new QiniuImageProxyController(reader, new QiniuImagePreviewService(reader, cache, "", "", "", Clock.systemUTC()));

        var response = controller.image("../secret");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(called).isFalse();
    }

    @Test
    void routesPreviewVariantsWithoutDownloadingTheOriginal() throws Exception {
        QiniuImageReader source = key -> { throw new AssertionError("CDN previews must not proxy original bytes"); };
        var controller = new QiniuImageProxyController(source,
                new QiniuImagePreviewService(source, cache, "https://img.example.com", "ak", "sk", Clock.systemUTC()));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/api/files/content/glass.png").param("variant", "thumb"))
                .andExpect(status().isFound()).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://img.example.com/glass.png?imageView2/")));
        mvc.perform(get("/api/files/content/glass.png").param("variant", "huge"))
                .andExpect(status().isBadRequest());
    }
}
