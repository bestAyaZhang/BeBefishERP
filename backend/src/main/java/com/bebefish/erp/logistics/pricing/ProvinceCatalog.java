package com.bebefish.erp.logistics.pricing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProvinceCatalog {
    private static final List<Province> OPTIONS = List.of(
            new Province("110000", "北京市", "华北"),
            new Province("120000", "天津市", "华北"),
            new Province("130000", "河北省", "华北"),
            new Province("140000", "山西省", "华北"),
            new Province("150000", "内蒙古自治区", "华北"),
            new Province("210000", "辽宁省", "东北"),
            new Province("220000", "吉林省", "东北"),
            new Province("230000", "黑龙江省", "东北"),
            new Province("310000", "上海市", "华东"),
            new Province("320000", "江苏省", "华东"),
            new Province("330000", "浙江省", "华东"),
            new Province("340000", "安徽省", "华东"),
            new Province("350000", "福建省", "华东"),
            new Province("360000", "江西省", "华东"),
            new Province("370000", "山东省", "华东"),
            new Province("410000", "河南省", "华中"),
            new Province("420000", "湖北省", "华中"),
            new Province("430000", "湖南省", "华中"),
            new Province("440000", "广东省", "华南"),
            new Province("450000", "广西壮族自治区", "华南"),
            new Province("460000", "海南省", "华南"),
            new Province("500000", "重庆市", "西南"),
            new Province("510000", "四川省", "西南"),
            new Province("520000", "贵州省", "西南"),
            new Province("530000", "云南省", "西南"),
            new Province("540000", "西藏自治区", "西南"),
            new Province("610000", "陕西省", "西北"),
            new Province("620000", "甘肃省", "西北"),
            new Province("630000", "青海省", "西北"),
            new Province("640000", "宁夏回族自治区", "西北"),
            new Province("650000", "新疆维吾尔自治区", "西北"),
            new Province("710000", "台湾省", "港澳台"),
            new Province("810000", "香港特别行政区", "港澳台"),
            new Province("820000", "澳门特别行政区", "港澳台"));
    private static final Map<String, String> CODES = aliases();

    private ProvinceCatalog() {}

    public record Province(String code, String name, String group) {}

    /** Only explicit province names/codes are accepted; never infer a province from arbitrary address text. */
    public static String normalize(String value) {
        return value == null ? null : CODES.get(value.strip());
    }

    public static List<Province> options() { return OPTIONS; }

    private static Map<String, String> aliases() {
        var result = new HashMap<String, String>();
        for (var province : OPTIONS) {
            result.put(province.code(), province.code());
            result.put(province.name(), province.code());
            var name = province.name();
            if (name.endsWith("省") || name.endsWith("市")) {
                result.put(name.substring(0, name.length() - 1), province.code());
            }
        }
        result.put("内蒙古", "150000");
        result.put("广西", "450000");
        result.put("西藏", "540000");
        result.put("宁夏", "640000");
        result.put("新疆", "650000");
        result.put("香港", "810000");
        result.put("澳门", "820000");
        return Map.copyOf(result);
    }
}
