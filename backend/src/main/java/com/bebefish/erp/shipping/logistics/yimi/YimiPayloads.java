package com.bebefish.erp.shipping.logistics.yimi;
import com.bebefish.erp.shipping.domain.ShipmentContent;
import com.bebefish.erp.shipping.domain.AneOrderDraft;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map;
public final class YimiPayloads {
    private YimiPayloads() { }
    public static Map<String,Object> orderParams(ShipmentContent content,String outsideNo,YimiProperties account,String productCode) {
        validateOrderContent(content, productCode);
        require(account != null && account.ready(), "壹米接口配置不完整，请补充商家及订购信息");
        text(outsideNo, 50, true, "客户订单号");
        var draft = content.orderDraft();
        var options = draft.yimi();
        var body = new TreeMap<String,Object>();
        body.put("sellerId", account.sellerId()); body.put("subscriptionId", account.subscriptionId());
        body.put("outSideNo", outsideNo); body.put("operatorType", "add"); body.put("serviceType", Integer.valueOf(productCode));
        body.put("settlementType", switch (draft.payType()) { case 102 -> 1; case 104 -> 2; case 103 -> 3; default -> throw new IllegalArgumentException("付款方式不受支持"); });
        body.put("visitDelivery", options.visitDelivery());
        int delivery = deliveryFlag(draft);
        body.put("deliveryFlag", delivery);
        if (delivery == 3 && !clean(options.floorNo()).isEmpty()) body.put("floorNo", clean(options.floorNo()));
        var sender = new TreeMap<String,Object>();
        sender.put("senderName", clean(content.senderName())); sender.put("senderPhone", clean(content.senderPhone()));
        sender.put("senderMobile", clean(content.senderPhone())); sender.put("senderProvince", clean(content.senderProvince()));
        sender.put("senderCity", clean(content.senderCity())); sender.put("senderCounty", clean(content.senderCounty()));
        sender.put("senderAddress", clean(content.senderDetailAddress()));
        var receiver = new TreeMap<String,Object>();
        receiver.put("receiverName", clean(content.recipientName())); receiver.put("receiverPhone", clean(content.recipientPhone()));
        receiver.put("receiverMobile", clean(content.recipientPhone())); receiver.put("receiverProvince", clean(content.recipientProvince()));
        receiver.put("receiverCity", clean(content.recipientCity())); receiver.put("receiverCounty", clean(content.recipientCounty()));
        receiver.put("receiverAddress", clean(content.recipientDetailAddress()));
        var goods = new TreeMap<String,Object>();
        goods.put("goodsName", clean(draft.cargoName()));
        if (draft.weight() != null) goods.put("goodsWeight", draft.weight().setScale(2,RoundingMode.UNNECESSARY));
        if (draft.volume() != null) goods.put("goodsVolume", draft.volume().setScale(2,RoundingMode.UNNECESSARY));
        goods.put("goodsQuqntity", draft.pieceAmount());
        goods.put("isCollectGoodsFee", 1); goods.put("remark", clean(draft.logisticsRemark()));
        body.put("electSenderRequest", sender); body.put("electReceiverRequest", receiver); body.put("goodsRequest", goods);
        return body;
    }
    public static void validateOrderContent(ShipmentContent content,String productCode) {
        require(content != null && content.orderDraft() != null, "请补充壹米货物资料");
        var draft = content.orderDraft();
        require(Set.of("1", "2").contains(clean(productCode)), "壹米运输产品必须为港到门或港到港");
        require(draft.goodsType() != null && Set.of(180,179,285).contains(draft.goodsType()), "请选择壹米送货方式");
        require((draft.goodsType() == 285) == "2".equals(productCode), "壹米运输产品与送货方式不一致");
        require(draft.payType() != null && Set.of(102,103,104).contains(draft.payType()), "请选择有效付款方式");
        require(draft.pieceAmount() != null && draft.pieceAmount() >= 1 && draft.pieceAmount() <= 1000, "壹米件数必须为1至1000");
        decimal(draft.weight(), "重量"); decimal(draft.volume(), "体积");
        text(draft.cargoName(), 50, true, "货物名称"); text(draft.logisticsRemark(), 100, false, "物流备注");
        text(content.senderName(), 50, true, "寄件人"); text(content.senderPhone(), 20, true, "寄件电话");
        text(content.senderProvince(), 50, true, "寄件省份"); text(content.senderCity(), 50, true, "寄件城市");
        text(content.senderCounty(), 50, true, "寄件区县"); text(content.senderDetailAddress(), 100, true, "寄件地址");
        text(content.recipientName(), 50, true, "收件人"); text(content.recipientPhone(), 20, true, "收件电话");
        text(content.recipientProvince(), 50, true, "收件省份"); text(content.recipientCity(), 50, true, "收件城市");
        text(content.recipientCounty(), 50, true, "收件区县"); text(content.recipientDetailAddress(), 50, true, "收件地址");
        require(draft.yimi() != null && draft.yimi().visitDelivery() != null && Set.of(0,1).contains(draft.yimi().visitDelivery()), "请明确选择壹米上门揽货或自送");
        int delivery = deliveryFlag(draft);
        require(draft.yimi().deliveryFlag() == null || draft.yimi().deliveryFlag() == delivery, "壹米送货选项与送货方式矛盾");
        String floor = clean(draft.yimi().floorNo());
        require(floor.isEmpty() || delivery == 3 && Set.of("-2","-1","2","3","4","5","6","7").contains(floor), "壹米楼层仅无电梯上楼时可选-2、-1或2至7楼");
    }
    private static int deliveryFlag(AneOrderDraft draft) {
        if (draft.goodsType() == 285) return 1;
        if (draft.goodsType() == 180) return 4;
        Integer explicit = draft.yimi().deliveryFlag();
        require(explicit != null && Set.of(2,3).contains(explicit), "送货上楼必须明确选择有电梯或无电梯");
        return explicit;
    }
    private static void decimal(BigDecimal value,String label) {
        require(value == null || value.signum() >= 0 && value.stripTrailingZeros().scale() <= 2, "壹米" + label + "必须非负且最多两位小数");
    }
    private static void text(String value,int max,boolean mandatory,String label) {
        String text = clean(value);
        require((!mandatory || !text.isEmpty()) && text.codePointCount(0,text.length()) <= max, label + "缺失或超过壹米长度限制");
    }
    private static String clean(String value) { return value == null ? "" : value.strip(); }
    private static void require(boolean valid,String message) { if (!valid) throw new IllegalArgumentException(message); }
}
