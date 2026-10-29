package com.bebefish.erp.masterdata.domain;

public record Customer(
        Long id, String number, String name, String contactPerson, String mobile, String telephone,
        String province, String city, String district, String detailAddress,
        String transportMethod, String settlementCycle,
        boolean system, String status, String remark, Long platformId
) {
    public Customer(Long id, String number, String name, String contactPerson, String mobile, String telephone,
                    String province, String city, String district, String detailAddress,
                    String transportMethod, String settlementCycle, boolean system, String status, String remark) {
        this(id, number, name, contactPerson, mobile, telephone, province, city, district, detailAddress,
                transportMethod, settlementCycle, system, status, remark, null);
    }
}
