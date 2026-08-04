package com.bebefish.erp.sales.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.inventory.application.InventoryChange;
import com.bebefish.erp.inventory.application.InventoryService;
import com.bebefish.erp.inventory.application.InventorySource;
import com.bebefish.erp.masterdata.application.CustomerService;
import com.bebefish.erp.masterdata.application.WarehouseService;
import com.bebefish.erp.masterdata.domain.Customer;
import com.bebefish.erp.masterdata.domain.Warehouse;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.sales.domain.SalesAmountCalculator;
import com.bebefish.erp.sales.domain.SalesLineInput;
import com.bebefish.erp.sales.domain.SalesOrder;
import com.bebefish.erp.sales.domain.SalesOrderItem;
import com.bebefish.erp.sales.domain.SalesOrderNumberGenerator;
import com.bebefish.erp.sales.domain.SalesOrderRepository;
import com.bebefish.erp.sales.domain.SalesOrderSearchCriteria;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalesDraftService {
    private static final Set<String> TRANSPORT_METHODS = Set.of("pickup", "delivery", "consignment", "freight", "express");
    private static final Set<String> SETTLEMENT_CYCLES = Set.of("daily", "monthly", "quarterly", "yearly", "annual");
    private final CustomerService customerService;
    private final WarehouseService warehouseService;
    private final ProductRepository productRepository;
    private final SalesOrderRepository orderRepository;
    private final SalesOrderNumberGenerator numberGenerator;
    private final SalesAmountCalculator amountCalculator;
    private final InventoryService inventoryService;

    public SalesDraftService(
            CustomerService customerService,
            WarehouseService warehouseService,
            ProductRepository productRepository,
            SalesOrderRepository orderRepository,
            SalesOrderNumberGenerator numberGenerator,
            SalesAmountCalculator amountCalculator,
            InventoryService inventoryService
    ) {
        this.customerService = customerService;
        this.warehouseService = warehouseService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.numberGenerator = numberGenerator;
        this.amountCalculator = amountCalculator;
        this.inventoryService = inventoryService;
    }

    @Transactional
    public SalesOrder create(SaveSalesOrderCommand command, String salespersonMobile) {
        var salesDate = command == null || command.salesDate() == null ? LocalDate.now() : command.salesDate();
        var number = numberGenerator.next(salesDate);
        return orderRepository.save(buildOrder(
                null, number, null, command, salespersonMobile, salesDate, "draft", null
        ));
    }

    @Transactional
    public SalesOrder update(long id, SaveSalesOrderCommand command, String salespersonMobile) {
        var existing = get(id);
        ensureDraft(existing, "已确认销售单不能直接修改");
        var salesDate = command == null || command.salesDate() == null ? existing.salesDate() : command.salesDate();
        return orderRepository.save(buildOrder(
                id, existing.salesNo(), existing.createdAt(), command, salespersonMobile, salesDate, "draft", existing
        ));
    }

    public SalesOrder update(long id, SaveSalesOrderCommand command) {
        var existing = get(id);
        return update(id, command, existing.salespersonMobile());
    }

    @Transactional(readOnly = true)
    public SalesOrder get(long id) {
        return orderRepository.findById(id).orElseThrow(() -> new BusinessException(
                "SALES_ORDER_NOT_FOUND", HttpStatus.NOT_FOUND, "销售单不存在"
        ));
    }

    @Transactional(readOnly = true)
    public Page<SalesOrder> list(SalesOrderSearchCriteria criteria, Pageable pageable) {
        return orderRepository.findAll(criteria, pageable);
    }

    @Transactional
    public void delete(long id) {
        var existing = get(id);
        ensureDraft(existing, "已确认销售单不能删除");
        orderRepository.deleteById(id);
    }

    @Transactional
    public SalesOrder confirm(long id, String operatorMobile) {
        var existing = get(id);
        ensureDraft(existing, "销售单已确认，请勿重复操作");
        inventoryService.decrease(
                existing.warehouseId(),
                existing.items().stream()
                        .map(item -> new InventoryChange(item.skuId(), item.quantity()))
                        .toList(),
                new InventorySource("sales", existing.id(), existing.salesNo()),
                operatorMobile
        );
        return orderRepository.save(withStatus(existing, "confirmed", existing.remark()));
    }

    @Transactional
    public SalesOrder voidOrder(long id, String reason, String operatorMobile) {
        var existing = get(id);
        if (!"confirmed".equals(existing.status())) {
            throw new BusinessException("SALES_ORDER_NOT_CONFIRMED", HttpStatus.BAD_REQUEST, "只有已确认销售单才能作废");
        }
        var voidReason = required(reason, "作废原因不能为空");
        inventoryService.increase(
                existing.warehouseId(),
                existing.items().stream()
                        .map(item -> new InventoryChange(item.skuId(), item.quantity()))
                        .toList(),
                new InventorySource("sales_void", existing.id(), existing.salesNo()),
                operatorMobile
        );
        return orderRepository.save(withStatus(existing, "void", voidReason));
    }

    private SalesOrder withStatus(SalesOrder order, String status, String remark) {
        return new SalesOrder(
                order.id(), order.salesNo(), order.customerId(), order.customerName(), order.warehouseId(),
                order.warehouseName(), order.salesDate(), order.salespersonMobile(), status, order.transportMethod(),
                order.settlementCycle(), order.paymentMethod(), order.deliveryAddress(), order.logisticsCompany(),
                order.trackingNo(), order.packageNote(), order.invoiceRequired(), order.invoiceStatus(),
                order.goodsAmount(), order.discountAmount(), order.shippingFee(), order.totalAmount(),
                order.receivedAmount(), order.outstandingAmount(), remark, order.createdAt(), LocalDateTime.now(),
                order.items()
        );
    }

    private SalesOrder buildOrder(
            Long id,
            String salesNo,
            java.time.LocalDateTime createdAt,
            SaveSalesOrderCommand command,
            String salespersonMobile,
            LocalDate salesDate,
            String status,
            SalesOrder existing
    ) {
        if (command == null) throw validation("销售单信息不能为空");
        var operator = required(salespersonMobile, "业务代表不能为空");
        if (command.customerId() == null || command.customerId() <= 0) throw validation("客户不能为空");
        var customer = customerService.get(command.customerId());
        ensureEnabled(customer.status(), "CUSTOMER_DISABLED", "客户已停用");
        var warehouse = command.warehouseId() == null
                ? warehouseService.getDefaultWarehouse()
                : warehouseService.get(command.warehouseId());
        ensureEnabled(warehouse.status(), "WAREHOUSE_DISABLED", "仓库已停用");
        var transport = optional(command.transportMethod(), customer.transportMethod());
        if (!TRANSPORT_METHODS.contains(transport)) throw validation("运输方式无效");
        var settlement = optional(command.settlementCycle(), customer.settlementCycle());
        if (!SETTLEMENT_CYCLES.contains(settlement)) throw validation("结算周期无效");
        var lines = command.lines();
        if (lines.isEmpty()) throw validation("销售单至少需要一条明细");
        var skuIds = new HashSet<Long>();
        var snapshots = lines.stream().map(line -> snapshot(line, skuIds)).toList();
        var amountInputs = snapshots.stream()
                .map(line -> new SalesLineInput(line.quantity(), line.unitPrice(), line.discountRate()))
                .toList();
        var summary = calculate(command, amountInputs);
        var invoiceStatus = command.invoiceRequired() ? optional(command.invoiceStatus(), "pending") : "not_required";
        if (!Set.of("not_required", "pending", "issued").contains(invoiceStatus)) {
            throw validation("开票状态无效");
        }
        return new SalesOrder(
                id, salesNo, customer.id(), customer.name(), warehouse.id(), warehouse.name(), salesDate,
                operator, status, transport, settlement, optional(command.paymentMethod(), null),
                optional(command.deliveryAddress(), address(customer)), optional(command.logisticsCompany(), null),
                optional(command.trackingNo(), null), optional(command.packageNote(), null), command.invoiceRequired(),
                invoiceStatus, summary.goodsAmount(), summary.discountAmount(), summary.shippingFee(),
                summary.totalAmount(), summary.receivedAmount(), summary.outstandingAmount(),
                optional(command.remark(), null), createdAt, null, snapshots
        );
    }

    private SalesOrderItem snapshot(SaveSalesOrderLineCommand line, Set<Long> skuIds) {
        if (line == null || line.skuId() <= 0 || !skuIds.add(line.skuId())) {
            throw validation("销售商品不能为空且不能重复");
        }
        var product = productRepository.findProductBySkuId(line.skuId()).orElseThrow(() -> new BusinessException(
                "SKU_NOT_FOUND", HttpStatus.NOT_FOUND, "SKU 不存在"
        ));
        ensureEnabled(product.status(), "PRODUCT_DISABLED", "商品已停用");
        var sku = product.skus().stream().filter(value -> value.id().equals(line.skuId())).findFirst()
                .orElseThrow(() -> new BusinessException("SKU_NOT_FOUND", HttpStatus.NOT_FOUND, "SKU 不存在"));
        ensureEnabled(sku.status(), "SKU_DISABLED", "SKU 已停用");
        var defaultPrice = moneyOrZero(sku.defaultSalePrice());
        var unitPrice = line.unitPrice() == null ? defaultPrice : line.unitPrice();
        var discountRate = line.discountRate() == null ? BigDecimal.ZERO : line.discountRate();
        var amount = line.quantity() == null || unitPrice == null
                ? BigDecimal.ZERO
                : line.quantity().multiply(unitPrice)
                        .multiply(BigDecimal.ONE.subtract(discountRate.divide(BigDecimal.valueOf(100))))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
        return new SalesOrderItem(
                null, sku.id(), line.quantity(), defaultPrice, unitPrice, discountRate, amount, sku.standardCost(),
                product.itemNo(), product.name(), sku.code(), sku.name(), sku.specText(),
                sku.packaging() == null ? null : sku.packaging().method(),
                sku.packaging() == null ? null : sku.packaging().cartonQuantity(), sku.barcode(), sku.salesUnit()
        );
    }

    private com.bebefish.erp.sales.domain.SalesAmountSummary calculate(
            SaveSalesOrderCommand command, List<SalesLineInput> lines
    ) {
        try {
            return amountCalculator.calculate(
                    lines,
                    command.shippingFee() == null ? BigDecimal.ZERO : command.shippingFee(),
                    command.receivedAmount() == null ? BigDecimal.ZERO : command.receivedAmount()
            );
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage());
        }
    }

    private void ensureDraft(SalesOrder order, String message) {
        if (!"draft".equals(order.status())) throw new BusinessException("SALES_ORDER_LOCKED", HttpStatus.CONFLICT, message);
    }

    private void ensureEnabled(String status, String code, String message) {
        if (!"enabled".equals(status)) throw new BusinessException(code, HttpStatus.CONFLICT, message);
    }

    private String address(Customer customer) {
        return java.util.stream.Stream.of(customer.province(), customer.city(), customer.district(), customer.detailAddress())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining());
    }

    private String optional(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String required(String value, String message) {
        var normalized = optional(value, null);
        if (normalized == null) throw validation(message);
        return normalized;
    }

    private BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message == null ? "销售单信息无效" : message);
    }
}
