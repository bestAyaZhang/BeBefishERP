package com.bebefish.erp.file.domain;

public record FileAsset(
        Long id,
        String originalName,
        String storageName,
        String storagePath,
        String accessUrl,
        String contentType,
        long sizeBytes,
        String status
) {
}
