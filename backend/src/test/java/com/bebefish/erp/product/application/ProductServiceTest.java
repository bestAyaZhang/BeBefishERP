package com.bebefish.erp.product.application;

import static com.bebefish.erp.product.domain.ProductType.SIMPLE;
import static com.bebefish.erp.product.domain.ProductType.VARIANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.domain.DefaultSkuCombinationGenerator;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.Specification;
import java.math.BigDecimal;
import java.util.ArrayList;
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

class ProductServiceTest {
    private FakeProductRepository repository;
    private ProductService service;

    @BeforeEach
    void setUp() {
        repository = new FakeProductRepository();
        service = new ProductService(repository, new DefaultSkuCombinationGenerator());
    }

    @Test
    void createsSimpleProductWithPackagingDataOnDefaultSku() {
        var result = service.createProduct(simpleProduct(
                "P100", "EW43245", packaging("42", "31", "28", null, "8.5", "9.2")
        ));

        assertThat(result.skus()).hasSize(1);
        assertThat(result.skus().getFirst().code()).isEqualTo("PRD-000001-DEFAULT");
        assertThat(result.skus().getFirst().isDefault()).isTrue();
        assertThat(result.skus().getFirst().packageVolumeCm3())
                .isEqualByComparingTo("36456.00");
    }

    @Test
    void generatesProductCodeInsteadOfUsingUserProvidedCode() {
        var result = service.createProduct(simpleProduct(
                "USER-ENTERED-CODE", "EW43249", packaging("1", "1", "1", null, "1", "1")
        ));

        assertThat(result.code()).matches("PRD-\\d{6}");
    }

    @Test
    void keepsManuallyEnteredPackageVolume() {
        var result = service.createProduct(simpleProduct(
                "P101", "EW43246", packaging("42", "31", "28", "36000", "8.5", "9.2")
        ));

        assertThat(result.skus().getFirst().packageVolumeCm3())
                .isEqualByComparingTo("36000");
    }

