create table shipment_logistics_order (
    shipment_id bigint primary key,
    order_no varchar(30) not null,
    state varchar(20) not null,
    test_environment boolean not null,
    tracking_no varchar(100) not null default '',
    child_tracking_nos text not null,
    message varchar(500) not null default '',
    request_snapshot text not null,
    operator_id varchar(100) not null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_shipment_logistics_order_no (order_no),
    constraint fk_logistics_shipment foreign key (shipment_id) references shipment (id),
    constraint ck_logistics_order_state check (state in ('processing', 'succeeded', 'rejected', 'unknown'))
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

insert into sys_permission (code, module_key, action_key, display_name, created_at, updated_at)
values ('shipping:order', 'shipping', 'order', '物流下单', now(3), now(3));
insert into sys_role_permission (role_id, permission_id, assigned_at)
select r.id, p.id, now(3) from sys_role r cross join sys_permission p
where r.code = 'SUPER_ADMIN' and p.code = 'shipping:order';
