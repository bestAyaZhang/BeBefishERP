package com.bebefish.erp.shipping.logistics.yimi;

import com.bebefish.erp.shipping.domain.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class YimiPayloadsTest {
    YimiProperties account = new YimiProperties(true, null, "key", "secret", "seller", "subscription");
    ShipmentContent content(int goods, int pay, String weight, int pieces, YimiOrderOptions options, String address, String phone, String remark) {
        return content(goods,pay,weight,"0.10",pieces,options,address,phone,remark);
    }
    ShipmentContent content(int goods, int pay, String weight, String volume, int pieces, YimiOrderOptions options, String address, String phone, String remark) {
        return new ShipmentContent(LocalDate.of(2026,10,27), "1688", "shop", List.of(), "收件人", phone, "上海市", "上海市", "嘉定区", address,
                "备货说明", "内部备注", null, new AneOrderDraft("玻璃杯", "纸箱", weight == null ? null : new BigDecimal(weight), volume == null ? null : new BigDecimal(volume), pieces, 524, goods, pay, remark, options),
                "prepared", "orderer", "", "", "寄件人", "13800000000", "浙江省", "金华市", "义乌市", "仓库1号");
    }
    ShipmentContent valid(int goods, int pay, YimiOrderOptions options) { return content(goods,pay,"10.12",2,options,"工业路1号","13900000000","轻放"); }

    @Test void buildsOfficialElectronicFieldsAndPaymentMeaningWithoutValueAddedServices() {
        for (int[] payment : new int[][]{{102,1},{104,2},{103,3}}) {
            var body = YimiPayloads.orderParams(valid(180,payment[0],new YimiOrderOptions(0,null,null)), "BF1", account, "1");
            assertThat(body).containsEntry("sellerId","seller").containsEntry("subscriptionId","subscription").containsEntry("outSideNo","BF1")
                    .containsEntry("serviceType",1).containsEntry("settlementType",payment[1]).containsEntry("visitDelivery",0).containsEntry("deliveryFlag",4).containsEntry("operatorType","add");
            assertThat(body).doesNotContainKeys("electOrderFeeList","floorNo","orderNos","appKey","appSecret");
            assertThat((Map<String,Object>)body.get("goodsRequest")).containsEntry("goodsQuqntity",2).containsEntry("goodsWeight",new BigDecimal("10.12"))
                    .containsEntry("isCollectGoodsFee",1).containsEntry("remark","轻放").doesNotContainKey("goodsQuantity");
            assertThat((Map<String,Object>)body.get("electReceiverRequest")).containsEntry("receiverPhone","13900000000").containsEntry("receiverMobile","13900000000");
            assertThat((Map<String,Object>)body.get("electSenderRequest")).containsEntry("senderPhone","13800000000").containsEntry("senderMobile","13800000000");
        }
    }
    @Test void mapsExplicitPickupAndUpstairsFloorWithProductCompatibility() {
        assertThat(YimiPayloads.orderParams(valid(285,102,new YimiOrderOptions(1,null,null)),"BF1",account,"2")).containsEntry("serviceType",2).containsEntry("visitDelivery",1).containsEntry("deliveryFlag",1);
        assertThat(YimiPayloads.orderParams(valid(179,102,new YimiOrderOptions(0,3,"-2")),"BF1",account,"1")).containsEntry("deliveryFlag",3).containsEntry("floorNo","-2");
        assertThatThrownBy(() -> YimiPayloads.validateOrderContent(valid(285,102,new YimiOrderOptions(0,null,null)),"1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YimiPayloads.validateOrderContent(valid(180,102,new YimiOrderOptions(0,null,null)),"2")).isInstanceOf(IllegalArgumentException.class);
        for (YimiOrderOptions options : new YimiOrderOptions[]{null,new YimiOrderOptions(null,null,null),new YimiOrderOptions(2,null,null),new YimiOrderOptions(0,2,null),new YimiOrderOptions(0,null,"2")}) {
            assertThatThrownBy(() -> YimiPayloads.validateOrderContent(valid(180,102,options),"1")).isInstanceOf(IllegalArgumentException.class);
        }
        for (YimiOrderOptions options : new YimiOrderOptions[]{new YimiOrderOptions(0,null,null),new YimiOrderOptions(0,4,null),new YimiOrderOptions(0,2,"2"),new YimiOrderOptions(0,3,"8")}) {
            assertThatThrownBy(() -> YimiPayloads.validateOrderContent(valid(179,102,options),"1")).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void rejectsOverprecisionCarrierLimitsAndMissingMerchantBeforeAnyOrder() {
        for (String weight : new String[]{"1.001","-1"}) assertThatThrownBy(() -> YimiPayloads.validateOrderContent(content(180,102,weight,2,new YimiOrderOptions(0,null,null),"address","phone",""),"1")).isInstanceOf(IllegalArgumentException.class);
        for (int pieces : new int[]{0,1001}) assertThatThrownBy(() -> YimiPayloads.validateOrderContent(content(180,102,"1.00",pieces,new YimiOrderOptions(0,null,null),"address","phone",""),"1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YimiPayloads.validateOrderContent(content(180,102,"1.00",2,new YimiOrderOptions(0,null,null),"a".repeat(51),"phone",""),"1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YimiPayloads.validateOrderContent(content(180,102,"1.00",2,new YimiOrderOptions(0,null,null),"address","1".repeat(21),""),"1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YimiPayloads.validateOrderContent(content(180,102,"1.00",2,new YimiOrderOptions(0,null,null),"address","phone","x".repeat(101)),"1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> YimiPayloads.orderParams(valid(180,102,new YimiOrderOptions(0,null,null)),"BF1",new YimiProperties(true,null,"key","secret","",""),"1")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void databaseTrailingZerosDoNotTurnExactTwoDecimalWeightIntoOverprecision() {
        var body = YimiPayloads.orderParams(content(180,102,"10.120",1000,new YimiOrderOptions(0,null,null),"address","phone",""),"BF1",account,"1");
        assertThat((Map<String,Object>)body.get("goodsRequest")).containsEntry("goodsWeight",new BigDecimal("10.12"));
    }
    @Test void countBasedOrderOmitsBothOptionalMeasuresAndSerializesWithoutNulls() throws Exception {
        var body = YimiPayloads.orderParams(content(180,102,null,null,6,new YimiOrderOptions(0,null,null),"address","phone",""),"BF1",account,"1");
        assertThat((Map<String,Object>)body.get("goodsRequest")).containsEntry("goodsQuqntity",6).doesNotContainKeys("goodsWeight","goodsVolume");
        var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(body).path("goodsRequest");
        assertThat(json.has("goodsWeight")).isFalse();
        assertThat(json.has("goodsVolume")).isFalse();
        assertThat(json.path("goodsQuqntity").asInt()).isEqualTo(6);
    }
    @Test void independentlyOmitsOnlyTheMissingMeasure() {
        var missingWeight = YimiPayloads.orderParams(content(180,102,null,"0.500",2,new YimiOrderOptions(0,null,null),"address","phone",""),"BF1",account,"1");
        assertThat((Map<String,Object>)missingWeight.get("goodsRequest")).containsEntry("goodsVolume",new BigDecimal("0.50")).doesNotContainKey("goodsWeight");
        var missingVolume = YimiPayloads.orderParams(content(180,102,"0.5",null,2,new YimiOrderOptions(0,null,null),"address","phone",""),"BF1",account,"1");
        assertThat((Map<String,Object>)missingVolume.get("goodsRequest")).containsEntry("goodsWeight",new BigDecimal("0.50")).doesNotContainKey("goodsVolume");
    }
    @Test void suppliedZeroAndSubKilogramWeightRemainExactWithoutAneMinimum() {
        for (String weight : new String[]{"0","0.5"}) {
            var body = YimiPayloads.orderParams(content(180,102,weight,"0",2,new YimiOrderOptions(0,null,null),"address","phone",""),"BF1",account,"1");
            assertThat((Map<String,Object>)body.get("goodsRequest")).containsEntry("goodsWeight",new BigDecimal(weight).setScale(2)).containsEntry("goodsVolume",new BigDecimal("0.00"));
        }
    }
    @Test void suppliedInvalidVolumeStillFailsEvenWhenWeightIsOmitted() {
        for (String volume : new String[]{"-0.01","0.001"}) {
            assertThatThrownBy(() -> YimiPayloads.orderParams(content(180,102,null,volume,2,new YimiOrderOptions(0,null,null),"address","phone",""),"BF1",account,"1"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("体积");
        }
    }
}
