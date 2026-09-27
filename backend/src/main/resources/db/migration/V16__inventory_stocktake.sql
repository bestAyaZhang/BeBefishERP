create table inventory_stocktake_task (
    id bigint primary key auto_increment,
    task_no varchar(50) not null,
    warehouse_id bigint not null,
    warehouse_name varchar(100) not null,
    scope_label varchar(200) not null,
    assignee_name varchar(100) not null,
    blind_count boolean not null default true,
    status varchar(30) not null,
    total_items int not null default 0,
    counted_items int not null default 0,
    difference_items int not null default 0,
    created_by varchar(100) not null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    submitted_at datetime(3) null,
    constraint ck_inventory_stocktake_task_status check (
        status in ('not_started', 'in_progress', 'awaiting_recount', 'awaiting_approval', 'completed')
    ),
    constraint ck_inventory_stocktake_task_counts check (
        total_items >= 0 and counted_items >= 0 and difference_items >= 0
    ),
    unique key uk_inventory_stocktake_task_no (task_no),
    key idx_inventory_stocktake_warehouse_created (warehouse_id, created_at),
    key idx_inventory_stocktake_status (status)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table inventory_stocktake_item (
    id bigint primary key auto_increment,
    task_id bigint not null,
    zone_name varchar(120) null,
    pallet_id varchar(120) not null,
    pallet_label varchar(120) not null,
    sku_id bigint not null,
    sku_code varchar(100) not null,
    product_name varchar(200) not null,
    sku_name varchar(200) not null,
    specification varchar(500) null,
    units_per_case int null,
    book_quantity decimal(18, 4) not null,
    first_count_quantity decimal(18, 4) null,
    recount_quantity decimal(18, 4) null,
    difference_quantity decimal(18, 4) null,
    status varchar(30) not null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_inventory_stocktake_item_quantity check (
        book_quantity >= 0
        and (first_count_quantity is null or first_count_quantity >= 0)
        and (recount_quantity is null or recount_quantity >= 0)
    ),
    constraint ck_inventory_stocktake_item_status check (
        status in ('uncounted', 'counted', 'matched', 'difference', 'recount_required', 'approved')
    ),
    constraint fk_inventory_stocktake_item_task foreign key (task_id)
        references inventory_stocktake_task (id) on delete cascade on update restrict,
    unique key uk_inventory_stocktake_item_location_sku (task_id, pallet_id, sku_id),
    key idx_inventory_stocktake_item_task_status (task_id, status),
    key idx_inventory_stocktake_item_sku (sku_id)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
