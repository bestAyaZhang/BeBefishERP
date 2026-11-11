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
    void unsupportedWebpDoesNotReadStorageBeforeRedirecting() {
        var reads = new AtomicInteger();
        var service = service(key -> { reads.incrementAndGet(); return new QiniuImage(new byte[]{1, 2, 3}, "image/webp"); }, "");
        for (int i = 0; i < 3; i++) assertThat(service.preview("glass.webp", "thumb").getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(reads).hasValue(0);
    }

    @Test
    void cachesUnsupportedContentsEvenWhenTheFilenameIsNotWebp() {
        var reads = new AtomicInteger();
        QiniuImageReader source = key -> { reads.incrementAndGet(); return new QiniuImage(new byte[]{1, 2, 3}, "image/webp"); };
        for (int i = 0; i < 3; i++) assertThat(service(source, "").preview("renamed.png", "thumb").getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(reads).hasValue(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1,40,20,false", "2,40,20,true", "3,40,20,true", "4,40,20,false",
            "5,20,40,false", "6,20,40,false", "7,20,40,true", "8,20,40,true"})
    void honorsPhonePhotoRotationAndMirroring(int orientation, int width, int height, boolean topLeftBlue) throws Exception {
        var jpeg = orientedJpeg(orientation);
        var response = service(key -> new QiniuImage(jpeg, "image/jpeg"), "").preview("phone.jpg", "thumb");
        var rendered = ImageIO.read(new ByteArrayInputStream((byte[]) response.getBody()));
        assertThat(rendered.getWidth()).isEqualTo(width); assertThat(rendered.getHeight()).isEqualTo(height);
        var corner = new Color(rendered.getRGB(2, 2));
        assertThat(corner.getRed() < 80 && corner.getBlue() > 150).isEqualTo(topLeftBlue);
    }

    @Test
    void evictsOldDiskPreviewsWhenCacheReachesItsCapacity() throws Exception {
        var old = directory.resolve("preview-v1-old-thumb.jpg");
        try (var file = java.nio.channels.FileChannel.open(old, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
            file.position(256L * 1024 * 1024 - 1); file.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        java.nio.file.Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(1));
        var service = service(key -> image(100, 50, false), "");
        service.preview("new.png", "thumb");
        assertThat(java.nio.file.Files.exists(old)).isFalse();
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

    private static byte[] orientedJpeg(int orientation) throws Exception {
        var bitmap = ImageIO.read(new ByteArrayInputStream(image(40, 20, false).content()));
        var jpeg = new ByteArrayOutputStream(); ImageIO.write(bitmap, "jpeg", jpeg);
        var raw = jpeg.toByteArray();
        var output = new ByteArrayOutputStream(); output.write(raw, 0, 2);
        output.write(new byte[]{(byte) 0xff,(byte) 0xe1,0,34,'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,(byte) orientation,0,0,0,0,0,0,0});
        output.write(raw, 2, raw.length - 2); return output.toByteArray();
    }
}
