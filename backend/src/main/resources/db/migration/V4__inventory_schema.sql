create table inventory_balance (
    id bigint primary key auto_increment,
    warehouse_id bigint not null,
    sku_id bigint not null,
    quantity decimal(18, 4) not null default 0,
    version_no bigint not null default 0,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_inventory_balance_quantity check (quantity >= 0),
    unique key uk_inventory_warehouse_sku (warehouse_id, sku_id),
    key idx_inventory_balance_sku (sku_id),
    key idx_inventory_balance_warehouse (warehouse_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table inventory_ledger (
    id bigint primary key auto_increment,
    warehouse_id bigint not null,
    sku_id bigint not null,
    direction varchar(20) not null,
    quantity decimal(18, 4) not null,
    before_quantity decimal(18, 4) not null,
    after_quantity decimal(18, 4) not null,
    source_type varchar(30) not null,
    source_id bigint not null,
    source_no varchar(50) not null,
    occurred_at datetime(3) not null,
    operator_mobile varchar(30) not null,
    constraint ck_inventory_ledger_quantity check (quantity > 0),
    constraint ck_inventory_ledger_before_quantity check (before_quantity >= 0),
    constraint ck_inventory_ledger_after_quantity check (after_quantity >= 0),
    key idx_inventory_ledger_warehouse_sku (warehouse_id, sku_id),
    key idx_inventory_ledger_occurred_at (occurred_at),
    key idx_inventory_ledger_source (source_type, source_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
