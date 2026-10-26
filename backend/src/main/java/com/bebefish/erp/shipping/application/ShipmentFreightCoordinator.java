package com.bebefish.erp.shipping.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.logistics.FreightQuoteService;
import com.bebefish.erp.logistics.LogisticsCatalogService;
import com.bebefish.erp.logistics.LogisticsDtos.Quote;
import com.bebefish.erp.logistics.LogisticsDtos.QuoteInput;
import com.bebefish.erp.shipping.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates price snapshots with the existing shipment transaction and optimistic version. */
@Service
public class ShipmentFreightCoordinator {
    private final ShipmentService shipments;
    private final ShipmentRepository repository;
    private final FreightQuoteService quotes;
    private final LogisticsCatalogService catalog;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ShipmentFreightCoordinator(ShipmentService shipments, ShipmentRepository repository, FreightQuoteService quotes,
            LogisticsCatalogService catalog, NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.shipments=shipments; this.repository=repository; this.quotes=quotes; this.catalog=catalog; this.jdbc=jdbc; this.mapper=mapper;
    }
    public record Stored(Quote quote,QuoteInput input,Long preferredCompanyId) {}
    public Stored stored(long id) {
        var found=jdbc.query("select quote_snapshot,conditions_snapshot,preferred_company_id from shipment_freight_selection where shipment_id=:id",
                new MapSqlParameterSource("id",id),(rs,row)->new Stored(read(rs.getString(1),Quote.class),read(rs.getString(2),QuoteInput.class),rs.getObject(3,Long.class)));
        return found.isEmpty()?new Stored(null,null,null):found.getFirst();
    }
    public Quote selection(long id) { shipments.get(id); return stored(id).quote(); }

    @Transactional
    public Shipment create(ShipmentFormInput form,List<Long> preparers,ShipmentFreightChoice choice,ErpPrincipal actor) {
        Stored selected=resolve(form,choice,new Stored(null,null,null),actor,false);
        var amount=selected.quote()!=null?selected.quote().amount():quotes.managedTemplatesEnabled()?null:form.estimatedFreight();
        Shipment saved=shipments.create(form.withEstimatedFreight(amount),preparers,actor.operatorIdentifier(),display(actor),actor.employeeId()>0?actor.employeeId():null);
        write(saved.id(),selected,actor.operatorIdentifier());
        return shipments.get(saved.id());
    }

    @Transactional
    public Shipment update(long id,ShipmentFormInput form,String status,long version,List<Long> preparers,ShipmentFreightChoice choice,ErpPrincipal actor) {
        var previous=repository.findByIdForUpdate(id).orElseThrow(()->new BusinessException("SHIPMENT_NOT_FOUND",HttpStatus.NOT_FOUND,"发货单不存在"));
        boolean ordered=repository.hasNonRejectedLogisticsOrder(id);
        var old=stored(id);
        boolean conditionsSame=Objects.equals(FreightQuoteService.input(previous.content().form()),FreightQuoteService.input(form))
                && Objects.equals(previous.content().orderDraft().productTypeId(),form.orderDraft().productTypeId());
        if(ordered && (choice!=null && (choice.clear() || !Objects.equals(choice.quoteId(),old.quote()==null?null:old.quote().quoteId())
                || choice.preferredCompanyId()!=null&&!Objects.equals(choice.preferredCompanyId(),old.preferredCompanyId()))))
            throw locked();
        var selected=resolve(form,choice,conditionsSame?old:new Stored(null,null,old.preferredCompanyId()),actor,ordered);
        var amount=selected.quote()!=null?selected.quote().amount()
                :quotes.managedTemplatesEnabled()?(conditionsSame&&old.quote()==null?previous.content().estimatedFreight():null)
                :old.quote()!=null?null:form.estimatedFreight();
        if(choice!=null&&choice.clear()) amount=null;
        Shipment saved=shipments.update(id,form.withEstimatedFreight(amount),status,version,actor.operatorIdentifier(),preparers,actor.roles().contains("SUPER_ADMIN"));
        write(saved.id(),selected,actor.operatorIdentifier());
        return shipments.get(saved.id());
    }

