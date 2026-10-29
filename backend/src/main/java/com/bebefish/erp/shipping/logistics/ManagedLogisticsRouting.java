package com.bebefish.erp.shipping.logistics;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.logistics.*;
import com.bebefish.erp.logistics.CarrierAccountResolver.ResolvedAccount;
import com.bebefish.erp.logistics.LogisticsDtos.*;
import com.bebefish.erp.shipping.application.ShipmentFreightCoordinator;
import com.bebefish.erp.shipping.domain.ShipmentRepository;
import com.bebefish.erp.shipping.logistics.yimi.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable reservations bind one account snapshot before the carrier request leaves the transaction. */
@Service
public class ManagedLogisticsRouting {
    private final FreightQuoteService quotes;
    private final LogisticsCatalogService catalog;
    private final CarrierAccountResolver accounts;
    private final ShipmentFreightCoordinator freight;
    private final ShipmentRepository shipments;
    private final AneOrderStore store;
    private final NamedParameterJdbcTemplate jdbc;
    private final AneProperties legacy;
    private final ObjectMapper mapper;
    public ManagedLogisticsRouting(FreightQuoteService quotes,LogisticsCatalogService catalog,CarrierAccountResolver accounts,
            ShipmentFreightCoordinator freight,ShipmentRepository shipments,AneOrderStore store,NamedParameterJdbcTemplate jdbc,AneProperties legacy,ObjectMapper mapper) {
        this.quotes=quotes;this.catalog=catalog;this.accounts=accounts;this.freight=freight;this.shipments=shipments;this.store=store;this.jdbc=jdbc;this.legacy=legacy;this.mapper=mapper;
    }
    public record Operation(Long companyId,Long accountId,Long accountVersionId,String companyName,AneProperties config,
            YimiProperties yimi,String productCode,Map<String,Object> business) {
        public Operation(Long companyId,Long accountId,Long accountVersionId,String companyName,AneProperties config) {
            this(companyId,accountId,accountVersionId,companyName,config,null,null,null);
        }
        public String provider(){return yimi==null?"ANE":"YIMI";}
        public boolean ready(){return yimi==null?config.ready():yimi.ready();}
        public boolean testEnvironment(){return yimi==null?config.testEnvironment():yimi.testEnvironment();}
    }
    public record TraceContext(String waybillNo,YimiProperties account) {}
    public record Placement(AneOrderStore.Claim claim,Operation operation) {}
    public record Cancellation(AneOrderStore.CancellationClaim claim,Operation operation) {}
    public record Binding(Long companyId,Long accountId,Long accountVersionId,boolean linked,boolean requiresConfirmation) {}

    public boolean managedTemplatesEnabled(){return quotes.managedTemplatesEnabled();}
    public AneOrderService.Availability availability(Long companyId) {
        boolean managed=managedTemplatesEnabled();
        if(companyId==null) {
            if(managed) for(var company:catalog.enabledCompanies()) {
                var account=catalog.account(company.id());
                if(account!=null&&completeSender(account.sender()))
                    return configuredAvailability(company,account,true,"已带入"+company.name()+"的寄件资料，请核对后比价选择物流");
            }
            return new AneOrderService.Availability(legacy.ready(),legacy.testEnvironment(),
                managed?"按合作模板比价并选择物流，最终费用以承运商账单为准":legacy.ready()?"安能物流下单服务已配置":"请配置合作物流账号和寄件资料",sender(legacy),managed);
        }
        var company=catalog.company(companyId);
        var account=catalog.account(companyId);
        if(account==null)return new AneOrderService.Availability(false,!catalog.runtimeEnvironment().equals("production"),"该公司尚未配置接口账号",null,managed);
        return configuredAvailability(company,account,managed,java.util.Set.of("ANE","YIMI").contains(company.provider())?"已加载该合作物流的默认寄件资料":"需线下下单");
    }
    private AneOrderService.Availability configuredAvailability(Company company,AccountView account,boolean managed,String message) {
        var value=account.sender();
        var info=value==null?null:new AneOrderService.SenderInfo(value.name(),value.phone(),value.province(),value.city(),value.county(),value.address());
        return new AneOrderService.Availability(java.util.Set.of("ANE","YIMI").contains(company.provider())&&company.status().equals("enabled")&&account.ready(),
                account.environment().equals("test"),message,info,managed);
    }
    private static boolean completeSender(Sender sender) {
        return sender!=null&&java.util.stream.Stream.of(sender.name(),sender.phone(),sender.province(),sender.city(),sender.county(),sender.address()).allMatch(value->value!=null&&!value.isBlank());
    }

