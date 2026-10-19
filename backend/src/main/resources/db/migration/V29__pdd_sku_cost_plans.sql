create table pdd_sku_cost_plan (
    id bigint primary key auto_increment,
    plan_name varchar(120) not null,
    remark varchar(1000) not null default '',
    lines_json mediumtext not null,
    formula_version int not null default 1,
    version_no bigint not null default 1,
    archived boolean not null default false,
    created_by varchar(100) not null,
    updated_by varchar(100) not null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    key idx_pdd_cost_updated (archived, updated_at, id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
