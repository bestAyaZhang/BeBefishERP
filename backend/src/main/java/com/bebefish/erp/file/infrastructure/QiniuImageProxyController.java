package com.bebefish.erp.file.infrastructure;

import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.nio.file.Path;
import java.time.Clock;

@RestController
@ConditionalOnProperty(name = "erp.file.storage.provider", havingValue = "qiniu")
public class QiniuImageProxyController {
    private static final Pattern STORAGE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,254}");
    private final QiniuImageReader reader;
    private final QiniuImagePreviewService previews;

    @Autowired
    public QiniuImageProxyController(
            @Value("${QINIU_ACCESS_KEY:}") String accessKey,
            @Value("${QINIU_SECRET_KEY:}") String secretKey,
            @Value("${QINIU_BUCKET:}") String bucket,
            @Value("${QINIU_CDN_DOMAIN:}") String cdnDomain,
            @Value("${ERP_IMAGE_CACHE_DIR:${java.io.tmpdir}/bebefish-image-cache}") String cacheDirectory,
            Clock clock
    ) {
        this.reader = new QiniuSdkImageReader(accessKey, secretKey, bucket);
        this.previews = new QiniuImagePreviewService(reader, Path.of(cacheDirectory), cdnDomain, accessKey, secretKey, clock);
    }

    QiniuImageProxyController(QiniuImageReader reader, QiniuImagePreviewService previews) {
        this.reader = reader;
        this.previews = previews;
    }

    @GetMapping(value = "/api/files/content/{storageName:.+}", params = "variant")
    public ResponseEntity<?> preview(@PathVariable String storageName, @RequestParam String variant) {
        return previews.preview(storageName, variant);
    }

    @GetMapping("/api/files/content/{storageName:.+}")
    public ResponseEntity<byte[]> image(@PathVariable String storageName) {
        if (!STORAGE_NAME.matcher(storageName).matches()) {
            return ResponseEntity.notFound().build();
        }
        var image = reader.read(storageName);
        var contentType = MediaType.parseMediaType(image.contentType());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .contentType(contentType)
                .body(image.content());
    }
}
