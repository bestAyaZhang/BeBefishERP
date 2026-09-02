package com.bebefish.erp.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.InventoryChange;
import com.bebefish.erp.inventory.application.InventoryService;
import com.bebefish.erp.inventory.application.InventorySource;
import com.bebefish.erp.masterdata.application.CustomerService;
import com.bebefish.erp.masterdata.application.WarehouseService;
import com.bebefish.erp.masterdata.domain.Customer;
import com.bebefish.erp.masterdata.domain.Warehouse;
import com.bebefish.erp.product.domain.Packaging;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.sales.domain.SalesOrder;
import com.bebefish.erp.sales.domain.SalesOrderItem;
import com.bebefish.erp.sales.domain.SalesOrderNumberGenerator;
import com.bebefish.erp.sales.domain.SalesOrderRepository;
import com.bebefish.erp.sales.domain.SalesOrderSequenceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SalesDraftServiceTest {
    private CustomerService customerService;
    private WarehouseService warehouseService;
    private ProductRepository productRepository;
    private SalesOrderRepository orderRepository;
    private InventoryService inventoryService;
    private SalesDraftService service;

    @BeforeEach
    void setUp() {
        customerService = mock(CustomerService.class);
        warehouseService = mock(WarehouseService.class);
        productRepository = mock(ProductRepository.class);
        orderRepository = mock(SalesOrderRepository.class);
        inventoryService = mock(InventoryService.class);
        var numberGenerator = new SalesOrderNumberGenerator(new InMemorySequenceRepository());
        service = new SalesDraftService(
                customerService, warehouseService, productRepository, orderRepository,
                numberGenerator, new com.bebefish.erp.sales.domain.SalesAmountCalculator(), inventoryService
        );
        when(customerService.get(1L)).thenReturn(new Customer(
                1L, "CUS-001", "杭州酒店用品店", "张三", "13800138000", null,
                "浙江省", "杭州市", "西湖区", "文三路88号", "delivery", "monthly",
                false, "enabled", null
        ));
        when(warehouseService.get(10L)).thenReturn(new Warehouse(
                10L, "WH-001", "杭州主仓", "杭州市", true, "enabled", null
        ));
        when(productRepository.findProductBySkuId(20L)).thenReturn(Optional.of(product()));
        when(orderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> {
            var order = invocation.getArgument(0, SalesOrder.class);
            return order.withIdentity(99L, order.createdAt(), order.updatedAt());
        });
    }

    @Test
    void createsDraftUsingCustomerDefaultsAndSkuSnapshot() {
        var draft = service.create(new SaveSalesOrderCommand(
                1L, 10L, LocalDate.of(2026, 7, 10), null, null, null,
                null, null, null, null, null, false, null, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(new SaveSalesOrderLineCommand(
                        20L, new BigDecimal("2"), new BigDecimal("12.50"), BigDecimal.ZERO
                ))
        ), "13800138000");

        assertThat(draft.status()).isEqualTo("draft");
        assertThat(draft.salesNo()).isEqualTo("SO202607100001");
        assertThat(draft.transportMethod()).isEqualTo("delivery");
        assertThat(draft.settlementCycle()).isEqualTo("monthly");
        assertThat(draft.deliveryAddress()).contains("文三路88号");
        assertThat(draft.items().getFirst().itemNoSnapshot()).isEqualTo("EW43245");
        assertThat(draft.totalAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void buildsAddressWhenSomeCustomerAddressPartsAreMissing() {
        when(customerService.get(2L)).thenReturn(new Customer(
                2L, "CUS-002", "散客", "李四", "13900139000", null,
                null, "杭州市", null, "滨江路8号", "pickup", "daily",
                false, "enabled", null
        ));

        var draft = service.create(new SaveSalesOrderCommand(
                2L, 10L, LocalDate.of(2026, 7, 10), null, null, null,
                null, null, null, null, null, false, null, BigDecimal.ZERO,
                BigDecimal.ZERO, List.of(new SaveSalesOrderLineCommand(
                        20L, new BigDecimal("1"), new BigDecimal("12.50"), BigDecimal.ZERO
                ))
        ), "13800138000");

        assertThat(draft.deliveryAddress()).isEqualTo("杭州市滨江路8号");
    }

    @Test
    void onlyDraftMayBeEditedOrDeleted() {
        var confirmed = new SalesOrder(
                77L, "SO202607090001", 1L, "客户", 10L, "仓库", LocalDate.now(),
                "13800138000", "confirmed", "delivery", "monthly", null, null, null, null, null,
                false, "not_required", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, List.of()
        );
        when(orderRepository.findById(77L)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.update(77L, new SaveSalesOrderCommand(
                1L, 10L, LocalDate.now(), "delivery", "monthly", null, null, null, null, null,
                null, false, null, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
        ))).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error).hasMessage("已确认销售单不能直接修改"));
        assertThatThrownBy(() -> service.delete(77L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error).hasMessage("已确认销售单不能删除"));
    }

    @Test
    void confirmsDraftByDecreasingInventoryAndChangingStatus() {
        var draft = draft(88L, "SO202607100088");
        when(orderRepository.findById(88L)).thenReturn(Optional.of(draft));
        when(orderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var confirmed = service.confirm(88L, "13800138000");

        assertThat(confirmed.status()).isEqualTo("confirmed");
        verify(inventoryService).decrease(
                10L,
                List.of(new InventoryChange(20L, new BigDecimal("2"))),
                new InventorySource("sales", 88L, "SO202607100088"),
                "13800138000"
        );
        verify(orderRepository).save(any(SalesOrder.class));
    }

    @Test
    void doesNotConfirmWhenInventoryIsInsufficient() {
        var draft = draft(89L, "SO202607100089");
        when(orderRepository.findById(89L)).thenReturn(Optional.of(draft));
        when(inventoryService.decrease(any(Long.class), any(), any(), any()))
                .thenThrow(new BusinessException(
                        "INVENTORY_NOT_ENOUGH", org.springframework.http.HttpStatus.BAD_REQUEST, "库存不足"
                ));

        assertThatThrownBy(() -> service.confirm(89L, "13800138000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("库存不足");
        org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.never()).save(any(SalesOrder.class));
    }

    @Test
    void voidsConfirmedOrderByRestoringInventory() {
        var confirmed = new SalesOrder(
                90L, "SO202607100090", 1L, "杭州酒店用品店", 10L, "杭州主仓", LocalDate.now(),
                "13800138000", "confirmed", "delivery", "monthly", null, "浙江省杭州市西湖区文三路88号",
                null, null, null, false, "not_required", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("25.00"), BigDecimal.ZERO, new BigDecimal("25.00"), null, null, null,
                List.of(new SalesOrderItem(
                        1L, 20L, new BigDecimal("2"), new BigDecimal("12.00"), new BigDecimal("12.50"),
                        BigDecimal.ZERO, new BigDecimal("25.00"), new BigDecimal("3.00"), "EW43245", "高硼硅玻璃杯",
                        "PRD-000001-001", "透明款", "透明", "彩盒", 48, "BAR-001", "只"
                ))
        );
        when(orderRepository.findById(90L)).thenReturn(Optional.of(confirmed));
        when(orderRepository.save(any(SalesOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var voided = service.voidOrder(90L, "客户取消", "13800138000");

        assertThat(voided.status()).isEqualTo("void");
        assertThat(voided.remark()).isEqualTo("客户取消");
        verify(inventoryService).increase(
                10L,
                List.of(new InventoryChange(20L, new BigDecimal("2"))),
                new InventorySource("sales_void", 90L, "SO202607100090"),
                "13800138000"
        );
    }

    private SalesOrder draft(long id, String salesNo) {
        return new SalesOrder(
                id, salesNo, 1L, "杭州酒店用品店", 10L, "杭州主仓", LocalDate.now(),
                "13800138000", "draft", "delivery", "monthly", null, "浙江省杭州市西湖区文三路88号",
                null, null, null, false, "not_required", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("25.00"), BigDecimal.ZERO, new BigDecimal("25.00"), null, null, null,
                List.of(new SalesOrderItem(
                        1L, 20L, new BigDecimal("2"), new BigDecimal("12.00"), new BigDecimal("12.50"),
                        BigDecimal.ZERO, new BigDecimal("25.00"), new BigDecimal("3.00"), "EW43245", "高硼硅玻璃杯",
                        "PRD-000001-001", "透明款", "透明", "彩盒", 48, "BAR-001", "只"
                ))
        );
    }

    private Product product() {
        var sku = new Sku(
                20L, "PRD-000001-001", "BAR-001", "透明款", "透明",
                List.of("透明"), "只", new BigDecimal("12.00"), new BigDecimal("3.00"),
                BigDecimal.ZERO,
                new Packaging(
                        null, null, null, null, null, null, null,
                        null, null, null, null,
                        null, null, null, null, "彩盒", 48, null, null
                ),
                null, false, "enabled"
        );
        return new Product(100L, "PRD-000001", "EW43245", "高硼硅玻璃杯", 1L, "共典",
                ProductType.VARIANT, null, "enabled", null, List.of(), List.of(sku), null, null);
    }

    private static final class InMemorySequenceRepository implements SalesOrderSequenceRepository {
        private int value;

        @Override
        public int next(LocalDate businessDate) {
            return ++value;
        }
    }
}
