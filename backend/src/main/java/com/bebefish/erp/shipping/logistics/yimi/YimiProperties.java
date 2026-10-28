package com.bebefish.erp.shipping.logistics.yimi;

import java.net.URI;

public record YimiProperties(boolean testEnvironment, URI gatewayUrl, String appKey, String appSecret,
        String sellerId, String subscriptionId,boolean designatedPickup,String salesmanCode,String salesmanName) {
    public YimiProperties(boolean testEnvironment,URI gatewayUrl,String appKey,String appSecret,String sellerId,String subscriptionId) {
        this(testEnvironment,gatewayUrl,appKey,appSecret,sellerId,subscriptionId,false,"","");
    }
    public static final URI TEST_GATEWAY = URI.create("https://yh-uop-test.yimidida.com/uce-uop-main/api/gateway");
    public static final URI PRODUCTION_GATEWAY = URI.create("https://yh-uop.uce.cn/uce-uop-main/api/gateway");
    public static URI expectedUrl(boolean testEnvironment) { return testEnvironment ? TEST_GATEWAY : PRODUCTION_GATEWAY; }
    public YimiProperties {
        gatewayUrl = gatewayUrl == null ? expectedUrl(testEnvironment) : gatewayUrl;
        validateGateway(gatewayUrl, testEnvironment);
        appKey = clean(appKey); appSecret = clean(appSecret);
        sellerId = clean(sellerId); subscriptionId = clean(subscriptionId);
        salesmanCode=clean(salesmanCode);salesmanName=clean(salesmanName);
        designatedPickup=designatedPickup||!salesmanCode.isEmpty()||!salesmanName.isEmpty();
    }
    public static void validateGateway(URI gatewayUrl, boolean testEnvironment) {
        // Compare the complete spelling too: explicit ports, userinfo, query and fragments are forbidden.
        if (gatewayUrl == null || !expectedUrl(testEnvironment).toString().equals(gatewayUrl.toString()))
            throw new IllegalArgumentException("壹米网关必须使用当前环境的固定 HTTPS 地址");
    }
    public static void validateGateway(boolean testEnvironment, URI gatewayUrl) { validateGateway(gatewayUrl,testEnvironment); }
    public boolean ready() { return !appKey.isBlank() && !appSecret.isBlank() && !sellerId.isBlank() && !subscriptionId.isBlank(); }
    public boolean orderReady(){return ready()&&(!designatedPickup||pickupText(salesmanCode)&&pickupText(salesmanName));}
    private static boolean pickupText(String value){return !value.isBlank()&&value.codePointCount(0,value.length())<=50;}
    @Override public String toString() {
        return "YimiProperties[testEnvironment=" + testEnvironment + ", gatewayUrl=" + gatewayUrl + ", credentials=[REDACTED]]";
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
}
