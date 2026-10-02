package com.bebefish.erp.shipping.logistics;

import java.net.URI;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "erp.shipping.ane")
public class AneProperties {
    private boolean enabled;
    private URI orderUrl = URI.create("https://opc.test.ane56.com/aneop/opwb/lb/new");
    private String code = "", appKey = "", customerCode = "", customerPass = "", dictId = "";
    private String senderName = "", senderPhone = "", senderProvince = "", senderCity = "", senderCounty = "", senderAddress = "";

    public boolean ready() {
        if (!enabled || orderUrl == null || !"https".equals(orderUrl.getScheme()) || orderUrl.getHost() == null
                || !orderUrl.getHost().endsWith(".ane56.com") || orderUrl.getUserInfo() != null) return false;
        return Stream.of(code, appKey, customerCode, customerPass, dictId, senderName, senderPhone, senderProvince,
                senderCity, senderCounty, senderAddress).allMatch(s -> s != null && !s.isBlank())
                && senderName.length() <= 30 && senderPhone.length() <= 30 && senderProvince.length() <= 30
                && senderCity.length() <= 30 && senderCounty.length() <= 30 && senderAddress.length() <= 100
                && customerCode.length() <= 30 && customerPass.length() <= 30 && dictId.length() <= 30;
    }
    public boolean testEnvironment() { return orderUrl != null && "opc.test.ane56.com".equals(orderUrl.getHost()); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public URI getOrderUrl() { return orderUrl; }
    public URI getUpdateUrl() {
        if (orderUrl == null || !orderUrl.getPath().endsWith("/new"))
            throw new IllegalStateException("安能下单地址无法派生取消接口");
        return orderUrl.resolve(orderUrl.getPath().replaceFirst("/new$", "/update"));
    }
    public void setOrderUrl(URI value) { orderUrl = value; }
    public String getCode() { return code; }
    public void setCode(String value) { code = value; }
    public String getAppKey() { return appKey; }
    public void setAppKey(String value) { appKey = value; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String value) { customerCode = value; }
    public String getCustomerPass() { return customerPass; }
    public void setCustomerPass(String value) { customerPass = value; }
    public String getDictId() { return dictId; }
    public void setDictId(String value) { dictId = value; }
    public String getSenderName() { return senderName; }
    public void setSenderName(String value) { senderName = value; }
    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String value) { senderPhone = value; }
    public String getSenderProvince() { return senderProvince; }
    public void setSenderProvince(String value) { senderProvince = value; }
    public String getSenderCity() { return senderCity; }
    public void setSenderCity(String value) { senderCity = value; }
    public String getSenderCounty() { return senderCounty; }
    public void setSenderCounty(String value) { senderCounty = value; }
    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String value) { senderAddress = value; }
}
