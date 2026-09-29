package com.bebefish.erp.platform.api;
import jakarta.validation.constraints.*;
public final class PlatformDtos {
    private PlatformDtos() {}
    public record Create(@Size(max=50) String code, @NotBlank @Size(max=200) String name,
        @Min(0) @Max(9999) Integer sortOrder, @Size(max=1000) String remark, @Positive Long platformId,
        String channelType, @Size(max=100) String ownerName, @Size(max=200) String optionLabelOverride) {
        public Create(String code,String name,Integer sortOrder,String remark,Long platformId) {
            this(code,name,sortOrder,remark,platformId,null,null,null);
        }
    }
    public record Update(@NotBlank @Size(max=200) String name, @Min(0) @Max(9999) Integer sortOrder,
        @Size(max=1000) String remark, @NotNull @Min(0) Long version,
        String channelType, @Size(max=100) String ownerName, @Size(max=200) String optionLabelOverride) {
        public Update(String name,Integer sortOrder,String remark,Long version) {
            this(name,sortOrder,remark,version,null,null,null);
        }
    }
    public record Status(@NotBlank @Pattern(regexp="enabled|disabled") String status, @NotNull @Min(0) Long version) {}
}