    @Transactional
    public Placement claimPlacement(long id,long version,String operator) {
        lock(id);
        var previous=store.find(id);
        if(previous.isPresent()&&!previous.get().state().equals("rejected"))
            return new Placement(new AneOrderStore.Claim(shipments.findById(id).orElseThrow(),previous.get().orderNo(),previous.get()),null);
        var shipment=shipments.findById(id).orElseThrow();
        var selected=freight.stored(id);
        Operation operation;
        if(selected.quote()!=null) {
            Quote quote=quotes.getForOrder(selected.quote().quoteId(),FreightQuoteService.input(shipment.content().form()));
            if(!java.util.Set.of("ANE","YIMI").contains(quote.provider())||!quote.apiReady()||quote.accountVersionId()==null)
                throw business("LOGISTICS_PROVIDER_NOT_SUPPORTED","所选物流暂未接入接口，请线下下单");
            if(quote.provider().equals("ANE")&&!Objects.equals(quote.productCode(),String.valueOf(shipment.content().orderDraft().productTypeId())))
                throw business("LOGISTICS_PRODUCT_CHANGED","运输产品与所选报价不一致，请重新选择");
            var account=accounts.resolve(quote.accountVersionId());
            if(!account.provider().equals(quote.provider())||account.companyId()!=quote.companyId())
                throw business("LOGISTICS_ACCOUNT_IDENTITY_MISMATCH","报价与物流账号不一致，请重新选择");
            operation=operation(account,quote.companyName(),quote.productCode());
        } else {
            if(quotes.managedTemplatesEnabled()||selected.preferredCompanyId()!=null)
                throw business("LOGISTICS_QUOTE_REQUIRED","请先测算并选择有效合作报价后下单");
            operation=new Operation(null,null,null,"安能物流",copy(legacy));
        }
        if(!operation.ready())throw business("LOGISTICS_NOT_CONFIGURED","物流接口账号或寄件资料未配置完整");
        if(operation.yimi()!=null) {
            if(!operation.yimi().orderReady())throw business("LOGISTICS_NOT_CONFIGURED","请补全指定揽收业务员工号和姓名");
            try{YimiPayloads.validateOrderContent(shipment.content(),operation.productCode());}
            catch(IllegalArgumentException error){throw new BusinessException("VALIDATION_FAILED",HttpStatus.BAD_REQUEST,error.getMessage());}
        } else AneOrderService.validateOrderContent(shipment.content());
        var claim=operation.accountId()==null?store.claim(id,version,operator,operation.config(),false)
                :store.claim(id,version,operator,operation.testEnvironment(),operation.provider());
        if(claim.existing()==null) {
            Map<String,Object> business=operation.yimi()==null?null:YimiPayloads.orderParams(claim.shipment().content(),claim.orderNo(),operation.yimi(),operation.productCode());
            operation=new Operation(operation.companyId(),operation.accountId(),operation.accountVersionId(),operation.companyName(),operation.config(),operation.yimi(),operation.productCode(),business);
            var params=new MapSqlParameterSource("id",id).addValue("company",operation.companyId()).addValue("account",operation.accountId())
                    .addValue("config",operation.accountVersionId()).addValue("hash",operation.accountId()==null?identity(operation.config()):null).addValue("business",json(business));
            jdbc.update("update shipment_logistics_order set carrier_company_id=:company,carrier_account_id=:account,carrier_account_version_id=:config,legacy_account_identity_hash=:hash,carrier_business_snapshot=cast(:business as json) where shipment_id=:id",params);
        }
        return new Placement(claim,operation);
    }

