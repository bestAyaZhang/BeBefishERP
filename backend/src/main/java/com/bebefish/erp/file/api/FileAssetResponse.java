package com.bebefish.erp.file.api;

import com.bebefish.erp.file.domain.FileAsset;

public record FileAssetResponse(Long id, String url, String contentType, long size) {
    static FileAssetResponse from(FileAsset asset) {
        return new FileAssetResponse(asset.id(), asset.accessUrl(), asset.contentType(), asset.sizeBytes());
    }
}
