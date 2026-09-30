package com.bebefish.erp.shipping.logistics;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.shipping.domain.AneOrderDraft;
import com.bebefish.erp.shipping.domain.ShipmentContent;
import com.bebefish.erp.shipping.domain.ShipmentRepository;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AneOrderService {
    private final AneProperties config;
    private final AneOrderStore store;
    private final AneOrderClient client;
    private final ShipmentRepository shipments;
    private final Validator validator;
    public AneOrderService(AneProperties config, AneOrderStore store, AneOrderClient client,
                           ShipmentRepository shipments, Validator validator) {
        this.config = config; this.store = store; this.client = client; this.shipments = shipments; this.validator = validator;
    }
    public record SenderInfo(String name, String phone, String province, String city, String county, String address) {}
    public record Availability(boolean available, boolean testEnvironment, String message, SenderInfo sender) {}
    public Availability availability() {
        return new Availability(config.ready(), config.testEnvironment(), config.ready()
                ? (config.testEnvironment() ? "安能测试环境：只用于接口联调，不安排实际走货" : "安能物流下单服务已配置")
                : "物流下单服务尚未接入：请配置安能账号、密钥和寄件人资料",
                new SenderInfo(config.getSenderName(), config.getSenderPhone(), config.getSenderProvince(),
                        config.getSenderCity(), config.getSenderCounty(), config.getSenderAddress()));
    }
    public LogisticsOrder get(long id) { return store.find(id).orElse(null); }

    public LogisticsOrder place(long id, PlaceAneOrderRequest input, String operator) {
        if (!config.ready()) throw new BusinessException("LOGISTICS_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE, availability().message());
        if (input == null || !validator.validate(input).isEmpty()) throw invalid("请按要求填写完整的安能下单资料");
        var preview = shipments.findById(id).orElseThrow(() ->
                new BusinessException("SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "发货单不存在"));
        validateOrderContent(preview.content());
        var claim = store.claim(id, input.version(), operator);
        if (claim.existing() != null) return claim.existing();
        var params = orderParams(claim.shipment().content(), claim.orderNo());
        AneOrderResult result = client.createOrder(params);
        return store.finish(id, result, operator);
    }

    public LogisticsOrder cancel(long id, CancelAneOrderRequest input, String operator) {
        if (!config.ready()) throw new BusinessException("LOGISTICS_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE, availability().message());
        if (input == null || !validator.validate(input).isEmpty()) throw invalid("请提供当前发货单版本");
        var snapshot = store.cancellationSnapshot(id);
        validateOrderContent(snapshot);
        // Validate the frozen content before reserving; a legacy/incomplete snapshot must not leave a pending request.
        var claim = store.claimCancellation(id, input.version(), operator);
        if (!claim.claimed()) return claim.order();
        var params = orderParams(snapshot, claim.order().orderNo());
        params.put("action", 10);
        params.put("ewbNo", ""); // The modification/cancellation contract explicitly specifies an empty ewbNo.
        return store.finishCancellation(id, client.cancelOrder(params), operator);
    }

    private LinkedHashMap<String, Object> orderParams(ShipmentContent c, String orderNo) {
        var draft = c.orderDraft();
        var params = new LinkedHashMap<String, Object>();
        params.put("orderNo", orderNo); params.put("productTypeId", draft.productTypeId());
        params.put("goodsType", String.valueOf(draft.goodsType())); params.put("weight", draft.weight());
        params.put("volume", draft.volume()); params.put("pieceAmount", draft.pieceAmount());
        params.put("cargoName", draft.cargoName()); params.put("packType", draft.packType());
        params.put("payType", draft.payType()); params.put("remark", draft.logisticsRemark());
        params.put("sendMan", c.senderName()); params.put("sendPhone", c.senderPhone());
        params.put("customerCode", config.getCustomerCode()); params.put("customerPass", config.getCustomerPass());
        params.put("sendProvinceName", c.senderProvince()); params.put("sendCityName", c.senderCity());
        params.put("sendCountyName", c.senderCounty()); params.put("detailAddress", c.senderDetailAddress());
        params.put("receiveMan", c.recipientName()); params.put("receivePhone", c.recipientPhone());
        params.put("toProvinceName", c.recipientProvince()); params.put("toCityName", c.recipientCity());
        params.put("toCountyName", c.recipientCounty()); params.put("toAddress", c.recipientDetailAddress());
        params.put("dictId", config.getDictId()); params.put("receiptType", 0);
        params.put("signSms", 0); params.put("receiveSendSms", 0); params.put("receiveReachSms", 0);
        return params;
    }

    private void validateOrderContent(ShipmentContent content) {
        if (content == null) throw invalid("原下单资料不完整，请联系安能网点处理取消");
        var draft = content.orderDraft();
        if (blank(content.senderName()) || content.senderName().length() > 30
                || blank(content.senderPhone()) || content.senderPhone().length() > 30
                || blank(content.senderProvince()) || content.senderProvince().length() > 30
                || blank(content.senderCity()) || content.senderCity().length() > 30
                || blank(content.senderCounty()) || content.senderCounty().length() > 30
                || blank(content.senderDetailAddress()) || content.senderDetailAddress().length() > 100) {
            throw invalid("请补全符合安能要求的发货人和地址信息");
        }
        if (blank(content.recipientName()) || content.recipientName().length() > 30
                || blank(content.recipientPhone()) || content.recipientPhone().length() > 30
                || blank(content.recipientProvince()) || content.recipientProvince().length() > 30
                || blank(content.recipientCity()) || content.recipientCity().length() > 30
                || blank(content.recipientCounty()) || content.recipientCounty().length() > 30
                || blank(content.recipientDetailAddress()) || content.recipientDetailAddress().length() > 100) {
            throw invalid("请补全符合安能要求的收件人和地址信息");
        }
        if (draft == null || blank(draft.cargoName()) || draft.cargoName().length() > 32
                || blank(draft.packType()) || draft.packType().length() > 50
                || draft.weight() == null || draft.weight().compareTo(BigDecimal.ONE) < 0
                || draft.volume() == null || draft.volume().compareTo(new BigDecimal("0.01")) < 0
                || draft.pieceAmount() == null || draft.pieceAmount() < 1 || draft.pieceAmount() > 9999) {
            throw invalid("请填写完整的安能货物名称、包装、重量、体积和件数");
        }
        if (draft.productTypeId() == null || !Set.of(95,24,23,524,270,546).contains(draft.productTypeId())
                || draft.goodsType() == null || !Set.of(180,179,285).contains(draft.goodsType())
                || draft.payType() == null || !Set.of(102,103,104).contains(draft.payType())) {
            throw invalid("安能产品类型、送货方式或付款方式无效");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private BusinessException invalid(String message) { return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message); }
}