    @Transactional
    public Cancellation claimCancellation(long id,long version,String operator) {
        lock(id);
        var order=store.find(id).orElseThrow(()->business("LOGISTICS_ORDER_NOT_FOUND","尚未创建物流订单"));
        if(java.util.Set.of("cancelled","cancel_processing","cancel_unknown").contains(order.state()))
            return new Cancellation(new AneOrderStore.CancellationClaim(order,false),null);
        Operation operation=cancellationOperation(id);
        if(!operation.ready()) throw business("LOGISTICS_NOT_CONFIGURED","原物流账号凭据不可用，请由管理员核实并更新同一账号凭据");
        if(operation.yimi()==null)AneOrderService.validateOrderContent(store.cancellationSnapshot(id));
        else if(!order.trackingNo().matches("[0-9]{12}"))throw business("LOGISTICS_SNAPSHOT_INVALID","原壹米运单号不完整，请联系网点核实");
        if(operation.yimi()!=null) {
            var frozen=frozenBusiness(id);
            if(frozen!=null&&(frozen.containsKey("salesmanCode")||frozen.containsKey("salesmanName"))) {
                if(!(frozen.get("salesmanCode") instanceof String code)||code.isBlank()
                        ||!(frozen.get("salesmanName") instanceof String name)||name.isBlank()
                        ||!"add".equals(frozen.get("operatorType"))||!order.orderNo().equals(frozen.get("outSideNo"))
                        ||!operation.yimi().sellerId().equals(frozen.get("sellerId"))||!operation.yimi().subscriptionId().equals(frozen.get("subscriptionId")))
                    throw business("LOGISTICS_SNAPSHOT_INVALID","原电子面单业务资料不完整或账号不一致，请核实后处理");
                operation=new Operation(operation.companyId(),operation.accountId(),operation.accountVersionId(),operation.companyName(),null,operation.yimi(),null,frozen);
            }
        }
        var claim=store.claimCancellation(id,version,operator,operation.testEnvironment());
        if(claim.claimed())jdbc.update("update shipment_logistics_order set cancellation_account_version_id=:version where shipment_id=:id",
                new MapSqlParameterSource("id",id).addValue("version",operation.accountVersionId()));
        return new Cancellation(claim,operation);
    }

    private Map<String,Object> frozenBusiness(long id) {
        String value=jdbc.queryForObject("select carrier_business_snapshot from shipment_logistics_order where shipment_id=:id",new MapSqlParameterSource("id",id),String.class);
        if(value==null)return null;
        try{return mapper.readValue(value,new TypeReference<Map<String,Object>>(){});}
        catch(JsonProcessingException error){throw business("LOGISTICS_SNAPSHOT_INVALID","原电子面单业务资料无法读取，请联系网点核实");}
    }

    public Binding binding(long id) {
        var rows=jdbc.query("select carrier_company_id,carrier_account_id,carrier_account_version_id,legacy_account_identity_hash from shipment_logistics_order where shipment_id=:id",
            new MapSqlParameterSource("id",id),(rs,n)->new Object[]{rs.getObject(1,Long.class),rs.getObject(2,Long.class),rs.getObject(3,Long.class),rs.getString(4)});
        if(rows.isEmpty())return new Binding(null,null,null,false,false);
        var values=rows.getFirst();
        boolean known=values[1]!=null||values[3]!=null&&values[3].equals(identity(legacy));
        return new Binding((Long)values[0],(Long)values[1],(Long)values[2],known,!known);
    }

