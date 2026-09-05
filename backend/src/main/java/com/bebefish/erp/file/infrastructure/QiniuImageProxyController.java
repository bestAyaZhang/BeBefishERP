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

@RestController
@ConditionalOnProperty(name = "erp.file.storage.provider", havingValue = "qiniu")
public class QiniuImageProxyController {
    private static final Pattern STORAGE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,254}");
    private final QiniuImageReader reader;

    @Autowired
    public QiniuImageProxyController(
            @Value("${QINIU_ACCESS_KEY:}") String accessKey,
            @Value("${QINIU_SECRET_KEY:}") String secretKey,
            @Value("${QINIU_BUCKET:}") String bucket
    ) {
        this(new QiniuSdkImageReader(accessKey, secretKey, bucket));
    }

    QiniuImageProxyController(QiniuImageReader reader) {
        this.reader = reader;
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
