package com.bebefish.erp.product.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.product.domain.Packaging;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.SkuCombination;
import com.bebefish.erp.product.domain.SkuCombinationGenerator;
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
    private static final Set<String> PRODUCT_DIMENSION_NAMES = Set.of("口径", "高度", "容量", "重量");
    private final ProductRepository repository;
    private final SkuCombinationGenerator combinationGenerator;

    public ProductService(
            ProductRepository repository,
            SkuCombinationGenerator combinationGenerator
    ) {
        this.repository = repository;
        this.combinationGenerator = combinationGenerator;
    }

    @Transactional
    public Product createProduct(SaveProductCommand command) {
        return repository.save(toProduct(null, command, null, repository.nextProductCode()));
    }

    @Transactional
    public Product updateProduct(long id, SaveProductCommand command) {
        var existing = getProduct(id);
        return repository.save(toProduct(id, command, existing, existing.code()));
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

    public String defaultSupplierName(long productId) {
        return repository.findDefaultSupplierName(productId).orElse(null);
    }

    @Transactional
    public Product changeStatus(long id, String status) {
        return repository.save(getProduct(id).withStatus(normalizeStatus(status)));
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
        ensureUniqueProduct(code, itemNo, id);
        var skus = command.type() == ProductType.SIMPLE
                ? simpleSkus(code, name, command.skus(), existing)
                : variantSkus(code, name, command, existing);
        ensureUniqueSkus(skus, id);
        return new Product(
                id, code, itemNo, name, command.categoryId(), optional(command.brand()), command.type(),
                command.mainImageFileId(), existing == null ? "enabled" : existing.status(), optional(command.remark()),
                command.specifications(), skus
        );
    }

    private List<Sku> simpleSkus(
            String productCode,
            String productName,
            List<SaveSkuCommand> commands,
            Product existing
    ) {
        if (commands.size() > 1) {
            checkDuplicateSkuCodes(commands, productCode);
            throw variantError("单规格产品只能维护一个 SKU");
        }
        var command = commands.isEmpty()
                ? new SaveSkuCommand(null, null, null, null, List.of(), "件", BigDecimal.ZERO,
                BigDecimal.ZERO, null, null)
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
                true
        ));
    }

    private List<Sku> variantSkus(
            String productCode,
            String productName,
            SaveProductCommand command,
            Product existing
    ) {
        var skuSpecifications = command.specifications().stream()
                .filter(specification -> !PRODUCT_DIMENSION_NAMES.contains(specification.name()))
                .toList();
        if (!command.skus().isEmpty()
                && skuSpecifications.isEmpty()
                && command.skus().stream().allMatch(sku -> sku.specificationValues().isEmpty())) {
            return manualVariantSkus(productCode, productName, command.skus(), existing);
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
                        BigDecimal.ZERO, BigDecimal.ZERO, null, null
                )).toList()
                : command.skus();
        var usedCombinations = new HashSet<String>();
        var skus = new ArrayList<Sku>();
        for (var skuCommand : commands) {
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
                    false
            ));
        }
        return List.copyOf(skus);
    }

    private List<Sku> manualVariantSkus(
            String productCode,
            String productName,
            List<SaveSkuCommand> commands,
            Product existing
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
                    index == 0
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
            boolean isDefault
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
                nonNegative(command.defaultSalePrice(), "默认售价不能小于 0"),
                nonNegative(command.standardCost(), "标准成本不能小于 0"),
                packaging,
                command.skuImageFileId(),
                isDefault,
                "enabled"
        );
    }

    private Packaging toPackaging(PackagingCommand command) {
        if (command == null) {
            return null;
        }
        var length = nonNegativeOrNull(command.lengthCm(), "包装长不能小于 0");
        var width = nonNegativeOrNull(command.widthCm(), "包装宽不能小于 0");
        var height = nonNegativeOrNull(command.heightCm(), "包装高不能小于 0");
        var volume = nonNegativeOrNull(command.volumeCm3(), "包装体积不能小于 0");
        var netWeight = nonNegativeOrNull(command.netWeightKg(), "净重不能小于 0");
        var grossWeight = nonNegativeOrNull(command.grossWeightKg(), "毛重不能小于 0");
        var gramWeight = nonNegativeOrNull(command.gramWeightG(), "克重不能小于 0");
        if (netWeight != null && grossWeight != null && grossWeight.compareTo(netWeight) < 0) {
            throw validation("毛重不能小于净重");
        }
        if (command.cartonQuantity() != null && command.cartonQuantity() <= 0) {
            throw validation("装箱数必须大于 0");
        }
        if (volume == null && length != null && width != null && height != null) {
            volume = length.multiply(width).multiply(height);
        }
        return new Packaging(
                length, width, height, volume, netWeight, grossWeight, gramWeight,
                optional(command.method()), command.cartonQuantity(),
                command.packageImageFileId(), command.cartonImageFileId()
        );
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
        return status == null || status.isBlank() ? null : normalizeStatus(status);
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
