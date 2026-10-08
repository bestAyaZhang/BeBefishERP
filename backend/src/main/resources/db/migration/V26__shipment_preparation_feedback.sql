alter table shipment
    add column preparer_employee_ids_json json null,
    add column actual_weight decimal(12,3) null,
    add column preparation_updated_by varchar(100) null,
    add column preparation_employee_id bigint null,
    add column preparation_updated_at datetime(3) null,
    add constraint ck_shipment_actual_weight check (actual_weight is null or actual_weight > 0);

-- Historical names are retained; account assignment must be explicitly confirmed by an administrator.
insert into sys_permission (code, module_key, action_key, display_name, created_at, updated_at)
values ('shipping:prepare', 'shipping', 'prepare', '更新备货', now(3), now(3));
insert into sys_role_permission (role_id, permission_id, assigned_at)
select r.id, p.id, now(3) from sys_role r cross join sys_permission p
where r.code = 'SUPER_ADMIN' and p.code = 'shipping:prepare';
