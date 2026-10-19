package com.bebefish.erp.masterdata.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.application.CustomerAccountViews.*;
import com.bebefish.erp.masterdata.infrastructure.JdbcCustomerAccountRepository;
import com.bebefish.erp.product.domain.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAccountService {
    private final CustomerService customers;
    private final ProductRepository products;
    private final JdbcCustomerAccountRepository repository;

    public CustomerAccountService(CustomerService customers, ProductRepository products, JdbcCustomerAccountRepository repository) {
        this.customers = customers; this.products = products; this.repository = repository;
    }

    public List<PlatformOption> platforms() { return repository.platforms(); }

    public List<Price> prices(long id) { customers.get(id); return repository.prices(id); }

    @Transactional
    public List<Price> savePrice(long id, long skuId, BigDecimal price, String operator) {
        if (!"enabled".equals(customers.get(id).status())) throw validation("客户已停用，不能设置价格");
        if (price == null || price.signum() < 0 || price.stripTrailingZeros().scale() > 2
                || price.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
            throw validation("单价必须为非负金额且最多两位小数");
        }
        var product = products.findProductBySkuId(skuId).orElseThrow(() -> validation("货品不存在"));
        if (!"enabled".equals(product.status()) || product.skus().stream()
                .noneMatch(sku -> sku.id() == skuId && "enabled".equals(sku.status()))) {
            throw validation("货品已停用，不能设置价格");
        }
        repository.savePrice(id, skuId, price.setScale(2), operator);
        return repository.prices(id);
    }

    @Transactional
    public void deletePrice(long id, long skuId) { customers.get(id); repository.deletePrice(id, skuId); }

    @Transactional(readOnly = true)
    public Bills bills(long id, LocalDate from, LocalDate to, int page, int size) {
        customers.get(id);
        if (page < 1 || size < 1 || size > 100) throw validation("分页参数无效");
        if (from != null && to != null && from.isAfter(to)) throw validation("开始日期不能晚于结束日期");
        return repository.bills(id, from, to, page, size);
    }

    private BusinessException validation(String message) {
        return new BusinessException("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, message);
    }
}
