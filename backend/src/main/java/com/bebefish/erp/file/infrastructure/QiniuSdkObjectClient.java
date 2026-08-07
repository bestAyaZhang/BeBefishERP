package com.bebefish.erp.file.infrastructure;

import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
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
                throw new IllegalStateException("七牛云图片上传失败：HTTP " + response.statusCode);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("七牛云图片上传失败", exception);
        }
    }

    private Region region(String regionId) {
        return regionId == null || regionId.isBlank()
                ? Region.autoRegion()
                : Region.createWithRegionId(regionId.trim());
    }
}
