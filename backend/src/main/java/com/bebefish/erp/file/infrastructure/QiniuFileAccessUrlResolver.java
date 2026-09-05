package com.bebefish.erp.file.infrastructure;

import com.bebefish.erp.file.domain.FileAccessUrlResolver;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "erp.file.storage.provider", havingValue = "qiniu")
public class QiniuFileAccessUrlResolver implements FileAccessUrlResolver {
    @Autowired
    public QiniuFileAccessUrlResolver(
            @Value("${QINIU_ACCESS_KEY:}") String accessKey,
            @Value("${QINIU_SECRET_KEY:}") String secretKey
    ) {
        if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("七牛云配置不完整，请设置 QINIU_ACCESS_KEY、QINIU_SECRET_KEY");
        }
    }

    @Override
    public String resolve(String accessUrl) {
        if (accessUrl == null || accessUrl.isBlank() || accessUrl.startsWith("/")) {
            return accessUrl;
        }
        var path = URI.create(accessUrl).getRawPath();
        var separator = path.lastIndexOf('/');
        var storageName = separator >= 0 ? path.substring(separator + 1) : path;
        return "/api/files/content/" + storageName;
    }
}
