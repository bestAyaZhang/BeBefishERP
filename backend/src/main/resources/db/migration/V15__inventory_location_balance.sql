create table inventory_location_balance (
    id bigint primary key auto_increment,
    warehouse_id bigint not null,
    zone_id varchar(120) null,
    pallet_id varchar(120) not null,
    sku_id bigint not null,
    quantity decimal(18, 4) not null default 0,
    version_no bigint not null default 0,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_inventory_location_balance_quantity check (quantity >= 0),
    unique key uk_inventory_location_pallet_sku (warehouse_id, pallet_id, sku_id),
    key idx_inventory_location_warehouse_zone (warehouse_id, zone_id),
    key idx_inventory_location_warehouse_pallet (warehouse_id, pallet_id),
    key idx_inventory_location_sku (sku_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

insert into inventory_location_balance (
    warehouse_id, zone_id, pallet_id, sku_id, quantity, version_no, created_at, updated_at
)
select warehouse_id, null, 'UNALLOCATED', sku_id, quantity, version_no, created_at, updated_at
from inventory_balance;
