package com.bebefish.erp.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.SupplierQuote;
import com.bebefish.erp.product.domain.SupplierQuoteRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class SupplierQuoteServiceTest {
    private FakeSupplierQuoteRepository repository;
    private FakeProductRepository productRepository;
    private SupplierQuoteService service;

    @BeforeEach
    void setUp() {
        repository = new FakeSupplierQuoteRepository();
        productRepository = new FakeProductRepository();
        service = new SupplierQuoteService(repository, productRepository);
    }

    @Test
    void skuMayHaveMultipleQuotesButOnlyOneDefault() {
        var first = service.saveQuote(10L, quote(1L, "2.20", true));
        var second = service.saveQuote(10L, quote(2L, "2.05", true));

        assertThat(repository.findById(first.id()).orElseThrow().isDefault()).isFalse();
        assertThat(repository.findById(second.id()).orElseThrow().isDefault()).isTrue();
    }

    @Test
    void updatesStandardCostOnlyWhenExplicitlyConfirmed() {
        var quote = service.saveQuote(10L, quote(1L, "2.20", false));

        service.setDefaultQuote(10L, quote.id(), false);
        assertThat(productRepository.findSku(10L).orElseThrow().standardCost())
                .isNotEqualByComparingTo("2.20");

        service.setDefaultQuote(10L, quote.id(), true);
        assertThat(productRepository.findSku(10L).orElseThrow().standardCost())
                .isEqualByComparingTo("2.20");
    }

    @Test
    void rejectsDuplicateSupplierQuoteForSameSku() {
        service.saveQuote(10L, quote(1L, "2.20", false));

        assertThatThrownBy(() -> service.saveQuote(10L, quote(1L, "2.10", false)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("SUPPLIER_QUOTE_CONFLICT"));
    }

    @Test
    void refusesToSetDisabledQuoteAsDefault() {
        var saved = repository.save(new SupplierQuote(
                null, 10L, 1L, null, new BigDecimal("2.20"), BigDecimal.ONE,
                false, "disabled"
        ));

        assertThatThrownBy(() -> service.setDefaultQuote(10L, saved.id(), true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("禁用报价不能设为默认报价");
    }

    @Test
    void updatingDefaultQuoteSynchronizesCostOnlyWhenExplicitlyConfirmed() {
        var saved = service.saveQuote(10L, quote(1L, "2.20", true));

        service.updateQuote(10L, saved.id(), quote(1L, "2.00", true), false);
        assertThat(productRepository.findSku(10L).orElseThrow().standardCost())
                .isEqualByComparingTo("1");

        service.updateQuote(10L, saved.id(), quote(1L, "1.90", true), true);
        assertThat(productRepository.findSku(10L).orElseThrow().standardCost())
                .isEqualByComparingTo("1.90");
    }

    private SaveSupplierQuoteCommand quote(Long supplierId, String price, boolean isDefault) {
        return new SaveSupplierQuoteCommand(
                supplierId, "SUP-ITEM", new BigDecimal(price), BigDecimal.ONE, isDefault
        );
    }

    private static final class FakeSupplierQuoteRepository implements SupplierQuoteRepository {
        private final AtomicLong sequence = new AtomicLong();
        private final Map<Long, SupplierQuote> quotes = new LinkedHashMap<>();

        @Override
        public boolean existsBySkuIdAndSupplierId(long skuId, long supplierId, Long excludedQuoteId) {
            return quotes.values().stream().anyMatch(quote -> quote.skuId() == skuId
                    && quote.supplierId() == supplierId && !quote.id().equals(excludedQuoteId));
        }

        @Override
        public SupplierQuote save(SupplierQuote quote) {
            var id = quote.id() == null ? sequence.incrementAndGet() : quote.id();
            var saved = new SupplierQuote(
                    id, quote.skuId(), quote.supplierId(), quote.supplierItemNo(), quote.purchasePrice(),
                    quote.minPurchaseQuantity(), quote.isDefault(), quote.status()
            );
            quotes.put(id, saved);
            return saved;
        }

        @Override
        public Optional<SupplierQuote> findById(long id) {
            return Optional.ofNullable(quotes.get(id));
        }

        @Override
        public List<SupplierQuote> findBySkuId(long skuId) {
            return quotes.values().stream().filter(quote -> quote.skuId() == skuId).toList();
        }

        @Override
        public void clearDefault(long skuId) {
            quotes.replaceAll((id, quote) -> quote.skuId() == skuId
                    ? quote.withDefault(false)
                    : quote);
        }
    }

    private static final class FakeProductRepository implements ProductRepository {
        private Sku sku = new Sku(
                10L, "SKU-10", null, "测试 SKU", null, List.of(), "只",
                BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, null, null, true, "enabled"
        );

        @Override public boolean existsByProductCode(String code, Long excludedProductId) { return false; }
        @Override public boolean existsByItemNo(String itemNo, Long excludedProductId) { return false; }
        @Override public boolean existsBySkuCode(String code, Long excludedProductId) { return false; }
        @Override public boolean existsByBarcode(String barcode, Long excludedProductId) { return false; }
        @Override public Product save(Product product) { return product; }
        @Override public String nextProductCode() { return "PRD-000001"; }
        @Override public Optional<Product> findById(long id) { return Optional.empty(); }
        @Override public Page<Product> findAll(String keyword, Long categoryId, Long supplierId, String status, Pageable pageable) {
            return new PageImpl<>(List.of());
        }
        @Override public Optional<Sku> findSku(long id) { return id == sku.id() ? Optional.of(sku) : Optional.empty(); }
        @Override public void updateSkuStandardCost(long id, BigDecimal standardCost) {
            if (id == sku.id()) sku = sku.withStandardCost(standardCost);
        }
    }
}