    private Stored resolve(ShipmentFormInput form,ShipmentFreightChoice choice,Stored previous,ErpPrincipal actor,boolean ordered) {
        if(choice==null) return previous;
        if(choice.clear() || choice.quoteId()==null || choice.quoteId().isBlank()) {
            if(ordered && previous.quote()!=null) throw locked();
            if(choice.preferredCompanyId()!=null) catalog.company(choice.preferredCompanyId());
            return new Stored(null,null,choice.preferredCompanyId());
        }
        if(previous.quote()!=null && Objects.equals(choice.quoteId(),previous.quote().quoteId())) return previous;
        if(ordered) throw locked();
        Quote quote=quotes.getValid(choice.quoteId(),actor.employeeId(),FreightQuoteService.input(form));
        if(quote.provider().equals("ANE")&&!Objects.equals(quote.productCode(),String.valueOf(form.orderDraft().productTypeId())))
            throw new BusinessException("LOGISTICS_PRODUCT_CHANGED",HttpStatus.CONFLICT,"运输产品与所选报价不一致，请重新选择");
        if(choice.preferredCompanyId()!=null && choice.preferredCompanyId()!=quote.companyId())
            throw new BusinessException("LOGISTICS_QUOTE_MISMATCH",HttpStatus.CONFLICT,"所选公司与运费报价不一致，请重新选择");
        return new Stored(quote,FreightQuoteService.input(form),quote.companyId());
    }

    private void write(long id,Stored value,String operator) {
        var parameters=new MapSqlParameterSource("id",id).addValue("quoteId",value.quote()==null?null:value.quote().quoteId())
                .addValue("companyId",value.preferredCompanyId()).addValue("snapshot",json(value.quote())).addValue("conditions",json(value.input())).addValue("operator",operator);
        jdbc.update("""
            insert into shipment_freight_selection(shipment_id,quote_id,preferred_company_id,quote_snapshot,conditions_snapshot,updated_by,updated_at)
            values(:id,:quoteId,:companyId,cast(:snapshot as json),cast(:conditions as json),:operator,now(3))
            on duplicate key update quote_id=:quoteId,preferred_company_id=:companyId,quote_snapshot=cast(:snapshot as json),
                conditions_snapshot=cast(:conditions as json),updated_by=:operator,updated_at=now(3)
            """,parameters);
        if(value.quote()!=null) {
            parameters.addValue("name",value.quote().companyName());
            jdbc.update("update shipment set logistics_company=:name where id=:id",parameters);
        } else if(value.preferredCompanyId()!=null) {
            parameters.addValue("name",catalog.company(value.preferredCompanyId()).name());
            jdbc.update("update shipment set logistics_company=:name where id=:id and not exists(select 1 from shipment_logistics_order where shipment_id=:id and state<>'rejected')",parameters);
        }
    }
    private String json(Object value) { if(value==null)return null; try{return mapper.writeValueAsString(value);}catch(JsonProcessingException error){throw new IllegalStateException("运费快照无法保存",error);} }
    private <T>T read(String value,Class<T> type) { if(value==null)return null; try{return mapper.readValue(value,type);}catch(JsonProcessingException error){throw new BusinessException("LOGISTICS_QUOTE_INVALID",HttpStatus.CONFLICT,"运费快照无法读取，请重新测算");} }
    private static BusinessException locked(){return new BusinessException("SHIPMENT_ALREADY_ORDERED",HttpStatus.CONFLICT,"已提交物流，不能修改承运公司或报价选择");}
    private static String display(ErpPrincipal actor){return actor.displayName()!=null&&!actor.displayName().isBlank()?actor.displayName():actor.mobile()!=null?actor.mobile():"员工#"+actor.employeeId();}
}
