package com.bebefish.erp.product.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.bebefish.erp.product.domain.Product;
import com.bebefish.erp.product.domain.ProductType;
import com.bebefish.erp.product.domain.Sku;
import com.bebefish.erp.product.domain.Specification;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProductJpaAdapterQueryCountTest {
    private static final String CATEGORY_CODE = "T3-QC-CAT";
    private static final String PRODUCT_CODE_PREFIX = "T3-QC-P-";

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    private long categoryId;
    private CountingDataSource countingDataSource;
    private ProductJpaAdapter countedRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbc.update("insert into product_category "
                + "(category_code, category_name, level_no, sort_order, status, created_at, updated_at) "
                + "values (?, 'Query Count Category', 1, 0, 'enabled', now(3), now(3))", CATEGORY_CODE);
        categoryId = jdbc.queryForObject(
                "select id from product_category where category_code = ?", Long.class, CATEGORY_CODE
        );

        var repository = new ProductJpaAdapter(jdbc);
        repository.save(product("1", "Alpha Product", "Red"));
        repository.save(product("2", "Beta Product", "Blue"));

        countingDataSource = new CountingDataSource(dataSource);
        countedRepository = new ProductJpaAdapter(new JdbcTemplate(countingDataSource));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void keepsProductPageHydrationQueryCountConstantAsPageSizeGrows() {
        countingDataSource.reset();
        var oneProductPage = countedRepository.findAll(
                null, categoryId, null, null, PageRequest.of(0, 1)
        );
        var oneProductQueryCount = countingDataSource.statementCount();

        assertThat(oneProductPage.getContent()).extracting(Product::name).containsExactly("Alpha Product");
        assertThat(oneProductPage.getContent().getFirst().specifications())
                .containsExactly(new Specification("Color", List.of("Red")));
        assertThat(oneProductPage.getContent().getFirst().skus())
                .extracting(Sku::specificationValues).containsExactly(List.of("Red"));

        countingDataSource.reset();
        var twoProductPage = countedRepository.findAll(
                null, categoryId, null, null, PageRequest.of(0, 2)
        );
        var twoProductQueryCount = countingDataSource.statementCount();

        assertThat(twoProductPage.getContent()).extracting(Product::name)
                .containsExactly("Alpha Product", "Beta Product");
        assertThat(twoProductQueryCount)
                .as("page hydration must use a fixed number of parameterized batch queries")
                .isEqualTo(oneProductQueryCount)
                .isEqualTo(6);
    }

    @Test
    void skipsBatchHydrationQueriesForAnEmptyPage() {
        countingDataSource.reset();

        var emptyPage = countedRepository.findAll(
                null, categoryId, null, null, PageRequest.of(1, 2)
        );

        assertThat(emptyPage.getContent()).isEmpty();
        assertThat(emptyPage.getTotalElements()).isEqualTo(2);
        assertThat(countingDataSource.statementCount())
                .as("an empty page must only execute count and page-id queries")
                .isEqualTo(2);
    }

    private Product product(String suffix, String name, String color) {
        var sku = new Sku(
                null,
                "T3-QC-SKU-" + suffix,
                "T3-QC-BARCODE-" + suffix,
                name + " SKU",
                color,
                List.of(color),
                "unit",
                new BigDecimal("10.00"),
                new BigDecimal("4.00"),
                BigDecimal.ONE,
                null,
                null,
                true,
                "enabled"
        );
        return new Product(
                null,
                PRODUCT_CODE_PREFIX + suffix,
                "T3-QC-ITEM-" + suffix,
                name,
                categoryId,
                "Test Brand",
                ProductType.VARIANT,
                null,
                "enabled",
                null,
                List.of(new Specification("Color", List.of(color))),
                List.of(sku),
                null,
                null
        );
    }

    private void cleanUp() {
        jdbc.update("delete link from product_sku_spec_value link "
                + "join product_spu product on product.id = link.product_id "
                + "where product.product_code like 'T3-QC-P-%'");
        jdbc.update("delete sku from product_sku sku "
                + "join product_spu product on product.id = sku.product_id "
                + "where product.product_code like 'T3-QC-P-%'");
        jdbc.update("delete value from product_spec_value value "
                + "join product_spec spec on spec.id = value.spec_id "
                + "join product_spu product on product.id = spec.product_id "
                + "where product.product_code like 'T3-QC-P-%'");
        jdbc.update("delete spec from product_spec spec "
                + "join product_spu product on product.id = spec.product_id "
                + "where product.product_code like 'T3-QC-P-%'");
        jdbc.update("delete from product_spu where product_code like 'T3-QC-P-%'");
        jdbc.update("delete from product_category where category_code = ?", CATEGORY_CODE);
    }

    private static final class CountingDataSource extends DelegatingDataSource {
        private final AtomicInteger statementCount = new AtomicInteger();

        private CountingDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return countingConnection(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return countingConnection(super.getConnection(username, password));
        }

        private Connection countingConnection(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    ProductJpaAdapterQueryCountTest.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        if ("prepareStatement".equals(method.getName())) {
                            statementCount.incrementAndGet();
                        }
                        try {
                            return method.invoke(connection, arguments);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }
            );
        }

        private void reset() {
            statementCount.set(0);
        }

        private int statementCount() {
            return statementCount.get();
        }
    }
}
