package com.bebefish.erp.file.infrastructure;

import com.bebefish.erp.file.domain.FileStorage;
import com.bebefish.erp.file.domain.ImageUpload;
import com.bebefish.erp.file.domain.StoredFile;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "erp.file.storage.provider", havingValue = "qiniu")
public class QiniuFileStorage implements FileStorage {
    private final QiniuObjectClient client;
    private final String domain;

    @Autowired
    public QiniuFileStorage(
            @Value("${QINIU_ACCESS_KEY:}") String accessKey,
            @Value("${QINIU_SECRET_KEY:}") String secretKey,
            @Value("${QINIU_BUCKET:}") String bucket,
            @Value("${QINIU_DOMAIN:}") String domain,
            @Value("${QINIU_REGION:}") String regionId
    ) {
        this(buildClient(accessKey, secretKey, bucket, domain, regionId), domain);
    }

    QiniuFileStorage(QiniuObjectClient client, String domain) {
        this.client = client;
        this.domain = normalizeDomain(domain);
    }

    @Override
    public StoredFile store(ImageUpload upload) {
        var storageName = UUID.randomUUID() + extension(upload.originalName());
        client.put(upload.content(), storageName, upload.contentType());
        return new StoredFile(storageName, storageName, domain + "/" + storageName);
    }

    private static QiniuObjectClient buildClient(
            String accessKey,
            String secretKey,
            String bucket,
            String domain,
            String regionId
    ) {
        if (blank(accessKey) || blank(secretKey) || blank(bucket) || blank(domain)) {
            throw new IllegalStateException(
                    "七牛云配置不完整，请设置 QINIU_ACCESS_KEY、QINIU_SECRET_KEY、QINIU_BUCKET、QINIU_DOMAIN"
            );
        }
        return new QiniuSdkObjectClient(accessKey, secretKey, bucket, regionId);
    }

    private static String normalizeDomain(String value) {
        var result = value.trim();
        if (!result.startsWith("http://") && !result.startsWith("https://")) {
            result = "http://" + result;
        }
        return result.replaceAll("/+$", "");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String extension(String originalName) {
        if (originalName == null) {
            return "";
        }
        var index = originalName.lastIndexOf('.');
        return index < 0 ? "" : originalName.substring(index).toLowerCase();
    }
}
