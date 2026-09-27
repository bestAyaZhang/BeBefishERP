create table shipment (
    id bigint primary key auto_increment,
    shipment_no varchar(50) not null,
    shipment_date date not null,
    recipient_name varchar(100) not null,
    recipient_phone varchar(50) not null,
    recipient_address varchar(1000) not null,
    logistics_company varchar(100) not null default '',
    weight decimal(12,3) null,
    tracking_no varchar(100) not null default '',
    platform varchar(100) not null default '',
    estimated_freight decimal(12,2) null,
    preparation_content text not null,
    remark text not null,
    status varchar(30) not null default 'unfinished',
    preparer varchar(100) not null default '',
    orderer varchar(100) not null default '',
    version_no bigint not null default 0,
    created_by varchar(100) not null,
    updated_by varchar(100) not null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_shipment_no (shipment_no),
    key ix_shipment_date (shipment_date, id),
    key ix_shipment_status (status, shipment_date),
    key ix_shipment_tracking (tracking_no),
    constraint ck_shipment_status check (status in ('unfinished', 'completed', 'out_of_stock', 'partially_shipped')),
    constraint ck_shipment_weight check (weight is null or weight >= 0),
    constraint ck_shipment_freight check (estimated_freight is null or estimated_freight >= 0),
    constraint ck_shipment_version check (version_no >= 0)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

insert into sys_permission (code, module_key, action_key, display_name, created_at, updated_at) values
    ('shipping:view', 'shipping', 'view', '查看发货列表', now(3), now(3)),
    ('shipping:create', 'shipping', 'create', '新增发货单', now(3), now(3)),
    ('shipping:edit', 'shipping', 'edit', '编辑发货单', now(3), now(3));

insert into sys_role_permission (role_id, permission_id, assigned_at)
select r.id, p.id, now(3) from sys_role r cross join sys_permission p
where r.code = 'SUPER_ADMIN' and p.module_key = 'shipping';
