package com.bebefish.erp.platform.domain;
import java.time.LocalDateTime;
public record Platform(long id, String code, String name, String status, int sortOrder, String remark,
        long version, String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, long shopCount) {}
