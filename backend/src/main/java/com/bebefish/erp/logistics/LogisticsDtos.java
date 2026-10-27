package com.bebefish.erp.logistics;

import com.bebefish.erp.logistics.pricing.FreightRules.Rules;
import com.bebefish.erp.shipping.domain.YimiOrderOptions;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Shared HTTP and internal freight contracts. Secret-bearing inputs never form response payloads. */
public final class LogisticsDtos {
    private LogisticsDtos() {}
    public record Company(long id,String code,String name,String provider,String status,String contactName,
            String contactPhone,int sortOrder,String remark,long version,Instant createdAt,Instant updatedAt) {}
    public record CompanyInput(String code,String name,String provider,String status,String contactName,
            String contactPhone,Integer sortOrder,String remark,Long version) {}
    public record StatusInput(String status,Long version) {}
    public record Sender(String name,String phone,String province,String city,String county,String address) {}
    public record YimiAccount(String sellerId,String subscriptionId) {}
    public record AccountInput(long version,String code,String customerCode,String dictId,String appKey,
            String customerPass,String orderUrl,Sender sender,String appSecret,YimiAccount yimi) {
        public AccountInput(long version,String code,String customerCode,String dictId,String appKey,
                String customerPass,String orderUrl,Sender sender) {
            this(version,code,customerCode,dictId,appKey,customerPass,orderUrl,sender,null,null);
        }
        @Override public String toString() { return "AccountInput[redacted]"; }
    }
    public record AccountView(long id,long companyId,String environment,long version,long accountVersionId,
            String code,String customerCode,String dictId,String orderUrl,Sender sender,boolean appKeyConfigured,
            boolean customerPassConfigured,boolean ready,String message,boolean appSecretConfigured,YimiAccount yimi) {
        public AccountView(long id,long companyId,String environment,long version,long accountVersionId,
                String code,String customerCode,String dictId,String orderUrl,Sender sender,boolean appKeyConfigured,
                boolean customerPassConfigured,boolean ready,String message) {
            this(id,companyId,environment,version,accountVersionId,code,customerCode,dictId,orderUrl,sender,
                appKeyConfigured,customerPassConfigured,ready,message,false,null);
        }
    }
    public record TemplateInput(String name,String productCode,String productName,String originProvince,
            String originCity,String originCounty,Rules rules,String status,String remark,long version) {}
    public record Template(long id,long companyId,Long accountId,String name,String productCode,String productName,
            String originProvince,String originCity,String originCounty,Rules rules,String status,String remark,
            long version,Instant updatedAt) {}
    public record QuoteInput(String senderProvince,String senderCity,String senderCounty,String recipientProvince,
            String recipientCity,String recipientCounty,BigDecimal weight,BigDecimal volume,BigDecimal pieceAmount,
            Integer goodsType,Integer payType,YimiOrderOptions yimi) {
        public QuoteInput(String senderProvince,String senderCity,String senderCounty,String recipientProvince,
                String recipientCity,String recipientCounty,BigDecimal weight,BigDecimal volume,BigDecimal pieceAmount,
                Integer goodsType,Integer payType) {
            this(senderProvince,senderCity,senderCounty,recipientProvince,recipientCity,recipientCounty,weight,volume,pieceAmount,goodsType,payType,null);
        }
    }
    public record Quote(String quoteId,long companyId,String companyName,String provider,Long accountId,
            Long accountVersionId,Long templateId,Long templateVersion,String productCode,String productName,
            BigDecimal amount,BigDecimal billedQuantity,String chargeMode,String matchedRegion,BigDecimal lowerExclusive,
            BigDecimal upperInclusive,BigDecimal firstQuantity,BigDecimal firstFee,BigDecimal additionalQuantity,
            BigDecimal additionalFee,Long renewalCount,boolean available,String reason,boolean apiReady,
            String apiMessage,boolean recommended,Instant expiresAt) {
        public Quote recommended(boolean value) {
            return new Quote(quoteId,companyId,companyName,provider,accountId,accountVersionId,templateId,templateVersion,
                productCode,productName,amount,billedQuantity,chargeMode,matchedRegion,lowerExclusive,upperInclusive,
                firstQuantity,firstFee,additionalQuantity,additionalFee,renewalCount,available,reason,apiReady,apiMessage,value,expiresAt);
        }
    }
    public record QuoteBatch(List<Quote> quotes,boolean legacyOrderingAllowed,Instant generatedAt) {
        public QuoteBatch { quotes=List.copyOf(quotes); }
    }
}
