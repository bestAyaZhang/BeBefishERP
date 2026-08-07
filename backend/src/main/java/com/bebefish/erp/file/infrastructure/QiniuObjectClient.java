package com.bebefish.erp.file.infrastructure;

public interface QiniuObjectClient {
    void put(byte[] content, String key, String contentType);
}
