package com.bebefish.erp.file.domain;

public record ImageUpload(String originalName, String contentType, byte[] content) {
    public ImageUpload {
        content = content == null ? new byte[0] : content.clone();
    }

    public long sizeBytes() {
        return content.length;
    }
}
