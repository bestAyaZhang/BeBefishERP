package com.bebefish.erp.shipping.infrastructure;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.logistics.*;
import com.bebefish.erp.logistics.LogisticsDtos.*;
import com.bebefish.erp.logistics.pricing.FreightRules.*;
import com.bebefish.erp.platform.api.PlatformDtos;
import com.bebefish.erp.platform.application.PlatformCatalogService;
import com.bebefish.erp.shipping.application.ShipmentService;
import com.bebefish.erp.shipping.domain.*;
import com.bebefish.erp.shipping.logistics.*;
import com.bebefish.erp.shipping.logistics.yimi.YimiOrderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="ERP_LOGISTICS_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ActiveProfiles("test") @Transactional
class ShipmentFreightIntegrationTest {
    @Autowired ApplicationContext context;
    @Autowired LogisticsCatalogService catalog;
    @Autowired FreightQuoteService quotes;
    @Autowired PlatformCatalogService platforms;
    @Autowired ShipmentService shipments;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AneOrderService ane;
    @Autowired ManagedLogisticsRouting routing;
    @MockBean AneOrderClient client;
    @MockBean YimiOrderClient yimi;

    @Test void offlineSelectionNeverCallsAnyCarrier() {
        var f=fixture();var quote=quote(f);var saved=create(f,new ShipmentFreightChoice(quote.quoteId(),f.company.id(),false),f.actor);
        assertThatThrownBy(()->ane.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test"))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("LOGISTICS_PROVIDER_NOT_SUPPORTED"));
        verifyNoInteractions(client,yimi);
    }
    @Test void managedNewFormDefaultsToConfiguredDatabaseSenderBeforeChoosingAQuote() {
        var f=fixture("ANE");
        // The test transaction rolls this back; existing QA accounts must not decide the default sender.
        jdbc.update("update logistics_company set status='disabled' where id<>? and status='enabled'",f.company.id());
        assertThat(ane.availability().sender().name()).isEqualTo("验收寄件人");
        assertThat(ane.availability().sender().province()).isEqualTo("浙江省");
        verifyNoInteractions(client,yimi);
    }
    @Test void newAneSelectionMustMatchTheShipmentProductAtSave() throws Exception {
        var f=fixture("ANE");var quote=quote(f);
        ObjectNode changed=mapper.valueToTree(f.form);
        ((ObjectNode)changed.get("orderDraft")).put("productTypeId",24);
        var mismatched=new Fixture(f.company,mapper.treeToValue(changed,ShipmentFormInput.class),f.preparerId,f.actor);
        assertThatThrownBy(()->create(mismatched,new ShipmentFreightChoice(quote.quoteId(),f.company.id(),false),f.actor))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("LOGISTICS_PRODUCT_CHANGED"));
        verifyNoInteractions(client,yimi);
    }
    @Test void productChangeClearsSavedAneQuoteWithoutAReplacement() throws Exception {
        var f=fixture("ANE");var quote=quote(f);var saved=create(f,new ShipmentFreightChoice(quote.quoteId(),f.company.id(),false),f.actor);
        ObjectNode changed=mapper.valueToTree(saved.content().form());
        ((ObjectNode)changed.get("orderDraft")).put("productTypeId",24);
        var edited=update(saved,mapper.treeToValue(changed,ShipmentFormInput.class),null,f.actor);
        assertThat(selection(edited.id())).isNull();
        assertThat(edited.content().estimatedFreight()).isNull();
    }
    @Test void selectedAccountIsUsedAndCancellationStaysWithItAfterCurrentAccountReplacement() {
        var f=fixture("ANE");var quote=quote(f);var saved=create(f,new ShipmentFreightChoice(quote.quoteId(),f.company.id(),false),f.actor);
        when(client.createOrder(anyMap(),any(AneProperties.class))).thenReturn(new AneOrderResult("succeeded","620000000001","","成功"));
        assertThat(ane.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test").state()).isEqualTo("succeeded");
        var used=org.mockito.ArgumentCaptor.forClass(AneProperties.class);
        verify(client).createOrder(anyMap(),used.capture());
        assertThat(used.getValue().getCustomerCode()).isEqualTo("CUST-OLD");
        assertThat(shipments.get(saved.id()).content().logisticsCompany()).isEqualTo(f.company.name());
        long originalAccount=catalog.account(f.company.id()).id();
        long revision=catalog.account(f.company.id()).version();
        catalog.saveAccount(f.company.id(),new AccountInput(revision,"PORTAL","CUST-NEW","DICT","NEW-KEY","NEW-PASS",catalog.expectedOrderUrl(),sender()));
        var company=catalog.company(f.company.id());catalog.companyStatus(company.id(),new StatusInput("disabled",company.version()));
        when(client.cancelOrder(anyMap(),any(AneProperties.class))).thenReturn(new AneCancellationResult("cancelled","取消成功"));
        var latest=shipments.get(saved.id());
        assertThat(ane.cancel(saved.id(),new CancelAneOrderRequest(latest.version()),"test").state()).isEqualTo("cancelled");
        verify(client).cancelOrder(anyMap(),used.capture());
        assertThat(used.getValue().getCustomerCode()).isEqualTo("CUST-OLD");
        assertThat(routing.binding(saved.id()).accountId()).isEqualTo(originalAccount);
    }
    @Test void templateChangedAfterSelectionStopsOrderingBeforeAnyCarrierCall() {
        var f=fixture("ANE");var quote=quote(f);var saved=create(f,new ShipmentFreightChoice(quote.quoteId(),f.company.id(),false),f.actor);
        var template=catalog.templates(f.company.id()).getFirst();
        catalog.templateStatus(template.id(),new StatusInput("disabled",template.version()));
        assertThatThrownBy(()->ane.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(client,yimi);
        assertThat(jdbc.queryForObject("select count(*) from shipment_logistics_order where shipment_id=?",Long.class,saved.id())).isZero();
    }
    @Test void disablingTheLastPublishedTemplateCannotReopenLegacyOrdering() {
        var f=fixture("ANE");var saved=create(f,null,f.actor);
        var template=catalog.templates(f.company.id()).getFirst();
        catalog.templateStatus(template.id(),new StatusInput("disabled",template.version()));
        assertThat(quotes.managedTemplatesEnabled()).isTrue();
        assertThatThrownBy(()->ane.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test"))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("LOGISTICS_QUOTE_REQUIRED"));
        verifyNoInteractions(client,yimi);
    }
    @Test void frozenLegacyIdentityCannotBeConfirmedAsADifferentAccount() throws Exception {
        var f=fixture("ANE");var saved=shipments.create(f.form,List.of(f.preparerId),"test","原录入员");
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest(String.join("\u0000","true","PORTAL","CUST-OLD","DICT").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("insert into shipment_logistics_order(shipment_id,order_no,state,test_environment,tracking_no,child_tracking_nos,message,request_snapshot,operator_id,created_at,updated_at,legacy_account_identity_hash) values(?,?,'succeeded',true,'620000000001','','test',?,'test',now(3),now(3),?)",saved.id(),"FROZEN-"+saved.id(),mapper.writeValueAsString(saved.content()),hash);
        var old=catalog.account(f.company.id());
        catalog.saveAccount(f.company.id(),new AccountInput(old.version(),"PORTAL","CUST-NEW","DICT","NEW-KEY","NEW-PASS",catalog.expectedOrderUrl(),sender()));
        assertThatThrownBy(()->routing.confirmAccount(saved.id(),catalog.account(f.company.id()).id(),saved.version(),true,f.actor))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("LOGISTICS_ACCOUNT_IDENTITY_MISMATCH"));
        assertThat(routing.binding(saved.id()).accountId()).isNull();
        verifyNoInteractions(client,yimi);
    }
    @Test void unknownHistoricalAccountRequiresAdministratorConfirmationBeforeCancellation() throws Exception {
        var f=fixture("ANE");var saved=shipments.create(f.form,List.of(f.preparerId),"test","原录入员");
        jdbc.update("insert into shipment_logistics_order(shipment_id,order_no,state,test_environment,tracking_no,child_tracking_nos,message,request_snapshot,operator_id,created_at,updated_at) values(?,?,'succeeded',true,'620000000001','','test',?,'test',now(3),now(3))",saved.id(),"LEGACY-"+saved.id(),mapper.writeValueAsString(saved.content()));
        assertThatThrownBy(()->ane.cancel(saved.id(),new CancelAneOrderRequest(saved.version()),"test"))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("LOGISTICS_ACCOUNT_CONFIRMATION_REQUIRED"));
        verifyNoInteractions(client,yimi);
        assertThatThrownBy(()->routing.confirmAccount(saved.id(),catalog.account(f.company.id()).id(),saved.version(),false,f.actor)).isInstanceOf(BusinessException.class);
        routing.confirmAccount(saved.id(),catalog.account(f.company.id()).id(),saved.version(),true,f.actor);
        when(client.cancelOrder(anyMap(),any(AneProperties.class))).thenReturn(new AneCancellationResult("cancelled","取消成功"));
        assertThat(ane.cancel(saved.id(),new CancelAneOrderRequest(shipments.get(saved.id()).version()),"test").state()).isEqualTo("cancelled");
        assertThatThrownBy(()->routing.confirmAccount(saved.id(),catalog.account(f.company.id()).id(),shipments.get(saved.id()).version(),true,f.actor)).isInstanceOf(BusinessException.class);
    }

    @Test void selectedServerQuoteControlsPriceAndCompanyInsteadOfClientAmount() {
        var f=fixture(); var quote=quote(f);
        Shipment saved=create(f, new ShipmentFreightChoice(quote.quoteId(), f.company.id(), false), f.actor);
        assertThat(saved.content().estimatedFreight()).isEqualByComparingTo("14");
        assertThat(saved.content().logisticsCompany()).isEqualTo(f.company.name());
        assertThat(selection(saved.id()).quoteId()).isEqualTo(quote.quoteId());
    }
    @Test void noQuoteStillAllowsDraftSavingWithoutPretendingItIsFree() {
        var f=fixture(); Shipment saved=create(f,null,f.actor);
        assertThat(saved.content().estimatedFreight()).isNull();
        assertThat(saved.content().status()).isEqualTo("unfinished");
        assertThat(selection(saved.id())).isNull();
    }
    @Test void managedDraftUpdatesCannotInventAnAmountWithoutAnIssuedQuote() {
        var f=fixture();var saved=create(f,null,f.actor);
        var edited=update(saved,saved.content().form().withEstimatedFreight(new BigDecimal("777.77")),null,f.actor);
        assertThat(edited.content().estimatedFreight()).isNull();
    }
    @Test void legacyManualEstimateIsPreservedWhenManagedPricingIsIntroduced() {
        var f=fixture();var saved=shipments.create(f.form.withEstimatedFreight(new BigDecimal("12.34")),List.of(f.preparerId),"test","原录入员");
        var edited=update(saved,saved.content().form().withEstimatedFreight(new BigDecimal("777.77")),null,f.actor);
        assertThat(edited.content().estimatedFreight()).isEqualByComparingTo("12.34");
    }
    @Test void changedPriceInputsClearCurrentQuoteButNonPriceEditsPreserveExpiredSnapshot() throws Exception {
        var f=fixture(); var quote=quote(f); Shipment saved=create(f,new ShipmentFreightChoice(quote.quoteId(),f.company.id(),false),f.actor);
        jdbc.update("update logistics_quote set expires_at=date_sub(now(3),interval 1 hour) where quote_id=?",quote.quoteId());
        var noteOnly=saved.content().form().withPreparationContent("补充商品5件","仅改备注");
        Shipment edited=update(saved,noteOnly,null,f.actor);
        assertThat(selection(edited.id()).quoteId()).isEqualTo(quote.quoteId());
        assertThat(edited.content().estimatedFreight()).isEqualByComparingTo("14");
        ObjectNode changed=mapper.valueToTree(edited.content().form());
        ((ObjectNode)changed.get("orderDraft")).put("weight",4);
        Shipment draft=update(edited,mapper.treeToValue(changed,ShipmentFormInput.class),null,f.actor);
        assertThat(selection(draft.id())).isNull();
        assertThat(draft.content().estimatedFreight()).isNull();
    }
    @Test void foreignOrExpiredNewQuoteCannotBeSaved() {
        var f=fixture(); var quote=quote(f); var choice=new ShipmentFreightChoice(quote.quoteId(),f.company.id(),false);
        var other=new ErpPrincipal(f.actor.employeeId()+1,null,"其他人",List.of(),List.of("shipping:create"));
        assertThatThrownBy(()->create(f,choice,other)).isInstanceOf(BusinessException.class);
        jdbc.update("update logistics_quote set expires_at=date_sub(now(3),interval 1 hour) where quote_id=?",quote.quoteId());
        assertThatThrownBy(()->create(f,choice,f.actor)).isInstanceOf(BusinessException.class);
    }
    @Test void submittedOrderCannotClearItsCarrierSelection() throws Exception {
        var f=fixture(); var quote=quote(f); Shipment saved=create(f,new ShipmentFreightChoice(quote.quoteId(),f.company.id(),false),f.actor);
        jdbc.update("insert into shipment_logistics_order(shipment_id,order_no,state,test_environment,tracking_no,child_tracking_nos,message,request_snapshot,operator_id,created_at,updated_at) values(?,?,'succeeded',true,'TEST','','test',?,'test',now(3),now(3))",saved.id(),"FREIGHT-"+saved.id(),mapper.writeValueAsString(saved.content()));
        assertThatThrownBy(()->update(saved,saved.content().form(),new ShipmentFreightChoice(null,null,true),f.actor))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("SHIPMENT_ALREADY_ORDERED"));
    }

    private record Fixture(Company company,ShipmentFormInput form,long preparerId,ErpPrincipal actor) {}
    private Fixture fixture() {
        return fixture("MANUAL");
    }
    private Sender sender(){return new Sender("验收寄件人","13800000000","浙江省","杭州市","余杭区","仓库1号");}
    private Fixture fixture(String provider) {
        String key="FREIGHT-"+java.util.UUID.randomUUID();
        jdbc.update("insert into employee(employee_no,name,employment_type,status,source,created_at,updated_at) values(?,?,'formal','active','manual',now(3),now(3))",key,"验收备货员");
        long employee=jdbc.queryForObject("select id from employee where employee_no=?",Long.class,key);
        var p=platforms.createPlatform(new PlatformDtos.Create(null,key,0,"",null),"test");
        var s=platforms.createShop(new PlatformDtos.Create(null,"验收店铺",0,"",p.id(),"ecommerce","验收",null),"test");
        Company c=catalog.createCompany(new CompanyInput(key,"合作物流验收",provider,"enabled","","",0,"",null));
        if(provider.equals("ANE"))catalog.saveAccount(c.id(),new AccountInput(0,"PORTAL","CUST-OLD","DICT","OLD-KEY","OLD-PASS",catalog.expectedOrderUrl(),sender()));
        var rules=new Rules("weight",false,new BigDecimal("6000"),List.of(new Band(BigDecimal.ZERO,null,BigDecimal.ONE,new BigDecimal("10"),BigDecimal.ONE,new BigDecimal("2"))),List.of());
        catalog.createTemplate(c.id(),new TemplateInput("验收模板",provider.equals("ANE")?"524":"standard","标准产品","浙江省","杭州市","余杭区",rules,"enabled","",0));
        var form=new ShipmentFormInput(p.name(),s.name(),List.of("验收备货员"),"验收收件人","13800000000","浙江省","杭州市","余杭区","验收地址1号","商品3件","",BigDecimal.ZERO,
            new AneOrderDraft("验收商品","纸箱",new BigDecimal("3"),new BigDecimal("0.03"),1,524,180,102,""),p.id(),s.id(),"验收寄件人","13800000000","浙江省","杭州市","余杭区","仓库1号");
        return new Fixture(c,form,employee,new ErpPrincipal(employee,null,"验收录入员",List.of("SUPER_ADMIN"),List.of("shipping:create","shipping:edit","shipping:view","logistics:configure")));
    }
    private Quote quote(Fixture f) { return quotes.quote(FreightQuoteService.input(f.form),f.actor.employeeId()).quotes().stream().filter(q->q.companyId()==f.company.id()&&q.available()).findFirst().orElseThrow(); }
    private Object coordinator() { return context.getBean("shipmentFreightCoordinator"); }
    private Shipment create(Fixture f,ShipmentFreightChoice choice,ErpPrincipal actor) { return ReflectionTestUtils.invokeMethod(coordinator(),"create",f.form,List.of(f.preparerId),choice,actor); }
    private Shipment update(Shipment saved,ShipmentFormInput form,ShipmentFreightChoice choice,ErpPrincipal actor) { return ReflectionTestUtils.invokeMethod(coordinator(),"update",saved.id(),form,saved.content().status(),saved.version(),saved.preparerEmployeeIds(),choice,actor); }
    private Quote selection(long id) { return ReflectionTestUtils.invokeMethod(coordinator(),"selection",id); }
}
