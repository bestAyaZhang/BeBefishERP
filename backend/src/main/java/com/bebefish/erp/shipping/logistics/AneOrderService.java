package com.bebefish.erp.shipping.logistics;

import com.bebefish.erp.common.api.BusinessException;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AneOrderService {
    private final AneProperties config;
    private final AneOrderStore store;
    private final AneOrderClient client;
    private final Validator validator;
    public AneOrderService(AneProperties config, AneOrderStore store, AneOrderClient client, Validator validator) {
        this.config = config; this.store = store; this.client = client; this.validator = validator;
    }
    public record Availability(boolean available, boolean testEnvironment, String message) {}
    public Availability availability() {
        return new Availability(config.ready(), config.testEnvironment(), config.ready()
                ? (config.testEnvironment() ? "安能测试环境：只用于接口联调，不安排实际走货" : "安能物流下单服务已配置")
                : "物流下单服务尚未接入：请配置安能账号、密钥和寄件人资料");
    }
    public LogisticsOrder get(long id) { return store.find(id).orElse(null); }

    public LogisticsOrder place(long id, PlaceAneOrderRequest input, String operator) {
        if (!config.ready()) throw new BusinessException("LOGISTICS_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE, availability().message());
        if (input == null || !validator.validate(input).isEmpty()) throw invalid("请按要求填写完整的安能下单资料");
        if (!Set.of(95,24,23,524,270,546).contains(input.productTypeId()) || !Set.of(180,179,285).contains(input.goodsType())
                || !Set.of(102,103,104).contains(input.payType())) throw invalid("安能产品类型、送货方式或付款方式无效");
        var claim = store.claim(id, input, operator);
        if (claim.existing() != null) return claim.existing();
        var c = claim.shipment().content();
        var params = new LinkedHashMap<String, Object>();
        params.put("orderNo", claim.orderNo()); params.put("productTypeId", input.productTypeId());
        params.put("goodsType", String.valueOf(input.goodsType())); params.put("weight", input.weight());
        params.put("volume", input.volume()); params.put("pieceAmount", input.pieceAmount());
        params.put("cargoName", input.cargoName().strip()); params.put("packType", input.packType().strip());
        params.put("payType", input.payType()); params.put("remark", input.remark() == null ? "" : input.remark().strip());
        params.put("sendMan", config.getSenderName()); params.put("sendPhone", config.getSenderPhone());
        params.put("customerCode", config.getCustomerCode()); params.put("customerPass", config.getCustomerPass());
        params.put("sendProvinceName", config.getSenderProvince()); params.put("sendCityName", config.getSenderCity());
        params.put("sendCountyName", config.getSenderCounty()); params.put("detailAddress", config.getSenderAddress());
        params.put("receiveMan", c.recipientName()); params.put("receivePhone", c.recipientPhone());
        params.put("toProvinceName", input.province().strip()); params.put("toCityName", input.city().strip());
        params.put("toCountyName", input.county().strip()); params.put("toAddress", input.address().strip());
        params.put("dictId", config.getDictId()); params.put("receiptType", 0);
        params.put("signSms", 0); params.put("receiveSendSms", 0); params.put("receiveReachSms", 0);
        AneOrderResult result = client.createOrder(params);
        return store.finish(id, input, result, operator);
    }
    private BusinessException invalid(String message) { return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message); }
}
