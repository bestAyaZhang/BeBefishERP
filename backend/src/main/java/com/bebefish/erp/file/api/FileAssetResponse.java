package com.bebefish.erp.file.api;

import com.bebefish.erp.file.domain.FileAsset;
import com.bebefish.erp.file.domain.FileAccessUrlResolver;

public record FileAssetResponse(Long id, String url, String contentType, long size) {
    static FileAssetResponse from(FileAsset asset, FileAccessUrlResolver urlResolver) {
        return new FileAssetResponse(
                asset.id(), urlResolver.resolve(asset.accessUrl()), asset.contentType(), asset.sizeBytes()
        );
    }
}
