insert into sales_platform(platform_code,platform_name,status,sort_order,remark,version_no,created_by,updated_by,created_at,updated_at)
select 'CUSTOMER_PDD','拼多多','enabled',10,'',0,'migration','migration',now(3),now(3)
where not exists (select 1 from sales_platform where platform_name='拼多多' or platform_code='CUSTOMER_PDD');
insert into sales_platform(platform_code,platform_name,status,sort_order,remark,version_no,created_by,updated_by,created_at,updated_at)
select 'CUSTOMER_1688','1688','enabled',20,'',0,'migration','migration',now(3),now(3)
where not exists (select 1 from sales_platform where platform_name='1688' or platform_code='CUSTOMER_1688');
insert into sales_platform(platform_code,platform_name,status,sort_order,remark,version_no,created_by,updated_by,created_at,updated_at)
select 'CUSTOMER_RETAIL','散户','enabled',30,'',0,'migration','migration',now(3),now(3)
where not exists (select 1 from sales_platform where platform_name='散户' or platform_code='CUSTOMER_RETAIL');

alter table customer add column platform_id bigint null,
    add key idx_customer_platform(platform_id),
    add constraint fk_customer_platform foreign key(platform_id) references sales_platform(id);

create table customer_sku_price (
    id bigint primary key auto_increment,
    customer_id bigint not null,
    sku_id bigint not null,
    unit_price decimal(18,2) not null,
    updated_by varchar(100) not null,
    updated_at datetime(3) not null,
    unique key uk_customer_sku_price(customer_id,sku_id),
    constraint fk_customer_price_customer foreign key(customer_id) references customer(id),
    constraint fk_customer_price_sku foreign key(sku_id) references product_sku(id),
    constraint ck_customer_price_nonnegative check(unit_price >= 0)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
