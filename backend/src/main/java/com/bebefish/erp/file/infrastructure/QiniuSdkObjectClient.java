package com.bebefish.erp.file.infrastructure;

import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.bebefish.erp.file.domain.FileStorageException;
import java.io.IOException;

public class QiniuSdkObjectClient implements QiniuObjectClient {
    private final String bucket;
    private final Auth auth;
    private final UploadManager uploadManager;

    public QiniuSdkObjectClient(String accessKey, String secretKey, String bucket, String regionId) {
        this.bucket = bucket;
        this.auth = Auth.create(accessKey, secretKey);
        this.uploadManager = new UploadManager(new Configuration(region(regionId)));
    }

    @Override
    public void put(byte[] content, String key, String contentType) {
        try {
            var uploadToken = auth.uploadToken(bucket);
            Response response = uploadManager.put(content, key, uploadToken, null, contentType, false);
            if (!response.isOK()) {
                var reason = response.error == null || response.error.isBlank()
                        ? "HTTP " + response.statusCode
                        : "HTTP " + response.statusCode + "：" + response.error;
                throw new FileStorageException("七牛云图片上传失败：" + reason);
            }
        } catch (IOException exception) {
            throw new FileStorageException("七牛云图片上传失败", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof FileStorageException) {
                throw exception;
            }
            throw new FileStorageException("七牛云图片上传失败", exception);
        }
    }

    private Region region(String regionId) {
        return regionId == null || regionId.isBlank()
                ? Region.autoRegion()
                : Region.createWithRegionId(regionId.trim());
    }
}
