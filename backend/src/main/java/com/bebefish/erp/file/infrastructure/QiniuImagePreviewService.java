package com.bebefish.erp.file.infrastructure;

import com.bebefish.erp.file.domain.FileStorageException;
import com.qiniu.util.Auth;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class QiniuImagePreviewService {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,254}");
    private static final long MAX_CACHE_BYTES = 256L * 1024 * 1024;
    private static final Rendered ORIGINAL = new Rendered(new byte[]{0}, "");
    private final QiniuImageReader source;
    private final Path directory;
    private final String cdnDomain;
    private final Auth auth;
    private final Clock clock;
    private final Semaphore decoding = new Semaphore(2);
    private final ConcurrentHashMap<String, CompletableFuture<Rendered>> inFlight = new ConcurrentHashMap<>();
    private long cacheBytes;

    QiniuImagePreviewService(QiniuImageReader source, Path directory, String domain, String accessKey, String secretKey, Clock clock) {
        this.source = source; this.directory = directory; this.clock = clock;
        this.cdnDomain = normalizeDomain(domain);
        this.auth = cdnDomain.isEmpty() ? null : Auth.create(accessKey, secretKey);
        try {
            Files.createDirectories(directory);
            try (var files = Files.list(directory)) {
                cacheBytes = files.filter(this::isCacheFile).mapToLong(this::size).sum();
            }
        } catch (IOException exception) { throw new FileStorageException("图片预览缓存目录不可用", exception); }
    }

    ResponseEntity<?> preview(String key, String variant) {
        if (!KEY.matcher(key).matches()) return ResponseEntity.notFound().build();
        int dimension = switch (variant) { case "thumb" -> 160; case "preview" -> 800; default -> 0; };
        if (dimension == 0) return ResponseEntity.badRequest().build();
        if (!cdnDomain.isEmpty()) {
            // Include processing parameters in the signed resource and keep URLs stable within a time window.
            long deadline = (clock.instant().getEpochSecond() / 1800 + 3) * 1800;
            String url = cdnDomain + "/" + key + "?imageView2/2/w/" + dimension + "/h/" + dimension + "/format/webp/q/82";
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(auth.privateDownloadUrlWithDeadline(url, deadline)))
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic()).build();
        }
        if (key.toLowerCase(java.util.Locale.ROOT).endsWith(".webp") && !ImageIO.getImageReadersByFormatName("webp").hasNext())
            return originalRedirect(key);
        var rendered = cachedPreview(key, variant, dimension);
        if (rendered == ORIGINAL) return originalRedirect(key);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(rendered.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(rendered.bytes());
    }

    private ResponseEntity<?> originalRedirect(String key) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create("/api/files/content/" + key))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).build();
    }

    private Rendered cachedPreview(String key, String variant, int dimension) {
        String name = "preview-v1-" + digest(key) + "-" + variant;
        var cached = readCache(name);
        if (cached != null) return cached;
        var pending = new CompletableFuture<Rendered>();
        var existing = inFlight.putIfAbsent(name, pending);
        if (existing != null) return join(existing);
        try {
            // Recheck after becoming owner: an earlier request may have just finished writing its cache.
            cached = readCache(name);
            if (cached != null) { pending.complete(cached); return cached; }
            decoding.acquire();
            Rendered rendered;
            try { rendered = render(source.read(key), dimension); }
            finally { decoding.release(); }
            writeCache(name, rendered);
            pending.complete(rendered); return rendered;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            var failure = new FileStorageException("图片预览生成被中断", exception);
            pending.completeExceptionally(failure); throw failure;
        } catch (RuntimeException exception) { pending.completeExceptionally(exception); throw exception; }
        finally { inFlight.remove(name, pending); }
    }

    private Rendered render(QiniuImage original, int dimension) {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(original.content()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return ORIGINAL;
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 80_000_000L) return ORIGINAL;
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / dimension);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                int orientation = JpegOrientation.read(original.content());
                var decoded = JpegOrientation.apply(reader.read(0, parameters), orientation);
                if (orientation >= 5) { int previousWidth = width; width = height; height = previousWidth; }
                double ratio = Math.min(1d, (double) dimension / Math.max(width, height));
                int targetWidth = Math.max(1, (int) Math.round(width * ratio));
                int targetHeight = Math.max(1, (int) Math.round(height * ratio));
                boolean alpha = decoded.getColorModel().hasAlpha();
                var result = new BufferedImage(targetWidth, targetHeight, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                var graphics = result.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(decoded, 0, 0, targetWidth, targetHeight, null);
                } finally { graphics.dispose(); decoded.flush(); }
                var bytes = new ByteArrayOutputStream();
                ImageIO.write(result, alpha ? "png" : "jpeg", bytes); result.flush();
                return new Rendered(bytes.toByteArray(), alpha ? "image/png" : "image/jpeg");
            } finally { reader.dispose(); }
        } catch (IOException exception) { throw new FileStorageException("图片预览生成失败", exception); }
    }

    private Rendered readCache(String name) {
        for (String extension : new String[]{".png", ".jpg", ".original"}) {
            Path file = directory.resolve(name + extension);
            try {
                if (Files.isRegularFile(file)) return extension.equals(".original") ? ORIGINAL
                        : new Rendered(Files.readAllBytes(file), extension.equals(".png") ? "image/png" : "image/jpeg");
            } catch (java.nio.file.NoSuchFileException ignored) { /* A concurrent cache cleanup can evict this entry. */ }
            catch (IOException exception) { throw new FileStorageException("图片预览缓存读取失败", exception); }
        }
        return null;
    }

    private void writeCache(String name, Rendered image) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile(directory, "preview-", ".tmp");
            Files.write(temporary, image.bytes());
            Files.move(temporary, directory.resolve(name + (image == ORIGINAL ? ".original" : image.contentType().equals("image/png") ? ".png" : ".jpg")),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            synchronized (this) {
                cacheBytes += image.bytes().length;
                if (cacheBytes > MAX_CACHE_BYTES) evict();
            }
        } catch (IOException exception) { throw new FileStorageException("图片预览缓存写入失败", exception); }
        finally { if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { } }
    }

    private void evict() throws IOException {
        try (var files = Files.list(directory)) {
            var oldest = files.filter(this::isCacheFile).sorted(Comparator.comparingLong(file -> {
                try { return Files.getLastModifiedTime(file).toMillis(); } catch (IOException ignored) { return 0L; }
            })).toList();
            for (Path file : oldest) {
                if (cacheBytes <= MAX_CACHE_BYTES * 7 / 8) break;
                long bytes = size(file); if (Files.deleteIfExists(file)) cacheBytes -= bytes;
            }
        }
    }

    private boolean isCacheFile(Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file) && name.startsWith("preview-v1-") && (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".original"));
    }
    private long size(Path file) { try { return Files.size(file); } catch (IOException ignored) { return 0L; } }
    private static Rendered join(CompletableFuture<Rendered> result) {
        try { return result.join(); } catch (CompletionException exception) { throw (RuntimeException) exception.getCause(); }
    }
    private static String digest(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static String normalizeDomain(String value) {
        if (value == null || value.isBlank()) return "";
        var uri = URI.create(value.trim());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getUserInfo() != null || (uri.getPath() != null && !uri.getPath().matches("/?"))
                || uri.getHost().endsWith(".clouddn.com")) throw new IllegalArgumentException("七牛 CDN 必须使用正式 HTTPS 域名");
        return value.trim().replaceAll("/+$", "");
    }
    private record Rendered(byte[] bytes, String contentType) { }
}
