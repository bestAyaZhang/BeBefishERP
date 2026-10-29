package com.bebefish.erp.masterdata.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.masterdata.domain.Customer;
import com.bebefish.erp.masterdata.infrastructure.JdbcCustomerAccountRepository;
import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductRepository;
import com.bebefish.erp.product.domain.Sku;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerAccountServiceTest {
    private CustomerService customers;
    private ProductRepository products;
    private JdbcCustomerAccountRepository repository;
    private CustomerAccountService service;
    private Customer customer;
    private Product product;
    private Sku sku;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerService.class); products = mock(ProductRepository.class);
        repository = mock(JdbcCustomerAccountRepository.class);
        service = new CustomerAccountService(customers, products, repository);
        customer = mock(Customer.class); product = mock(Product.class); sku = mock(Sku.class);
        when(customers.get(1)).thenReturn(customer); when(customer.status()).thenReturn("enabled");
        when(products.findProductBySkuId(20)).thenReturn(Optional.of(product));
        when(product.status()).thenReturn("enabled"); when(product.skus()).thenReturn(List.of(sku));
        when(sku.id()).thenReturn(20L); when(sku.status()).thenReturn("enabled");
    }

    @Test
    void storesZeroPriceWithTwoDecimals() {
        service.savePrice(1, 20, BigDecimal.ZERO, "operator");
        verify(repository).savePrice(1, 20, new BigDecimal("0.00"), "operator");
    }

    @Test
    void rejectsInvalidMoneyBeforeWriting() {
        for (var price : List.of(new BigDecimal("-0.01"), new BigDecimal("0.001"), new BigDecimal("10000000000000000"))) {
            assertThatThrownBy(() -> service.savePrice(1, 20, price, "operator")).isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> service.savePrice(1, 20, null, "operator")).isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsDisabledCustomersAndProducts() {
        when(customer.status()).thenReturn("disabled");
        assertThatThrownBy(() -> service.savePrice(1, 20, BigDecimal.ONE, "operator")).hasMessageContaining("客户已停用");
        when(customer.status()).thenReturn("enabled"); when(sku.status()).thenReturn("disabled");
        assertThatThrownBy(() -> service.savePrice(1, 20, BigDecimal.ONE, "operator")).hasMessageContaining("货品已停用");
        when(sku.status()).thenReturn("enabled"); when(product.status()).thenReturn("disabled");
        assertThatThrownBy(() -> service.savePrice(1, 20, BigDecimal.ONE, "operator")).hasMessageContaining("货品已停用");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsReversedDatesAndInvalidPagination() {
        assertThatThrownBy(() -> service.bills(1, LocalDate.parse("2026-10-14"), LocalDate.parse("2026-10-01"), 1, 20)).hasMessageContaining("开始日期");
        assertThatThrownBy(() -> service.bills(1, null, null, 0, 20)).hasMessageContaining("分页");
        assertThatThrownBy(() -> service.bills(1, null, null, 1, 101)).hasMessageContaining("分页");
        verifyNoInteractions(repository);
    }
}
