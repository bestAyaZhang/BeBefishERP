alter table shipment_logistics_order drop check ck_logistics_order_state;
alter table shipment_logistics_order add constraint ck_logistics_order_state
    check (state in ('processing', 'succeeded', 'rejected', 'unknown',
                    'cancel_processing', 'cancel_rejected', 'cancel_unknown', 'cancelled'));

insert into sys_permission (code, module_key, action_key, display_name, created_at, updated_at)
values ('shipping:cancel', 'shipping', 'cancel', '取消安能订单', now(3), now(3));
insert into sys_role_permission (role_id, permission_id, assigned_at)
select r.id, p.id, now(3) from sys_role r cross join sys_permission p
where r.code = 'SUPER_ADMIN' and p.code = 'shipping:cancel';
