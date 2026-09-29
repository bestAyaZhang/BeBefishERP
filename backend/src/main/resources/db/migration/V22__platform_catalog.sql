create table sales_platform (
    id bigint primary key auto_increment,
    platform_code varchar(50) not null,
    platform_name varchar(100) not null,
    status varchar(20) not null default 'enabled',
    sort_order int not null default 0,
    remark varchar(1000) not null default '',
    version_no bigint not null default 0,
    created_by varchar(100) not null,
    updated_by varchar(100) not null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_platform_code(platform_code),
    unique key uk_platform_name(platform_name),
    key ix_platform_status(status,sort_order,id),
    constraint ck_platform_status check(status in ('enabled','disabled')),
    constraint ck_platform_sort check(sort_order between 0 and 9999),
    constraint ck_platform_version check(version_no >= 0)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table platform_shop (
    id bigint primary key auto_increment,
    platform_id bigint not null,
    shop_code varchar(50) not null,
    shop_name varchar(200) not null,
    status varchar(20) not null default 'enabled',
    sort_order int not null default 0,
    remark varchar(1000) not null default '',
    version_no bigint not null default 0,
    created_by varchar(100) not null,
    updated_by varchar(100) not null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_shop_code(shop_code),
    unique key uk_shop_platform_name(platform_id,shop_name),
    unique key uk_shop_id_platform(id,platform_id),
    key ix_shop_status(platform_id,status,sort_order,id),
    constraint fk_shop_platform foreign key(platform_id) references sales_platform(id),
    constraint ck_shop_status check(status in ('enabled','disabled')),
    constraint ck_shop_sort check(sort_order between 0 and 9999),
    constraint ck_shop_version check(version_no >= 0)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

alter table shipment
    add column platform_id bigint null,
    add column shop_id bigint null,
    add key ix_shipment_platform(platform_id,shipment_date,id),
    add key ix_shipment_shop(shop_id,shipment_date,id),
    add constraint fk_shipment_platform foreign key(platform_id) references sales_platform(id),
    add constraint fk_shipment_shop foreign key(shop_id,platform_id) references platform_shop(id,platform_id),
    add constraint ck_shipment_source check(shop_id is null or platform_id is not null);

insert into sys_permission(code,module_key,action_key,display_name,created_at,updated_at) values
('platform:view','platform','view','查看平台和店铺',now(3),now(3)),
('platform:create','platform','create','新增平台和店铺',now(3),now(3)),
('platform:edit','platform','edit','编辑及启停用平台和店铺',now(3),now(3));

insert into sys_role_permission(role_id,permission_id,assigned_at)
select r.id,p.id,now(3) from sys_role r cross join sys_permission p
where r.code='SUPER_ADMIN' and p.module_key='platform';
