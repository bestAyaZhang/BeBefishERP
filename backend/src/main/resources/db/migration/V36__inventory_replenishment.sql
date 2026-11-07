alter table warehouse add column independent_replenishment boolean not null default false;

create table inventory_replenishment_policy (
    sku_id bigint not null,
    scope_warehouse_id bigint not null comment '0 represents the shared SKU scope',
    mode varchar(10) not null,
    buffer_days int not null,
    lead_days int not null,
    manual_safety bigint null,
    manual_point bigint null,
    primary key (sku_id, scope_warehouse_id),
    constraint fk_replenishment_policy_sku foreign key (sku_id)
        references product_sku (id) on delete cascade,
    constraint ck_replenishment_scope check (scope_warehouse_id >= 0),
    constraint ck_replenishment_mode check (mode in ('auto', 'manual')),
    constraint ck_replenishment_days check (buffer_days between 0 and 36500 and lead_days between 0 and 36500),
    constraint ck_replenishment_thresholds check (
        (mode = 'auto' and manual_safety is null and manual_point is null)
        or (manual_safety is not null and manual_point is not null
            and manual_safety >= 0 and manual_point >= manual_safety and manual_point <= 99999999999999)
    )
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
