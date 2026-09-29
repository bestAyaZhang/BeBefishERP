package com.bebefish.erp.platform.domain;
import java.time.LocalDateTime;
public record PlatformShop(long id, long platformId, String code, String name, String status, int sortOrder,
        String remark, long version, String createdBy, String updatedBy, LocalDateTime createdAt,
        LocalDateTime updatedAt, String platformName, String platformStatus,
        String channelType, String ownerName, String optionLabel) {
    public PlatformShop(long id,long platformId,String code,String name,String status,int sortOrder,String remark,long version,
            String createdBy,String updatedBy,LocalDateTime createdAt,LocalDateTime updatedAt,String platformName,String platformStatus) {
        this(id,platformId,code,name,status,sortOrder,remark,version,createdBy,updatedBy,createdAt,updatedAt,platformName,platformStatus,
            "ecommerce","",name);
    }
}
