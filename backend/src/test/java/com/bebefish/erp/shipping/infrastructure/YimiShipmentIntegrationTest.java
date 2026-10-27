package com.bebefish.erp.shipping.infrastructure;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.common.security.ErpPrincipal;
import com.bebefish.erp.logistics.*;
import com.bebefish.erp.logistics.LogisticsDtos.*;
import com.bebefish.erp.logistics.pricing.FreightRules.*;
import com.bebefish.erp.platform.api.PlatformDtos;
import com.bebefish.erp.platform.application.PlatformCatalogService;
import com.bebefish.erp.shipping.application.*;
import com.bebefish.erp.shipping.domain.*;
import com.bebefish.erp.shipping.logistics.*;
import com.bebefish.erp.shipping.logistics.yimi.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="ERP_LOGISTICS_MASTER_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@ActiveProfiles("test") @Transactional
class YimiShipmentIntegrationTest {
    @Autowired LogisticsCatalogService catalog;
    @Autowired FreightQuoteService quotes;
    @Autowired PlatformCatalogService platforms;
    @Autowired ShipmentFreightCoordinator freight;
    @Autowired ShipmentService shipments;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AneOrderService orders;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean YimiOrderClient yimi;
    @MockBean AneOrderClient ane;

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void commitsCompleteReservationBeforeCallingYimiOutsideAnyTransaction() {
        var transactions=new TransactionTemplate(transactionManager);
        // Commit setup separately; a setup failure rolls back every fixture row.
        var fixture=java.util.Objects.requireNonNull(transactions.execute(status->{
            var f=fixture();return new DurableFixture(f,create(f));
        }));
        var f=fixture.fixture();var saved=fixture.shipment();
        try {
            var originalAccount=catalog.account(f.company.id());
            when(yimi.createOrder(anyMap(),any(),anyString())).thenAnswer(invocation->{
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                Map<String,Object> outbound=invocation.getArgument(0);
                String orderNo=invocation.getArgument(2);
                // A raw, separately acquired connection cannot see uncommitted caller writes.
                try(var connection=dataSource.getConnection();var statement=connection.prepareStatement("""
                        select state,carrier_provider,test_environment,order_no,carrier_company_id,
                               carrier_account_id,carrier_account_version_id,carrier_business_snapshot,request_snapshot
                        from shipment_logistics_order where shipment_id=?
                        """)) {
                    assertThat(connection.getAutoCommit()).isTrue();
                    statement.setLong(1,saved.id());
                    try(var row=statement.executeQuery()) {
                        assertThat(row.next()).as("reservation visible before the carrier returns").isTrue();
                        assertThat(row.getString("state")).isEqualTo("processing");
                        assertThat(row.getString("carrier_provider")).isEqualTo("YIMI");
                        assertThat(row.getBoolean("test_environment")).isTrue();
                        assertThat(row.getString("order_no")).isEqualTo(orderNo);
                        assertThat(row.getLong("carrier_company_id")).isEqualTo(f.company.id());
                        assertThat(row.getLong("carrier_account_id")).isEqualTo(originalAccount.id());
                        assertThat(row.getLong("carrier_account_version_id")).isEqualTo(originalAccount.accountVersionId());
                        String snapshot=row.getString("carrier_business_snapshot");
                        var business=mapper.readTree(snapshot);
                        assertThat(business).isEqualTo(mapper.readTree(mapper.writeValueAsString(outbound)));
                        assertThat(business.path("outSideNo").asText()).isEqualTo(orderNo);
                        assertThat(business.path("sellerId").asText()).isEqualTo("seller-old");
                        assertThat(business.path("subscriptionId").asText()).isEqualTo("sub-old");
                        assertThat(business.path("serviceType").asInt()).isEqualTo(1);
                        assertThat(business.path("settlementType").asInt()).isEqualTo(2);
                        assertThat(business.path("visitDelivery").asInt()).isZero();
                        assertThat(business.path("deliveryFlag").asInt()).isEqualTo(4);
                        assertThat(business.path("electSenderRequest").path("senderAddress").asText()).isEqualTo("验收仓库1号");
                        assertThat(business.path("electReceiverRequest").path("receiverAddress").asText()).isEqualTo("验收路36号");
                        assertThat(business.path("goodsRequest").path("goodsName").asText()).isEqualTo("商品");
                        assertThat(business.path("goodsRequest").path("goodsWeight").decimalValue()).isEqualByComparingTo("12");
                        assertThat(business.path("goodsRequest").path("goodsVolume").decimalValue()).isEqualByComparingTo("0.12");
                        assertThat(business.path("goodsRequest").path("goodsQuqntity").asInt()).isEqualTo(1);
                        assertThat(snapshot).doesNotContain("OLD-KEY","OLD-SECRET","appKey","appSecret","customerPass","secret_cipher");
                        assertThat(mapper.readTree(row.getString("request_snapshot")))
                            .isEqualTo(mapper.readTree(mapper.writeValueAsString(saved.content())));
                        assertThat(row.next()).isFalse();
                    }
                }
                return new YimiOrderResult(new AneOrderResult("succeeded","778899444496","778899444496001","取号成功"),Map.of("outSideNo",orderNo,"wayBillNo","778899444496"));
            });
            var placed=orders.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test");
            assertThat(placed.state()).isEqualTo("succeeded");
            assertThat(placed.provider()).isEqualTo("YIMI");
            assertThat(placed.trackingNo()).isEqualTo("778899444496");
            assertThat(jdbc.queryForObject("select state from shipment_logistics_order where shipment_id=?",String.class,saved.id())).isEqualTo("succeeded");
            assertThat(shipments.get(saved.id()).content().trackingNo()).isEqualTo("778899444496");
            verify(yimi,times(1)).createOrder(anyMap(),any(),anyString());
            verifyNoMoreInteractions(yimi);verifyNoInteractions(ane);
        } finally {
            transactions.executeWithoutResult(status->cleanup(fixture));
        }
    }

