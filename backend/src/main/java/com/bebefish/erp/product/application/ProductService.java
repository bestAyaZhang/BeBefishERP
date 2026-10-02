package com.bebefish.erp.product.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.domain.Packaging;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.SkuCombination;
import com.bebefish.erp.product.domain.SkuCombinationGenerator;
import com.bebefish.erp.product.domain.SupplierQuoteRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private static final Set<String> STATUSES = Set.of("enabled", "disabled");
    private static final Set<String> PRODUCT_STATUSES = Set.of("enabled", "disabled", "draft");
    private static final Set<String> PRODUCT_DIMENSION_NAMES = Set.of("口径", "高度", "容量", "重量");
    private final ProductRepository repository;
    private final SkuCombinationGenerator combinationGenerator;
    private final SupplierQuoteRepository supplierQuoteRepository;
    private final ProductSupplierQuoteSynchronizer supplierQuoteSynchronizer;

    public ProductService(
            ProductRepository repository,
            SkuCombinationGenerator combinationGenerator,
            SupplierQuoteRepository supplierQuoteRepository,
            ProductSupplierQuoteSynchronizer supplierQuoteSynchronizer
    ) {
        this.repository = repository;
        this.combinationGenerator = combinationGenerator;
        this.supplierQuoteRepository = supplierQuoteRepository;
        this.supplierQuoteSynchronizer = supplierQuoteSynchronizer;
    }

    @Transactional
    public Product createProduct(SaveProductCommand command) {
        var product = toProduct(null, command, null, repository.nextProductCode());
        return saveProductWithSupplierQuotes(product, command.skus());
    }

    @Transactional
    public Product updateProduct(long id, SaveProductCommand command) {
        var existing = getProduct(id);
        var product = toProduct(id, command, existing, existing.code());
        deleteRemovedSkuQuotes(existing, product);
        return saveProductWithSupplierQuotes(product, command.skus());
    }

    @Transactional(readOnly = true)
    public Product getProduct(long id) {
        return repository.findById(id).orElseThrow(() -> new BusinessException(
                "PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND, "产品不存在"
        ));
    }

    @Transactional(readOnly = true)
    public Page<Product> listProducts(
            String keyword,
            Long categoryId,
            Long supplierId,
            String status,
            Pageable pageable
    ) {
        return repository.findAll(
                optional(keyword), categoryId, supplierId, normalizeOptionalStatus(status), pageable
        );
    }

    public String mainImageUrl(long productId) {
        return repository.findMainImageUrl(productId).orElse(null);
    }

    public Map<Long, String> imageUrls(long productId) {
        return repository.findImageUrls(productId);
    }

    public String defaultSupplierName(long productId) {
        return repository.findDefaultSupplierName(productId).orElse(null);
    }

    @Transactional
    public Product changeStatus(long id, String status) {
        return repository.save(getProduct(id).withStatus(normalizeProductStatus(status)));
    }

    private Product saveProductWithSupplierQuotes(Product product, List<SaveSkuCommand> skuCommands) {
        var saved = repository.save(product);
        for (var index = 0; index < skuCommands.size(); index++) {
            var quoteCommands = skuCommands.get(index).supplierQuotes();
            if (quoteCommands == null) {
                continue;
            }
            var skuCode = product.skus().get(index).code();
            var savedSku = saved.skus().stream()
                    .filter(sku -> sku.code().equals(skuCode))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("商品保存后未找到对应 SKU"));
            supplierQuoteSynchronizer.synchronize(savedSku.id(), quoteCommands);
        }
        return saved;
    }

    private void deleteRemovedSkuQuotes(Product existing, Product product) {
        var retainedSkuIds = product.skus().stream()
                .map(Sku::id)
                .filter(skuId -> skuId != null)
                .collect(java.util.stream.Collectors.toSet());
        var removedSkuIds = existing.skus().stream()
                .map(Sku::id)
                .filter(skuId -> !retainedSkuIds.contains(skuId))
                .toList();
        supplierQuoteRepository.deleteBySkuIds(removedSkuIds);
    }

    private Product toProduct(Long id, SaveProductCommand command, Product existing, String generatedCode) {
        if (command == null) {
            throw validation("产品信息不能为空");
        }
        var code = required(generatedCode, "产品编码不能为空");
        var itemNo = required(command.itemNo(), "货号不能为空");
        var name = required(command.productName(), "产品名称不能为空");
        if (command.categoryId() == null || command.categoryId() <= 0) {
            throw validation("产品分类不能为空");
        }
        if (command.type() == null) {
            throw validation("产品类型不能为空");
        }
        if (command.type() == ProductType.SIMPLE && command.specifications().stream()
                .anyMatch(specification -> !PRODUCT_DIMENSION_NAMES.contains(specification.name()))) {
            throw variantError("单规格产品不能定义 SKU 规格");
        }
        ensureUniqueProduct(code, itemNo, id);
        var explicitSkuControls = hasExplicitSkuControls(command.skus());
        var rawSkus = command.type() == ProductType.SIMPLE
                ? simpleSkus(code, name, command.skus(), existing, explicitSkuControls)
                : variantSkus(code, name, command, existing, explicitSkuControls);
        var skus = normalizeSkuDefaults(rawSkus, explicitSkuControls, existing);
        ensureUniqueSkus(skus, id);
        return new Product(
                id, code, itemNo, name, command.categoryId(), optional(command.brand()), command.type(),
                command.mainImageFileId(), productStatus(command.status(), existing), optional(command.remark()),
                command.specifications(), skus,
                existing == null ? null : existing.createdAt(), existing == null ? null : existing.updatedAt()
        );
    }

    private List<Sku> simpleSkus(
            String productCode,
            String productName,
            List<SaveSkuCommand> commands,
            Product existing,
            boolean explicitSkuControls
    ) {
        if (commands.size() > 1) {
            checkDuplicateSkuCodes(commands, productCode);
            throw variantError("单规格产品只能维护一个 SKU");
        }
        var command = commands.isEmpty()
                ? new SaveSkuCommand(null, null, null, null, List.of(), "件", BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null)
                : commands.getFirst();
        if (!command.specificationValues().isEmpty()) {
            throw variantError("单规格产品不能填写规格值");
        }
        return List.of(toSku(
                command,
                findExistingSku(existing, command),
                productCode + "-DEFAULT",
                productName,
                List.of(),
                true,
                explicitSkuControls
        ));
    }

    private List<Sku> variantSkus(
            String productCode,
            String productName,
            SaveProductCommand command,
            Product existing,
            boolean explicitSkuControls
    ) {
        var skuSpecifications = command.specifications().stream()
                .filter(specification -> !PRODUCT_DIMENSION_NAMES.contains(specification.name()))
                .toList();
        if (!command.skus().isEmpty()
                && skuSpecifications.isEmpty()
                && command.skus().stream().allMatch(sku -> sku.specificationValues().isEmpty())) {
            return manualVariantSkus(
                    productCode, productName, command.skus(), existing, explicitSkuControls
            );
        }
        List<SkuCombination> generated;
        try {
            generated = combinationGenerator.generate(
                    ProductType.VARIANT, productCode, skuSpecifications
            );
        } catch (IllegalArgumentException exception) {
            throw variantError(exception.getMessage());
        }
        var generatedByValues = new HashMap<String, SkuCombination>();
        for (var combination : generated) {
            generatedByValues.put(valuesKey(combination.values()), combination);
        }
        var commands = command.skus().isEmpty()
                ? generated.stream().map(combination -> new SaveSkuCommand(
                        null, combination.skuCode(), null, null, combination.values(), "件",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null
                )).toList()
                : command.skus();
        var usedCombinations = new HashSet<String>();
        var skus = new ArrayList<Sku>();
        for (var index = 0; index < commands.size(); index++) {
            var skuCommand = commands.get(index);
            var key = valuesKey(skuCommand.specificationValues());
            var combination = generatedByValues.get(key);
            if (combination == null || !usedCombinations.add(key)) {
                throw variantError("规格组合不完整或重复");
            }
            skus.add(toSku(
                    skuCommand,
                    findExistingSku(existing, skuCommand),
                    combination.skuCode(),
                    productName + " - " + combination.displayText(),
                    combination.values(),
                    index == 0,
                    explicitSkuControls
            ));
        }
        return List.copyOf(skus);
    }

    private List<Sku> manualVariantSkus(
            String productCode,
            String productName,
            List<SaveSkuCommand> commands,
            Product existing,
            boolean explicitSkuControls
    ) {
        var skus = new ArrayList<Sku>();
        for (var index = 0; index < commands.size(); index++) {
            var command = commands.get(index);
            var defaultCode = productCode + "-" + String.format("%03d", index + 1);
            skus.add(toSku(
                    command,
                    findExistingSku(existing, command),
                    defaultCode,
                    productName + " - SKU " + (index + 1),
                    List.of(),
                    index == 0,
                    explicitSkuControls
            ));
        }
        return List.copyOf(skus);
    }

    private Sku toSku(
            SaveSkuCommand command,
            Sku existing,
            String defaultCode,
            String defaultName,
            List<String> values,
            boolean legacyDefault,
            boolean explicitSkuControls
    ) {
        if (command == null) {
            throw variantError("SKU 信息不能为空");
        }
        var code = optional(command.skuCode());
        if (code == null) {
            code = defaultCode;
        }
        var packaging = toPackaging(command.packaging());
        return new Sku(
                existing == null ? command.id() : existing.id(),
                code,
                optional(command.barcode()),
                optional(command.skuName()) == null ? defaultName : optional(command.skuName()),
                values.isEmpty() ? null : String.join(" / ", values),
                values,
                optional(command.salesUnit()) == null ? "件" : optional(command.salesUnit()),
                decimal(nonNegative(command.defaultSalePrice(), "默认售价不能小于 0"), 15, 4, "默认售价"),
                decimal(nonNegative(command.standardCost(), "标准成本不能小于 0"), 15, 4, "标准成本"),
                decimal(nonNegative(command.safetyStockQuantity(), "安全库存不能小于 0"), 14, 4, "安全库存"),
                packaging,
                command.skuImageFileId(),
                explicitSkuControls
                        ? command.defaultSku()
                        : existing == null ? legacyDefault : existing.isDefault(),
                explicitSkuControls
                        ? normalizeStatus(command.status())
                        : existing == null ? "enabled" : existing.status()
        );
    }

    private Packaging toPackaging(PackagingCommand command) {
        if (command == null) {
            return null;
        }
        var length = decimal(nonNegativeOrNull(command.lengthCm(), "包装长不能小于 0"), 9, 3, "包装长");
        var width = decimal(nonNegativeOrNull(command.widthCm(), "包装宽不能小于 0"), 9, 3, "包装宽");
        var height = decimal(nonNegativeOrNull(command.heightCm(), "包装高不能小于 0"), 9, 3, "包装高");
        var volume = decimal(nonNegativeOrNull(command.volumeCm3(), "包装体积不能小于 0"), 15, 3, "包装体积");
        var innerLength = decimal(nonNegativeOrNull(command.innerLengthCm(), "内盒长不能小于 0"), 9, 3, "内盒长");
        var innerWidth = decimal(nonNegativeOrNull(command.innerWidthCm(), "内盒宽不能小于 0"), 9, 3, "内盒宽");
        var innerHeight = decimal(nonNegativeOrNull(command.innerHeightCm(), "内盒高不能小于 0"), 9, 3, "内盒高");
        var productLength = decimal(nonNegativeOrNull(command.productLengthCm(), "产品长不能小于 0"), 9, 3, "产品长");
        var productWidth = decimal(nonNegativeOrNull(command.productWidthCm(), "产品宽不能小于 0"), 9, 3, "产品宽");
        var productHeight = decimal(nonNegativeOrNull(command.productHeightCm(), "产品高不能小于 0"), 9, 3, "产品高");
        var capacity = decimal(nonNegativeOrNull(command.capacityMl(), "容量不能小于 0"), 9, 3, "容量");
        var netWeight = decimal(nonNegativeOrNull(command.netWeightKg(), "净重不能小于 0"), 9, 3, "净重");
        var grossWeight = decimal(nonNegativeOrNull(command.grossWeightKg(), "毛重不能小于 0"), 9, 3, "毛重");
        var gramWeight = decimal(nonNegativeOrNull(command.gramWeightG(), "克重不能小于 0"), 9, 3, "克重");
        var innerWeight = decimal(nonNegativeOrNull(command.innerWeightKg(), "内盒重量不能小于 0"), 9, 3, "内盒重量");
        if (netWeight != null && grossWeight != null && grossWeight.compareTo(netWeight) < 0) {
            throw validation("毛重不能小于净重");
        }
        if (command.cartonQuantity() != null && command.cartonQuantity() <= 0) {
            throw validation("装箱数必须大于 0");
        }
        if (volume == null && length != null && width != null && height != null) {
            volume = decimal(length.multiply(width).multiply(height), 15, 3, "包装体积");
        }
        return new Packaging(
                length, width, height, volume, innerLength, innerWidth, innerHeight,
                productLength, productWidth, productHeight, capacity,
                netWeight, grossWeight, gramWeight, innerWeight,
                optional(command.method()), command.cartonQuantity(),
                command.packageImageFileId(), command.cartonImageFileId()
        );
    }

    private boolean hasExplicitSkuControls(List<SaveSkuCommand> commands) {
        var anyDeclared = commands.stream().anyMatch(command ->
                command.defaultSku() != null || optional(command.status()) != null
        );
        if (!anyDeclared) {
            return false;
        }
        var allDeclared = commands.stream().allMatch(command ->
                command.defaultSku() != null && optional(command.status()) != null
        );
        if (!allDeclared) {
            throw validation("SKU 默认项和状态必须全部显式提交或全部省略");
        }
        return true;
    }

    private List<Sku> normalizeSkuDefaults(
            List<Sku> skus,
            boolean explicitSkuControls,
            Product existing
    ) {
        if (explicitSkuControls) {
            validateSkuDefaults(skus);
            return skus;
        }
        var persistedDefaultId = existing == null ? null : existing.skus().stream()
                .filter(Sku::isDefault)
                .filter(sku -> "enabled".equals(sku.status()))
                .map(Sku::id)
                .findFirst()
                .orElse(null);
        var selectedDefault = skus.stream()
                .filter(sku -> persistedDefaultId != null && persistedDefaultId.equals(sku.id()))
                .filter(sku -> "enabled".equals(sku.status()))
                .findFirst()
                .orElseGet(() -> skus.stream()
                        .filter(sku -> "enabled".equals(sku.status()))
                        .findFirst()
                        .orElseThrow(() -> validation("至少一个 SKU 必须启用")));
        return skus.stream()
                .map(sku -> sku.withState(sku == selectedDefault, sku.status()))
                .toList();
    }

    private void validateSkuDefaults(List<Sku> skus) {
        var defaults = skus.stream().filter(Sku::isDefault).toList();
        if (defaults.size() != 1) {
            throw validation("商品必须且只能有一个默认 SKU");
        }
        if (!"enabled".equals(defaults.getFirst().status())) {
            throw validation("默认 SKU 必须启用");
        }
    }

    private String productStatus(String requestedStatus, Product existing) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return existing == null ? "enabled" : existing.status();
        }
        return normalizeProductStatus(requestedStatus);
    }

    private void ensureUniqueProduct(String code, String itemNo, Long id) {
        if (repository.existsByProductCode(code, id)) {
            throw duplicate("DUPLICATE_PRODUCT_CODE", "产品编码已存在");
        }
        if (repository.existsByItemNo(itemNo, id)) {
            throw duplicate("DUPLICATE_ITEM_NO", "货号已存在");
        }
    }

    private void ensureUniqueSkus(List<Sku> skus, Long productId) {
        var codes = new HashSet<String>();
        var barcodes = new HashSet<String>();
        for (var sku : skus) {
            if (!codes.add(sku.code()) || repository.existsBySkuCode(sku.code(), productId)) {
                throw duplicate("DUPLICATE_SKU_CODE", "SKU 编码已存在");
            }
            if (sku.barcode() != null
                    && (!barcodes.add(sku.barcode()) || repository.existsByBarcode(sku.barcode(), productId))) {
                throw duplicate("DUPLICATE_SKU_CODE", "条码已存在");
            }
        }
    }

    private void checkDuplicateSkuCodes(List<SaveSkuCommand> commands, String productCode) {
        var codes = new HashSet<String>();
        for (var command : commands) {
            var code = optional(command.skuCode());
            if (code == null) {
                code = productCode + "-DEFAULT";
            }
            if (!codes.add(code)) {
                throw duplicate("DUPLICATE_SKU_CODE", "SKU 编码已存在");
            }
        }
    }

    private Sku findExistingSku(Product existing, SaveSkuCommand command) {
        if (existing == null) {
            return null;
        }
        return existing.skus().stream()
                .filter(sku -> (command.id() != null && command.id().equals(sku.id()))
                        || (command.id() == null && command.skuCode() != null
                        && command.skuCode().trim().equals(sku.code())))
                .findFirst()
                .orElse(null);
    }

    private String valuesKey(List<String> values) {
        return String.join("\u0001", values.stream().map(value -> value == null ? "" : value.trim()).toList());
    }

    private String normalizeOptionalStatus(String status) {
        if (status == null || status.isBlank()) return null;
        if ("published".equalsIgnoreCase(status.trim())) return "published";
        return normalizeProductStatus(status);
    }

    private String normalizeProductStatus(String status) {
        var value = required(status, "产品状态不能为空").toLowerCase();
        if (!PRODUCT_STATUSES.contains(value)) throw validation("产品状态无效");
        return value;
    }

    private String normalizeStatus(String status) {
        var value = required(status, "产品状态不能为空").toLowerCase();
        if (!STATUSES.contains(value)) {
            throw validation("产品状态无效");
        }
        return value;
    }

    private BigDecimal nonNegative(BigDecimal value, String message) {
        var result = value == null ? BigDecimal.ZERO : value;
        return nonNegativeOrNull(result, message);
    }

    private BigDecimal nonNegativeOrNull(BigDecimal value, String message) {
        if (value != null && value.signum() < 0) {
            throw validation(message);
        }
        return value;
    }

    private BigDecimal decimal(BigDecimal value, int integerDigits, int fractionDigits, String fieldName) {
        return DecimalConstraints.requireFits(value, integerDigits, fractionDigits, fieldName);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw validation(message);
        }
        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException duplicate(String code, String message) {
        return new BusinessException(code, HttpStatus.CONFLICT, message);
    }

    private BusinessException variantError(String message) {
        return new BusinessException("INVALID_PRODUCT_VARIANT", HttpStatus.BAD_REQUEST, message);
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
