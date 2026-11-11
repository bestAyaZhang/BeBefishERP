package com.bebefish.erp.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

class QiniuImagePreviewServiceTest {
    @TempDir Path directory;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T08:00:00Z"), ZoneOffset.UTC);

    @Test
    void fitsOpaquePreviewWithoutCroppingAndReusesDiskCacheAcrossInstances() throws Exception {
        var reads = new AtomicInteger();
        QiniuImageReader source = key -> { reads.incrementAndGet(); return image(1600, 1000, false); };
        var first = service(source, "").preview("glass.png", "thumb");
        var rendered = ImageIO.read(new ByteArrayInputStream((byte[]) first.getBody()));
        assertThat(rendered.getWidth()).isEqualTo(160);
        assertThat(rendered.getHeight()).isEqualTo(100);
        assertThat(first.getHeaders().getContentType().toString()).isEqualTo("image/jpeg");
        assertThat(first.getHeaders().getCacheControl()).contains("immutable");
        var second = service(source, "").preview("glass.png", "thumb");
        assertThat(second.getBody()).isEqualTo(first.getBody());
        assertThat(reads).hasValue(1);
    }

    @Test
    void preservesTransparencyAndDoesNotUpscaleSmallImages() throws Exception {
        var response = service(key -> image(8, 4, true), "").preview("alpha.png", "preview");
        var rendered = ImageIO.read(new ByteArrayInputStream((byte[]) response.getBody()));
        assertThat(rendered.getWidth()).isEqualTo(8);
        assertThat(rendered.getHeight()).isEqualTo(4);
        assertThat(rendered.getColorModel().hasAlpha()).isTrue();
        assertThat(rendered.getRGB(0, 0) >>> 24).isZero();
    }

    @Test
    void fitsTallImagesAndSharesOneStorageReadForConcurrentRequests() throws Exception {
        var reads = new AtomicInteger();
        var service = service(key -> { reads.incrementAndGet(); return image(100, 2000, false); }, "");
        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = new ArrayList<java.util.concurrent.Future<org.springframework.http.ResponseEntity<?>>>();
            for (int i = 0; i < 8; i++) results.add(executor.submit(() -> service.preview("long.png", "thumb")));
            for (var result : results) {
                var rendered = ImageIO.read(new ByteArrayInputStream((byte[]) result.get().getBody()));
                assertThat(rendered.getWidth()).isEqualTo(8);
                assertThat(rendered.getHeight()).isEqualTo(160);
            }
        }
        assertThat(reads).hasValue(1);
    }

    @Test
    void rejectsUnknownVariantsAndTraversalBeforeReadingStorage() {
        var reads = new AtomicInteger();
        var service = service(key -> { reads.incrementAndGet(); return image(10, 10, false); }, "");
        assertThat(service.preview("../secret", "thumb").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(service.preview("image.png", "100000").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reads).hasValue(0);
    }

    @Test
    void redirectsUnsupportedFormatsToOriginalInsteadOfReturningBrokenPreview() {
        var response = service(key -> new QiniuImage(new byte[]{1, 2, 3}, "image/webp"), "")
                .preview("glass.webp", "thumb");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/files/content/glass.webp");
    }

    @Test
    void signsCdnProcessingUrlAndKeepsRedirectLifetimeShorterThanSignature() throws Exception {
        var reads = new AtomicInteger();
        var response = service(key -> { reads.incrementAndGet(); return image(10, 10, false); }, "https://img.example.com/")
                .preview("glass.png", "thumb");
        var location = response.getHeaders().getLocation().toString();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(location).startsWith("https://img.example.com/glass.png?imageView2/2/w/160/h/160/format/webp/q/82&e=");
        assertThat(location).contains("&token=test-ak:");
        var signedPart = location.substring(0, location.indexOf("&token="));
        var mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec("test-sk".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA1"));
        var expectedSignature = java.util.Base64.getUrlEncoder().encodeToString(mac.doFinal(signedPart.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertThat(location.substring(location.indexOf("&token=") + 7)).isEqualTo("test-ak:" + expectedSignature);
        assertThat(response.getHeaders().getCacheControl()).contains("max-age=300");
        assertThat(reads).hasValue(0);
    }

    private QiniuImagePreviewService service(QiniuImageReader reader, String domain) {
        return new QiniuImagePreviewService(reader, directory, domain, "test-ak", "test-sk", clock);
    }

    private static QiniuImage image(int width, int height, boolean alpha) {
        try {
            var image = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            if (!alpha) {
                var graphics = image.createGraphics(); graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height); graphics.setColor(Color.BLUE);
                graphics.fillRect(width / 2, 0, width / 2, height); graphics.dispose();
            }
            var output = new ByteArrayOutputStream(); ImageIO.write(image, "png", output);
            return new QiniuImage(output.toByteArray(), "image/png");
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}