    @Transactional
    public Binding confirmAccount(long id,long accountId,long version,boolean confirmed,ErpPrincipal actor) {
        if(!confirmed||!actor.roles().contains("SUPER_ADMIN")||!actor.permissions().contains("logistics:configure"))
            throw new BusinessException("LOGISTICS_ACCOUNT_CONFIRM_FORBIDDEN",HttpStatus.FORBIDDEN,"只有超级管理员核实后可确认原物流账号");
        lock(id);
        var shipment=shipments.findById(id).orElseThrow();
        if(shipment.version()!=version)throw business("SHIPMENT_VERSION_CONFLICT","发货单已更新，请刷新后核对原账号");
        var order=store.find(id).orElseThrow(()->business("LOGISTICS_ORDER_NOT_FOUND","尚未创建物流订单"));
        var existing=binding(id);
        if(existing.accountId()!=null)throw business("LOGISTICS_ACCOUNT_ALREADY_CONFIRMED","本单已确认物流账号，不能重新关联");
        var account=latestAccount(accountId);
        if(!account.provider().equals("ANE")||order.testEnvironment()!=account.environment().equals("test"))
            throw business("LOGISTICS_ENVIRONMENT_CHANGED","所选原账号的物流公司或测试/正式环境与订单不一致");
        var frozenIdentity=jdbc.queryForObject("select legacy_account_identity_hash from shipment_logistics_order where shipment_id=:id",
                new MapSqlParameterSource("id",id),String.class);
        if(frozenIdentity!=null&&!frozenIdentity.equals(identity(properties(account))))
            throw business("LOGISTICS_ACCOUNT_IDENTITY_MISMATCH","所选账号与本单已记录的原下单账号不同，不能关联");
        var parameters=new MapSqlParameterSource("id",id).addValue("company",account.companyId()).addValue("account",account.accountId())
                .addValue("config",account.accountVersionId()).addValue("operator",actor.operatorIdentifier());
        jdbc.update("update shipment_logistics_order set carrier_company_id=:company,carrier_account_id=:account,carrier_account_version_id=:config,account_confirmed_by=:operator,account_confirmed_at=now(3) where shipment_id=:id",parameters);
        jdbc.update("update shipment set version_no=version_no+1,updated_by=:operator,updated_at=now(3) where id=:id",parameters);
        return binding(id);
    }

