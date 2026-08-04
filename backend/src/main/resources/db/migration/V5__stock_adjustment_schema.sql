create table stock_adjustment (
    id bigint primary key auto_increment,
    adjustment_no varchar(50) not null,
    warehouse_id bigint not null,
    reason varchar(200) not null,
    status varchar(20) not null,
    remark varchar(500) null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_stock_adjustment_status check (status in ('draft', 'confirmed', 'voided')),
    unique key uk_stock_adjustment_no (adjustment_no),
    key idx_stock_adjustment_warehouse_status (warehouse_id, status),
    key idx_stock_adjustment_created_at (created_at)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table stock_adjustment_item (
    id bigint primary key auto_increment,
    adjustment_id bigint not null,
    sku_id bigint not null,
    quantity_delta decimal(18, 4) not null,
    constraint ck_stock_adjustment_item_delta check (quantity_delta <> 0),
    unique key uk_stock_adjustment_item_sku (adjustment_id, sku_id),
    constraint fk_stock_adjustment_item_adjustment foreign key (adjustment_id)
        references stock_adjustment (id) on delete cascade on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