    @Test
    void rejectsGrossWeightBelowNetWeight() {
        assertThatThrownBy(() -> service.createProduct(simpleProduct(
                "P102", "EW43247", packaging("42", "31", "28", null, "9.2", "8.5")
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage("毛重不能小于净重");
    }

    @Test
    void keepsSafetyStockAndInnerPackagingData() {
        var result = service.createProduct(simpleProductWithSafetyStock(
                "12.5", "36", "25", "22", "1.1"
        ));

        var sku = result.skus().getFirst();
        assertThat(sku.safetyStockQuantity()).isEqualByComparingTo("12.5");
        assertThat(sku.packaging().innerLengthCm()).isEqualByComparingTo("36");
        assertThat(sku.packaging().innerWidthCm()).isEqualByComparingTo("25");
        assertThat(sku.packaging().innerHeightCm()).isEqualByComparingTo("22");
        assertThat(sku.packaging().innerWeightKg()).isEqualByComparingTo("1.1");
    }

    @Test
    void rejectsNegativeSafetyStock() {
        assertThatThrownBy(() -> service.createProduct(simpleProductWithSafetyStock(
                "-1", "36", "25", "22", "1.1"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessage("安全库存不能小于 0");
    }

    @Test
    void rejectsDuplicateItemNumber() {
        service.createProduct(simpleProduct(
                "P100", "EW43245", packaging("1", "1", "1", null, "1", "1")
        ));

        assertThatThrownBy(() -> service.createProduct(simpleProduct(
                "P999", "EW43245", packaging("1", "1", "1", null, "1", "1")
        ))).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.code()).isEqualTo("DUPLICATE_ITEM_NO"));
    }

    @Test
    void createsVariantProductWithCartesianSkuCombinations() {
        var command = new SaveProductCommand(
                "P200", "EW50000", "玻璃杯", 1L, "共典", VARIANT,
                null, null,
                List.of(
                        new Specification("颜色", List.of("透明", "烟灰")),
                        new Specification("花纹", List.of("竖纹", "樱花纹"))
                ),
                List.of()
        );

        var result = service.createProduct(command);

        assertThat(result.skus()).extracting(Sku::code)
                .containsExactly("PRD-000001-001", "PRD-000001-002", "PRD-000001-003", "PRD-000001-004");
        assertThat(result.skus()).extracting(Sku::specText)
                .containsExactly("透明 / 竖纹", "透明 / 樱花纹", "烟灰 / 竖纹", "烟灰 / 樱花纹");
    }

    @Test
    void createsManualVariantSkusWithoutFormalSpecificationValues() {
        var command = new SaveProductCommand(
                "P201", "EW50001", "玻璃杯", 1L, "共典", VARIANT,
                null, null, List.of(),
                List.of(
                        new SaveSkuCommand(null, null, null, "透明款", List.of(), "只",
                                new BigDecimal("9.90"), new BigDecimal("2.20"), BigDecimal.ZERO, null, null),
                        new SaveSkuCommand(null, null, null, "烟灰款", List.of(), "只",
                                new BigDecimal("10.90"), new BigDecimal("2.50"), BigDecimal.ZERO, null, null)
                )
        );

        var result = service.createProduct(command);

        assertThat(result.skus()).hasSize(2);
        assertThat(result.skus()).extracting(Sku::code)
                .containsExactly("PRD-000001-001", "PRD-000001-002");
        assertThat(result.skus()).extracting(Sku::name)
                .containsExactly("透明款", "烟灰款");
        assertThat(result.skus()).extracting(Sku::specText)
                .containsOnlyNulls();
    }

    @Test
    void keepsProductDimensionsSeparateFromManualVariantSkuSpecifications() {
        var command = new SaveProductCommand(
                "P202", "EW50002", "玻璃杯", 1L, "共典", VARIANT,
                null, null, List.of(new Specification("口径", List.of("70±1mm"))),
                List.of(
                        new SaveSkuCommand(null, null, null, "透明款", List.of(), "只",
                                new BigDecimal("9.90"), new BigDecimal("2.20"), BigDecimal.ZERO, null, null),
                        new SaveSkuCommand(null, null, null, "烟灰款", List.of(), "只",
                                new BigDecimal("10.90"), new BigDecimal("2.50"), BigDecimal.ZERO, null, null)
                )
        );

        var result = service.createProduct(command);

        assertThat(result.specifications()).containsExactly(new Specification("口径", List.of("70±1mm")));
        assertThat(result.skus()).hasSize(2);
    }

    @Test
    void keepsProductDimensionsWhenCreatingSimpleProduct() {
        var command = new SaveProductCommand(
                "P203", "EW50003", "玻璃杯", 1L, "共典", SIMPLE,
                null, null, List.of(
                        new Specification("口径", List.of("70±1mm")),
                        new Specification("高度", List.of("83.5±1mm")),
                        new Specification("容量", List.of("210ml")),
                        new Specification("重量", List.of("200±12g"))
                ),
                List.of(sku(null, null, List.of(), packaging("1", "1", "1", null, "1", "1")))
        );

        var result = service.createProduct(command);

        assertThat(result.specifications()).containsExactly(
                new Specification("口径", List.of("70±1mm")),
                new Specification("高度", List.of("83.5±1mm")),
                new Specification("容量", List.of("210ml")),
                new Specification("重量", List.of("200±12g"))
        );
    }

    @Test
    void keepsProductDimensionsWhenUpdatingSimpleProduct() {
        var created = service.createProduct(simpleProduct(
                "P204", "EW50004", packaging("1", "1", "1", null, "1", "1")
        ));
        var existingSku = created.skus().getFirst();
        var update = new SaveProductCommand(
                created.code(), created.itemNo(), "更新后的玻璃杯", created.categoryId(),
                created.brand(), SIMPLE, null, null,
                List.of(new Specification("容量", List.of("210ml"))),
                List.of(sku(existingSku.id(), existingSku.code(), List.of(),
                        packaging("1", "1", "1", null, "1", "1")))
        );

        var updated = service.updateProduct(created.id(), update);

        assertThat(updated.specifications()).containsExactly(new Specification("容量", List.of("210ml")));
    }

    @Test
    void rejectsDuplicateSkuCodeInsideOneProduct() {
        var sku = sku(null, "SAME-SKU", List.of(), packaging("1", "1", "1", null, "1", "1"));
        var command = new SaveProductCommand(
                "P300", "EW60000", "杯子", 1L, "共典", SIMPLE,
                null, null, List.of(), List.of(sku, sku)
        );

        assertThatThrownBy(() -> service.createProduct(command))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("DUPLICATE_SKU_CODE"));
    }

    @Test
    void updatingExistingSkuKeepsItsIdentity() {
        var created = service.createProduct(simpleProduct(
                "P400", "EW70000", packaging("1", "1", "1", null, "1", "1")
        ));
        var existingSku = created.skus().getFirst();
        var update = new SaveProductCommand(
                created.code(), created.itemNo(), "更新后的产品", created.categoryId(),
                created.brand(), SIMPLE, null, "已更新", List.of(),
                List.of(sku(existingSku.id(), existingSku.code(), List.of(),
                        packaging("2", "2", "2", null, "1", "1")))
        );

        var updated = service.updateProduct(created.id(), update);

        assertThat(updated.skus().getFirst().id()).isEqualTo(existingSku.id());
        assertThat(updated.skus().getFirst().packageVolumeCm3()).isEqualByComparingTo("8");
    }

    @Test
    void updatingDisabledProductKeepsItsDisabledStatus() {
        var created = service.createProduct(simpleProduct(
                "P401", "EW70001", packaging("1", "1", "1", null, "1", "1")
        ));
        service.changeStatus(created.id(), "disabled");
        var existingSku = service.getProduct(created.id()).skus().getFirst();

        var updated = service.updateProduct(created.id(), new SaveProductCommand(
                created.code(), created.itemNo(), "已停用产品", created.categoryId(),
                created.brand(), SIMPLE, null, null, List.of(),
                List.of(sku(existingSku.id(), existingSku.code(), List.of(),
                        packaging("1", "1", "1", null, "1", "1")))
        ));

        assertThat(updated.status()).isEqualTo("disabled");
    }

    private SaveProductCommand simpleProduct(
            String productCode,
            String itemNo,
            PackagingCommand packaging
    ) {
        return new SaveProductCommand(
                productCode, itemNo, "高脚红酒杯", 1L, "共典", SIMPLE,
                null, null, List.of(),
                List.of(sku(null, null, List.of(), packaging))
        );
    }

    private SaveProductCommand simpleProductWithSafetyStock(
            String safetyStock,
            String innerLength,
            String innerWidth,
            String innerHeight,
            String innerWeight
    ) {
        var packaging = new PackagingCommand(
                new BigDecimal("42"), new BigDecimal("31"), new BigDecimal("28"), null,
                decimal(innerLength), decimal(innerWidth), decimal(innerHeight),
                new BigDecimal("8.5"), new BigDecimal("9.2"), new BigDecimal("350"),
                decimal(innerWeight), "彩盒", 12, null, null
        );
        var sku = new SaveSkuCommand(
                null, null, null, null, List.of(), "只",
                new BigDecimal("9.90"), new BigDecimal("2.20"), decimal(safetyStock),
                packaging, null
        );
        return new SaveProductCommand(
                "P-SAFETY", "EW-SAFETY", "高脚红酒杯", 1L, "共典", SIMPLE,
                null, null, List.of(), List.of(sku)
        );
    }

    private SaveSkuCommand sku(
            Long id,
            String code,
            List<String> specificationValues,
            PackagingCommand packaging
    ) {
        return new SaveSkuCommand(
                id, code, null, null, specificationValues, "只",
                new BigDecimal("9.90"), new BigDecimal("2.20"), BigDecimal.ZERO, packaging, null
        );
    }

    private PackagingCommand packaging(
            String length,
            String width,
            String height,
            String volume,
            String netWeight,
            String grossWeight
    ) {
        return new PackagingCommand(
                decimal(length), decimal(width), decimal(height), decimal(volume),
                null, null, null,
                decimal(netWeight), decimal(grossWeight), new BigDecimal("350"), null,
                "彩盒", 12, null, null
        );
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static final class FakeProductRepository implements ProductRepository {
        private final AtomicLong productSequence = new AtomicLong();
        private final AtomicLong skuSequence = new AtomicLong();
        private final Map<Long, Product> products = new LinkedHashMap<>();

        @Override
        public boolean existsByProductCode(String code, Long excludedProductId) {
            return products.values().stream().anyMatch(product -> product.code().equals(code)
                    && !product.id().equals(excludedProductId));
        }

        @Override
        public boolean existsByItemNo(String itemNo, Long excludedProductId) {
            return products.values().stream().anyMatch(product -> product.itemNo().equals(itemNo)
                    && !product.id().equals(excludedProductId));
        }

        @Override
        public boolean existsBySkuCode(String code, Long excludedProductId) {
            return products.values().stream()
                    .filter(product -> !product.id().equals(excludedProductId))
                    .flatMap(product -> product.skus().stream())
                    .anyMatch(sku -> sku.code().equals(code));
        }

        @Override
        public boolean existsByBarcode(String barcode, Long excludedProductId) {
            return products.values().stream()
                    .filter(product -> !product.id().equals(excludedProductId))
                    .flatMap(product -> product.skus().stream())
                    .anyMatch(sku -> barcode.equals(sku.barcode()));
        }

        @Override
        public Product save(Product product) {
            var productId = product.id() == null ? productSequence.incrementAndGet() : product.id();
            var skus = new ArrayList<Sku>();
            for (var sku : product.skus()) {
                skus.add(sku.withId(sku.id() == null ? skuSequence.incrementAndGet() : sku.id()));
            }
            var saved = product.withIdentity(productId, skus);
            products.put(productId, saved);
            return saved;
        }

        @Override
        public String nextProductCode() {
            return "PRD-%06d".formatted(productSequence.get() + 1);
        }

        @Override
        public Optional<Product> findById(long id) {
            return Optional.ofNullable(products.get(id));
        }

        @Override
        public Optional<Sku> findSku(long id) {
            return products.values().stream()
                    .flatMap(product -> product.skus().stream())
                    .filter(sku -> sku.id().equals(id))
                    .findFirst();
        }

        @Override
        public void updateSkuStandardCost(long id, BigDecimal standardCost) {
            products.replaceAll((productId, product) -> product.withIdentity(product.id(), product.skus().stream()
                    .map(sku -> sku.id().equals(id) ? sku.withStandardCost(standardCost) : sku)
                    .toList()));
        }

        @Override
        public Page<Product> findAll(
                String keyword,
                Long categoryId,
                Long supplierId,
                String status,
                Pageable pageable
        ) {
            var values = products.values().stream().toList();
            return new PageImpl<>(values, pageable, values.size());
        }
    }
}