    private Operation cancellationOperation(long id) {
        Binding binding=binding(id);
        if(binding.accountId()!=null) {
            var account=latestAccount(binding.accountId());
            var order=store.find(id).orElseThrow();
            if(!Objects.equals(binding.companyId(),account.companyId())||!order.provider().equals(account.provider())
                    ||order.testEnvironment()!=account.environment().equals("test"))
                throw business("LOGISTICS_ACCOUNT_IDENTITY_MISMATCH","原订单与关联物流账号不一致，请管理员核实");
            return operation(account,"",null);
        }
        if(!binding.requiresConfirmation())return new Operation(null,null,null,"安能物流",copy(legacy));
        throw business("LOGISTICS_ACCOUNT_CONFIRMATION_REQUIRED","此历史单尚未核实原物流账号，请超级管理员确认后再取消");
    }
    @Transactional
    public TraceContext traceContext(long id) {
        lock(id);
        var order=store.find(id).orElse(null);
        if(order==null||!"YIMI".equals(order.provider())||!order.trackingNo().matches("[0-9]{12}"))return null;
        var operation=cancellationOperation(id);
        if(operation.yimi()==null||!operation.ready())throw business("LOGISTICS_NOT_CONFIGURED","原壹米账号凭据不可用，请更新原账号配置");
        return new TraceContext(order.trackingNo(),operation.yimi());
    }
    private static Operation operation(ResolvedAccount account,String companyName,String productCode) {
        if(account.provider().equals("YIMI")) {
            var in=account.config();var ids=in.yimi();
            var config=new YimiProperties(account.environment().equals("test"),in.orderUrl()==null||in.orderUrl().isBlank()?null:URI.create(in.orderUrl()),
                in.appKey(),in.appSecret(),ids==null?null:ids.sellerId(),ids==null?null:ids.subscriptionId(),ids!=null&&ids.designatedPickup(),
                ids==null?null:ids.salesmanCode(),ids==null?null:ids.salesmanName());
            return new Operation(account.companyId(),account.accountId(),account.accountVersionId(),companyName,null,config,productCode,null);
        }
        if(!account.provider().equals("ANE"))throw business("LOGISTICS_PROVIDER_NOT_SUPPORTED","该物流公司需线下下单");
        return new Operation(account.companyId(),account.accountId(),account.accountVersionId(),companyName,properties(account));
    }
    private String json(Map<String,Object> value) {
        if(value==null)return null;
        try{return mapper.writeValueAsString(value);}catch(JsonProcessingException error){throw new IllegalStateException("无法保存物流业务资料",error);}
    }
    private ResolvedAccount latestAccount(long accountId) {
        var ids=jdbc.queryForList("select id from logistics_account_version where account_id=:id order by version_no desc limit 1 for update",new MapSqlParameterSource("id",accountId),Long.class);
        if(ids.isEmpty())throw business("LOGISTICS_ACCOUNT_NOT_FOUND","原物流账号配置不存在，请联系管理员");
        return accounts.resolve(ids.getFirst());
    }
    private void lock(long id){if(jdbc.queryForList("select id from shipment where id=:id for update",new MapSqlParameterSource("id",id),Long.class).isEmpty())throw new BusinessException("SHIPMENT_NOT_FOUND",HttpStatus.NOT_FOUND,"发货单不存在");}
    private static BusinessException business(String code,String message){return new BusinessException(code,HttpStatus.CONFLICT,message);}
    private static AneOrderService.SenderInfo sender(AneProperties c){return new AneOrderService.SenderInfo(c.getSenderName(),c.getSenderPhone(),c.getSenderProvince(),c.getSenderCity(),c.getSenderCounty(),c.getSenderAddress());}
    private static AneProperties properties(ResolvedAccount account) {
        var in=account.config(); var out=new AneProperties(); out.setEnabled(true);out.setCode(in.code());out.setCustomerCode(in.customerCode());out.setCustomerPass(in.customerPass());out.setDictId(in.dictId());out.setAppKey(in.appKey());
        if(in.orderUrl()!=null&&!in.orderUrl().isBlank())out.setOrderUrl(URI.create(in.orderUrl()));
        if(in.sender()!=null){out.setSenderName(in.sender().name());out.setSenderPhone(in.sender().phone());out.setSenderProvince(in.sender().province());out.setSenderCity(in.sender().city());out.setSenderCounty(in.sender().county());out.setSenderAddress(in.sender().address());}
        return out;
    }
    private static AneProperties copy(AneProperties in) {
        var out=new AneProperties();out.setEnabled(in.isEnabled());out.setOrderUrl(in.getOrderUrl());out.setCode(in.getCode());out.setAppKey(in.getAppKey());out.setCustomerCode(in.getCustomerCode());out.setCustomerPass(in.getCustomerPass());out.setDictId(in.getDictId());
        out.setSenderName(in.getSenderName());out.setSenderPhone(in.getSenderPhone());out.setSenderProvince(in.getSenderProvince());out.setSenderCity(in.getSenderCity());out.setSenderCounty(in.getSenderCounty());out.setSenderAddress(in.getSenderAddress());return out;
    }
    private static String identity(AneProperties in) {
        String value=String.join("\u0000",String.valueOf(in.testEnvironment()),String.valueOf(in.getCode()),String.valueOf(in.getCustomerCode()),String.valueOf(in.getDictId()));
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
}
