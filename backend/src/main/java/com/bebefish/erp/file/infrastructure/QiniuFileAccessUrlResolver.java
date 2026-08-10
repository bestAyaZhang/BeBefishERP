package com.bebefish.erp.file.infrastructure;

import com.qiniu.util.Auth;
import com.bebefish.erp.file.domain.FileAccessUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "erp.file.storage.provider", havingValue = "qiniu")
public class QiniuFileAccessUrlResolver implements FileAccessUrlResolver {
    private static final long DOWNLOAD_URL_TTL_SECONDS = 3600;
    private final Auth auth;

    @Autowired
    public QiniuFileAccessUrlResolver(
            @Value("${QINIU_ACCESS_KEY:}") String accessKey,
            @Value("${QINIU_SECRET_KEY:}") String secretKey
    ) {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("七牛云配置不完整，请设置 QINIU_ACCESS_KEY、QINIU_SECRET_KEY");
        }
        this.auth = Auth.create(accessKey, secretKey);
    }

    @Override
    public String resolve(String accessUrl) {
        if (accessUrl == null || accessUrl.isBlank() || accessUrl.startsWith("/")) {
            return accessUrl;
        }
        return auth.privateDownloadUrl(toHttpUrl(accessUrl), DOWNLOAD_URL_TTL_SECONDS);
    }

    private String toHttpUrl(String accessUrl) {
        return accessUrl.startsWith("https://")
                ? "http://" + accessUrl.substring("https://".length())
                : accessUrl;
    }
}
