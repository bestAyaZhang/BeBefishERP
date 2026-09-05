package com.bebefish.erp.file.infrastructure;

import com.bebefish.erp.file.domain.FileStorageException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiniu.util.Auth;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.http.MediaType;

final class QiniuSdkImageReader implements QiniuImageReader {
    private static final String DEFAULT_REGION_QUERY_URL = "https://uc.qiniuapi.com/v4/query";
    private static final long DOWNLOAD_URL_TTL_SECONDS = 180;
    private final Auth auth;
    private final String accessKey;
    private final String bucket;
    private final String regionQueryUrl;
    private final boolean sourceHttps;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile String sourceDomain;

    QiniuSdkImageReader(String accessKey, String secretKey, String bucket) {
        this(accessKey, secretKey, bucket, DEFAULT_REGION_QUERY_URL, true);
    }

    QiniuSdkImageReader(
            String accessKey,
            String secretKey,
            String bucket,
            String regionQueryUrl,
            boolean sourceHttps
    ) {
        if (blank(accessKey) || blank(secretKey) || blank(bucket)) {
            throw new IllegalStateException(
                    "七牛云配置不完整，请设置 QINIU_ACCESS_KEY、QINIU_SECRET_KEY、QINIU_BUCKET"
            );
        }
        this.auth = Auth.create(accessKey, secretKey);
        this.accessKey = accessKey;
        this.bucket = bucket;
        this.regionQueryUrl = regionQueryUrl;
        this.sourceHttps = sourceHttps;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public QiniuImage read(String storageName) {
        var sourceUrl = sourceUrl(storageName);
        var signedUrl = auth.privateDownloadUrl(sourceUrl, DOWNLOAD_URL_TTL_SECONDS);
        var request = HttpRequest.newBuilder(URI.create(signedUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FileStorageException("七牛云图片读取失败：HTTP " + response.statusCode());
            }
            var contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            if (!contentType.toLowerCase().startsWith("image/")) {
                throw new FileStorageException("七牛云图片读取失败：响应不是图片");
            }
            return new QiniuImage(response.body(), contentType);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FileStorageException("七牛云图片读取失败", exception);
        } catch (IOException exception) {
            throw new FileStorageException("七牛云图片读取失败", exception);
        }
    }

    private String sourceUrl(String storageName) {
        var scheme = sourceHttps ? "https://" : "http://";
        return scheme + sourceDomain() + "/" + storageName;
    }

    private String sourceDomain() {
        var cached = sourceDomain;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (sourceDomain == null) {
                sourceDomain = querySourceDomain();
            }
            return sourceDomain;
        }
    }

    private String querySourceDomain() {
        var queryUrl = regionQueryUrl
                + "?ak=" + encode(accessKey)
                + "&bucket=" + encode(bucket);
        var request = HttpRequest.newBuilder(URI.create(queryUrl))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FileStorageException("七牛云源站查询失败：HTTP " + response.statusCode());
            }
            var domains = objectMapper.readTree(response.body())
                    .path("hosts")
                    .path(0)
                    .path("io_src")
                    .path("domains");
            if (!domains.isArray() || domains.isEmpty() || domains.path(0).asText().isBlank()) {
                throw new FileStorageException("七牛云源站查询失败：未返回下载域名");
            }
            return stripScheme(domains.path(0).asText());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FileStorageException("七牛云源站查询失败", exception);
        } catch (IOException exception) {
            throw new FileStorageException("七牛云源站查询失败", exception);
        }
    }

    private static String stripScheme(String value) {
        return value.trim()
                .replaceFirst("^https?://", "")
                .replaceAll("/+$", "");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