    @Test void selectedYimiUsesItsOwnPayloadAndFreezesBusinessOnlySnapshot() throws Exception {
        var f=fixture();var saved=create(f);stubSuccess();
        var placed=orders.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test");
        assertThat(placed.state()).isEqualTo("succeeded");
        assertThat(placed.trackingNo()).isEqualTo("778899444496");
        assertThat(mapper.valueToTree(placed).path("provider").asText()).isEqualTo("YIMI");
        var payload=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(yimi).createOrder(payload.capture(),any(YimiProperties.class),anyString());
        assertThat(payload.getValue()).containsEntry("settlementType",2).containsEntry("visitDelivery",0);
        assertThat(payload.getValue()).doesNotContainKeys("appKey","appSecret","customerPass","productTypeId");
        String snapshot=jdbc.queryForObject("select carrier_business_snapshot from shipment_logistics_order where shipment_id=?",String.class,saved.id());
        assertThat(mapper.readTree(snapshot).path("outSideNo").asText()).isEqualTo(placed.orderNo());
        assertThat(snapshot).doesNotContain("OLD-KEY","OLD-SECRET");
        assertThat(shipments.get(saved.id()).content().logisticsCompany()).isEqualTo(f.company.name());
        assertThat(shipments.get(saved.id()).content().status()).isEqualTo("unfinished");
        verifyNoInteractions(ane);
    }
    @Test void missingExplicitPickupRejectsBeforeCreatingAReservation() {
        var f=fixture(new YimiOrderOptions(null,null,""));var saved=create(f);
        assertThatThrownBy(()->orders.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test"))
            .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo("VALIDATION_FAILED"));
        assertThat(jdbc.queryForObject("select count(*) from shipment_logistics_order where shipment_id=?",Long.class,saved.id())).isZero();
        verifyNoInteractions(yimi,ane);
    }
    @Test void unknownOrderCannotTriggerASecondCarrierRequest() {
        var f=fixture();var saved=create(f);
        when(yimi.createOrder(anyMap(),any(),anyString())).thenReturn(new YimiOrderResult(new AneOrderResult("unknown","","","待核实"),Map.of()));
        assertThat(orders.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test").state()).isEqualTo("unknown");
        assertThat(orders.place(saved.id(),new PlaceAneOrderRequest(shipments.get(saved.id()).version()),"test").state()).isEqualTo("unknown");
        verify(yimi,times(1)).createOrder(anyMap(),any(),anyString());verifyNoInteractions(ane);
    }
    @Test void cancelAndTraceKeepOriginalIdentityAndRotatedSecretAfterCompanyReplacement() {
        var f=fixture();var saved=create(f);stubSuccess();orders.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test");
        var old=catalog.account(f.company.id());
        catalog.saveAccount(f.company.id(),account(old.version(),"","ROTATED-SECRET","seller-old","sub-old"));
        var rotated=catalog.account(f.company.id());
        catalog.saveAccount(f.company.id(),account(rotated.version(),"NEW-KEY","NEW-SECRET","seller-new","sub-new"));
        var company=catalog.company(f.company.id());catalog.companyStatus(company.id(),new StatusInput("disabled",company.version()));
        when(yimi.queryTrace(anyString(),any())).thenReturn(new YimiTraceResult("empty","无轨迹信息",List.of()));
        YimiTraceResult trace=ReflectionTestUtils.invokeMethod(orders,"trace",saved.id());
        assertThat(trace.status()).isEqualTo("empty");
        when(yimi.cancelOrder(anyMap(),any())).thenReturn(new AneCancellationResult("cancelled","已取消"));
        var version=shipments.get(saved.id()).version();
        assertThat(orders.cancel(saved.id(),new CancelAneOrderRequest(version),"test").state()).isEqualTo("cancelled");
        var context=org.mockito.ArgumentCaptor.forClass(YimiProperties.class);
        verify(yimi).queryTrace(eq("778899444496"),context.capture());
        verify(yimi).cancelOrder(argThat(body->"778899444496".equals(String.valueOf(body.get("wayBillNo")))&&!body.containsKey("orderNos")),context.capture());
        assertThat(context.getAllValues()).allSatisfy(c->{assertThat(c.appKey()).isEqualTo("OLD-KEY");assertThat(c.appSecret()).isEqualTo("ROTATED-SECRET");assertThat(c.sellerId()).isEqualTo("seller-old");assertThat(c.testEnvironment()).isTrue();});
        orders.cancel(saved.id(),new CancelAneOrderRequest(shipments.get(saved.id()).version()),"test");
        verify(yimi,times(1)).cancelOrder(anyMap(),any());verifyNoInteractions(ane);
    }
    @Test void cancelledUnknownResultKeepsTrackingAndDoesNotRetry() {
        var f=fixture();var saved=create(f);stubSuccess();orders.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test");
        when(yimi.cancelOrder(anyMap(),any())).thenReturn(new AneCancellationResult("cancel_unknown","取消待核实"));
        var result=orders.cancel(saved.id(),new CancelAneOrderRequest(shipments.get(saved.id()).version()),"test");
        assertThat(result.state()).isEqualTo("cancel_unknown");
        assertThat(shipments.get(saved.id()).content().trackingNo()).isEqualTo("778899444496");
        orders.cancel(saved.id(),new CancelAneOrderRequest(shipments.get(saved.id()).version()),"test");
        verify(yimi,times(1)).cancelOrder(anyMap(),any());
    }
    @Test void providerMismatchRejectsCancellationBeforeAnyCarrierRequest() {
        var f=fixture();var saved=create(f);stubSuccess();
        orders.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test");
        jdbc.update("update shipment_logistics_order set carrier_provider='ANE' where shipment_id=?",saved.id());
        assertThatThrownBy(()->orders.cancel(saved.id(),new CancelAneOrderRequest(shipments.get(saved.id()).version()),"test"))
            .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo("LOGISTICS_ACCOUNT_IDENTITY_MISMATCH"));
        assertThat(jdbc.queryForObject("select state from shipment_logistics_order where shipment_id=?",String.class,saved.id())).isEqualTo("succeeded");
        verify(yimi,times(1)).createOrder(anyMap(),any(),anyString());
        verifyNoMoreInteractions(yimi);verifyNoInteractions(ane);
    }
    @Test void environmentMismatchRejectsCancellationAndTraceBeforeAnyCarrierRequest() {
        var f=fixture();var saved=create(f);stubSuccess();
        orders.place(saved.id(),new PlaceAneOrderRequest(saved.version()),"test");
        jdbc.update("update shipment_logistics_order set test_environment=false where shipment_id=?",saved.id());
        assertThatThrownBy(()->orders.cancel(saved.id(),new CancelAneOrderRequest(shipments.get(saved.id()).version()),"test"))
            .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo("LOGISTICS_ACCOUNT_IDENTITY_MISMATCH"));
        assertThatThrownBy(()->orders.trace(saved.id()))
            .isInstanceOfSatisfying(BusinessException.class,error->assertThat(error.code()).isEqualTo("LOGISTICS_ACCOUNT_IDENTITY_MISMATCH"));
        assertThat(jdbc.queryForObject("select state from shipment_logistics_order where shipment_id=?",String.class,saved.id())).isEqualTo("succeeded");
        verify(yimi,times(1)).createOrder(anyMap(),any(),anyString());
        verifyNoMoreInteractions(yimi);verifyNoInteractions(ane);
    }
    @Test void changedOptionsClearQuoteBeforeNewOrderAndNotesPreserveOptions() throws Exception {
        var f=fixture();var saved=create(f);
        var edited=freight.update(saved.id(),saved.content().form().withPreparationContent("商品2件","改备注"),"unfinished",saved.version(),saved.preparerEmployeeIds(),null,f.actor);
        assertThat(edited.content().orderDraft().yimi()).isEqualTo(f.form.orderDraft().yimi());
        assertThat(freight.selection(edited.id())).isNotNull();
        var json=mapper.valueToTree(edited.content().form());((com.fasterxml.jackson.databind.node.ObjectNode)json.path("orderDraft").path("yimi")).put("visitDelivery",1);
        var changed=freight.update(edited.id(),mapper.treeToValue(json,ShipmentFormInput.class),"unfinished",edited.version(),edited.preparerEmployeeIds(),null,f.actor);
        assertThat(freight.selection(changed.id())).isNull();assertThat(changed.content().estimatedFreight()).isNull();
        assertThatThrownBy(()->orders.place(changed.id(),new PlaceAneOrderRequest(changed.version()),"test")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(yimi,ane);
    }
    private void stubSuccess() {
        when(yimi.createOrder(anyMap(),any(),anyString())).thenAnswer(invocation->new YimiOrderResult(new AneOrderResult("succeeded","778899444496","778899444496001","取号成功"),Map.of("outSideNo",invocation.getArgument(2),"wayBillNo","778899444496","sourceSiteCode","TEST")));
    }
    private record Fixture(Company company,ShipmentFormInput form,long employee,ErpPrincipal actor) {}
    private record DurableFixture(Fixture fixture,Shipment shipment) {}
    private void cleanup(DurableFixture durable) {
        var f=durable.fixture();long id=durable.shipment().id();
        jdbc.update("delete from shipment_logistics_order where shipment_id=?",id);
        jdbc.update("delete from shipment_freight_selection where shipment_id=?",id);
        jdbc.update("delete from shipment where id=?",id);
        jdbc.update("delete from logistics_quote where employee_id=?",f.employee);
        jdbc.update("delete from logistics_audit where company_id=?",f.company.id());
        jdbc.update("delete from logistics_template_version where template_id in (select id from logistics_template where company_id=?)",f.company.id());
        jdbc.update("delete from logistics_template where company_id=?",f.company.id());
        jdbc.update("delete from logistics_account_version where account_id in (select id from logistics_account where company_id=?)",f.company.id());
        jdbc.update("delete from logistics_account where company_id=?",f.company.id());
        jdbc.update("delete from logistics_company where id=?",f.company.id());
        jdbc.update("delete from platform_shop where id=?",f.form.shopId());
        jdbc.update("delete from sales_platform where id=?",f.form.platformId());
        jdbc.update("delete from employee where id=?",f.employee);
    }
    private Fixture fixture(){return fixture(new YimiOrderOptions(0,null,""));}
    private Fixture fixture(YimiOrderOptions options) {
        String code="YM-"+java.util.UUID.randomUUID().toString().substring(0,12);
        jdbc.update("insert into employee(employee_no,name,employment_type,status,source,created_at,updated_at) values(?,?,'formal','active','manual',now(3),now(3))",code,"壹米验收备货员");
        long employee=jdbc.queryForObject("select id from employee where employee_no=?",Long.class,code);
        var p=platforms.createPlatform(new PlatformDtos.Create(null,code,0,"",null),"test");
        var s=platforms.createShop(new PlatformDtos.Create(null,"壹米测试店",0,"",p.id(),"ecommerce","验收",null),"test");
        var company=catalog.createCompany(new CompanyInput(code,"壹米测试物流","YIMI","enabled","","",0,"",null));
        catalog.saveAccount(company.id(),account(0,"OLD-KEY","OLD-SECRET","seller-old","sub-old"));
        var rules=new Rules("weight",false,new BigDecimal("6000"),List.of(new Band(BigDecimal.ZERO,null,BigDecimal.ONE,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.ONE)),List.of());
        catalog.createTemplate(company.id(),new TemplateInput("测试合同","1","港到门","浙江省","杭州市","余杭区",rules,"enabled","",0));
        var form=new ShipmentFormInput(p.name(),s.name(),List.of("壹米验收备货员"),"验收收件人","13800000003","广东省","东莞市","寮步镇","验收路36号","商品1件","",null,
            new AneOrderDraft("商品","纸箱",new BigDecimal("12"),new BigDecimal("0.12"),1,524,180,104,"",options),p.id(),s.id(),"验收寄件人","13800000001","浙江省","杭州市","余杭区","验收仓库1号");
        return new Fixture(company,form,employee,new ErpPrincipal(employee,null,"壹米验收录入员",List.of("SUPER_ADMIN"),List.of("shipping:create","shipping:edit","shipping:view","logistics:configure")));
    }
    private AccountInput account(long version,String key,String secret,String seller,String subscription) {
        return mapper.convertValue(Map.of("version",version,"code","","customerCode","","dictId","","appKey",key,"appSecret",secret,"orderUrl",YimiProperties.TEST_GATEWAY.toString(),"sender",Map.of("name","验收寄件人","phone","13800000001","province","浙江省","city","杭州市","county","余杭区","address","验收仓库1号"),"yimi",Map.of("sellerId",seller,"subscriptionId",subscription)),AccountInput.class);
    }
    private Shipment create(Fixture f) {
        var quote=quotes.quote(FreightQuoteService.input(f.form),f.employee).quotes().stream().filter(q->q.companyId()==f.company.id()&&q.available()).findFirst().orElseThrow();
        return freight.create(f.form,List.of(f.employee),new ShipmentFreightChoice(quote.quoteId(),f.company.id(),false),f.actor);
    }
}
