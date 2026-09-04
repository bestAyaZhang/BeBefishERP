create table department (
    id bigint primary key auto_increment,
    department_code varchar(50) not null,
    department_name varchar(100) not null,
    parent_id bigint null,
    feishu_department_id varchar(128) null,
    sort_order int not null default 0,
    status varchar(20) not null default 'enabled',
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_department_status check (status in ('enabled', 'disabled')),
    constraint ck_department_sort_order check (sort_order >= 0),
    unique key uk_department_code (department_code),
    unique key uk_department_feishu (feishu_department_id),
    constraint fk_department_parent foreign key (parent_id)
        references department (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table position (
    id bigint primary key auto_increment,
    position_code varchar(50) not null,
    position_name varchar(100) not null,
    department_id bigint null,
    sort_order int not null default 0,
    status varchar(20) not null default 'enabled',
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_position_status check (status in ('enabled', 'disabled')),
    constraint ck_position_sort_order check (sort_order >= 0),
    unique key uk_position_code (position_code),
    constraint fk_position_department foreign key (department_id)
        references department (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table employee (
    id bigint primary key auto_increment,
    employee_no varchar(50) not null,
    name varchar(100) not null,
    mobile varchar(30) null,
    avatar_url varchar(1000) null,
    department_id bigint null,
    position_id bigint null,
    employment_type varchar(20) not null,
    status varchar(20) not null,
    source varchar(20) not null,
    profile_complete boolean not null default false,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_employee_employment_type check (employment_type in ('formal', 'temporary')),
    constraint ck_employee_status check (status in ('active', 'disabled', 'resigned')),
    constraint ck_employee_source check (source in ('manual', 'feishu')),
    unique key uk_employee_no (employee_no),
    unique key uk_employee_mobile (mobile),
    constraint fk_employee_department foreign key (department_id)
        references department (id) on delete restrict on update restrict,
    constraint fk_employee_position foreign key (position_id)
        references position (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_user (
    id bigint primary key auto_increment,
    employee_id bigint not null,
    mobile varchar(30) null,
    password_hash varchar(100) null,
    status varchar(20) not null,
    last_login_method varchar(20) null,
    last_login_at datetime(3) null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_sys_user_status check (status in ('enabled', 'disabled')),
    constraint ck_sys_user_login_method check (
        last_login_method is null or last_login_method in ('password', 'feishu')
    ),
    unique key uk_sys_user_employee (employee_id),
    unique key uk_sys_user_mobile (mobile),
    constraint fk_sys_user_employee foreign key (employee_id)
        references employee (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_permission (
    id bigint primary key auto_increment,
    code varchar(100) not null,
    module_key varchar(50) not null,
    action_key varchar(50) not null,
    display_name varchar(100) not null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    unique key uk_sys_permission_code (code)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_role (
    id bigint primary key auto_increment,
    code varchar(50) not null,
    name varchar(100) not null,
    description varchar(500) not null default '',
    kind varchar(20) not null,
    immutable boolean not null default false,
    is_sensitive boolean not null default false,
    status varchar(20) not null,
    data_scope varchar(40) not null,
    updated_by varchar(100) not null default '系统',
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_sys_role_kind check (kind in ('system', 'custom')),
    constraint ck_sys_role_status check (status in ('enabled', 'disabled')),
    constraint ck_sys_role_data_scope check (
        data_scope in ('SELF', 'DEPARTMENT', 'DEPARTMENT_AND_DESCENDANTS', 'COMPANY')
    ),
    unique key uk_sys_role_code (code)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_role_permission (
    role_id bigint not null,
    permission_id bigint not null,
    assigned_at datetime(3) not null,
    primary key (role_id, permission_id),
    constraint fk_role_permission_role foreign key (role_id)
        references sys_role (id) on delete cascade on update restrict,
    constraint fk_role_permission_permission foreign key (permission_id)
        references sys_permission (id) on delete cascade on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_user_role (
    id bigint primary key auto_increment,
    user_id bigint not null,
    role_id bigint not null,
    assignment_source varchar(20) not null,
    assigned_at datetime(3) not null,
    constraint ck_user_role_source check (assignment_source in ('LOCAL', 'FEISHU')),
    unique key uk_user_role_source (user_id, role_id, assignment_source),
    constraint fk_user_role_user foreign key (user_id)
        references sys_user (id) on delete cascade on update restrict,
    constraint fk_user_role_role foreign key (role_id)
        references sys_role (id) on delete cascade on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_feishu_identity (
    id bigint primary key auto_increment,
    user_id bigint not null,
    tenant_key varchar(128) not null,
    open_id varchar(128) not null,
    union_id varchar(128) null,
    display_name varchar(100) not null,
    avatar_url varchar(1000) null,
    bound_at datetime(3) not null,
    last_verified_at datetime(3) not null,
    unique key uk_feishu_identity_user (user_id),
    unique key uk_feishu_identity_open (tenant_key, open_id),
    unique key uk_feishu_identity_union (tenant_key, union_id),
    constraint fk_feishu_identity_user foreign key (user_id)
        references sys_user (id) on delete cascade on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_feishu_role_mapping (
    id bigint primary key auto_increment,
    tenant_key varchar(128) not null,
    feishu_role_id varchar(128) not null,
    feishu_role_name varchar(100) not null,
    erp_role_id bigint not null,
    enabled boolean not null default true,
    member_count int not null default 0,
    last_synced_at datetime(3) null,
    last_error varchar(500) null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_feishu_mapping_member_count check (member_count >= 0),
    unique key uk_feishu_role_mapping (tenant_key, feishu_role_id),
    constraint fk_feishu_mapping_role foreign key (erp_role_id)
        references sys_role (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_auth_session (
    id bigint primary key auto_increment,
    user_id bigint not null,
    token_hash char(64) not null,
    login_method varchar(20) not null,
    created_at datetime(3) not null,
    expires_at datetime(3) not null,
    revoked_at datetime(3) null,
    constraint ck_auth_session_method check (login_method in ('password', 'feishu')),
    constraint ck_auth_session_expiry check (expires_at > created_at),
    unique key uk_auth_session_token (token_hash),
    key ix_auth_session_user (user_id, expires_at),
    constraint fk_auth_session_user foreign key (user_id)
        references sys_user (id) on delete cascade on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_oauth_state (
    id bigint primary key auto_increment,
    state_hash char(64) not null,
    created_at datetime(3) not null,
    expires_at datetime(3) not null,
    consumed_at datetime(3) null,
    constraint ck_oauth_state_expiry check (expires_at > created_at),
    unique key uk_oauth_state_hash (state_hash)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_login_ticket (
    id bigint primary key auto_increment,
    ticket_hash char(64) not null,
    user_id bigint not null,
    warnings_json text null,
    created_at datetime(3) not null,
    expires_at datetime(3) not null,
    consumed_at datetime(3) null,
    constraint ck_login_ticket_expiry check (expires_at > created_at),
    unique key uk_login_ticket_hash (ticket_hash),
    constraint fk_login_ticket_user foreign key (user_id)
        references sys_user (id) on delete cascade on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sys_login_audit (
    id bigint primary key auto_increment,
    user_id bigint null,
    identity_method varchar(20) not null,
    result varchar(20) not null,
    error_code varchar(100) null,
    tenant_key varchar(128) null,
    ip_address varchar(45) null,
    user_agent varchar(500) null,
    occurred_at datetime(3) not null,
    constraint ck_login_audit_method check (identity_method in ('password', 'feishu')),
    constraint ck_login_audit_result check (result in ('success', 'failure')),
    key ix_login_audit_occurred (occurred_at),
    key ix_login_audit_user (user_id, occurred_at),
    constraint fk_login_audit_user foreign key (user_id)
        references sys_user (id) on delete set null on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

insert into sys_permission (code, module_key, action_key, display_name, created_at, updated_at) values
    ('system:user:view', 'system:user', 'view', '查看用户', now(3), now(3)),
    ('system:role:view', 'system:role', 'view', '查看角色', now(3), now(3)),
    ('system:role:manage', 'system:role', 'manage', '管理角色', now(3), now(3)),
    ('dashboard:view', 'dashboard', 'view', '查看工作台', now(3), now(3)),
    ('product:view', 'product', 'view', '查看商品', now(3), now(3)),
    ('product:create', 'product', 'create', '新增商品', now(3), now(3)),
    ('product:edit', 'product', 'edit', '编辑商品', now(3), now(3)),
    ('product:delete', 'product', 'delete', '删除商品', now(3), now(3)),
    ('product:export', 'product', 'export', '导出商品', now(3), now(3)),
    ('category:view', 'category', 'view', '查看分类', now(3), now(3)),
    ('category:create', 'category', 'create', '新增分类', now(3), now(3)),
    ('category:edit', 'category', 'edit', '编辑分类', now(3), now(3)),
    ('category:delete', 'category', 'delete', '删除分类', now(3), now(3)),
    ('category:export', 'category', 'export', '导出分类', now(3), now(3)),
    ('customer:view', 'customer', 'view', '查看客户', now(3), now(3)),
    ('customer:create', 'customer', 'create', '新增客户', now(3), now(3)),
    ('customer:edit', 'customer', 'edit', '编辑客户', now(3), now(3)),
    ('customer:delete', 'customer', 'delete', '删除客户', now(3), now(3)),
    ('customer:export', 'customer', 'export', '导出客户', now(3), now(3)),
    ('supplier:view', 'supplier', 'view', '查看供应商', now(3), now(3)),
    ('supplier:create', 'supplier', 'create', '新增供应商', now(3), now(3)),
    ('supplier:edit', 'supplier', 'edit', '编辑供应商', now(3), now(3)),
    ('supplier:delete', 'supplier', 'delete', '删除供应商', now(3), now(3)),
    ('supplier:export', 'supplier', 'export', '导出供应商', now(3), now(3)),
    ('warehouse:view', 'warehouse', 'view', '查看仓库', now(3), now(3)),
    ('warehouse:create', 'warehouse', 'create', '新增仓库', now(3), now(3)),
    ('warehouse:edit', 'warehouse', 'edit', '编辑仓库', now(3), now(3)),
    ('warehouse:delete', 'warehouse', 'delete', '删除仓库', now(3), now(3)),
    ('warehouse:export', 'warehouse', 'export', '导出仓库', now(3), now(3)),
    ('inventory:view', 'inventory', 'view', '查看库存', now(3), now(3)),
    ('inventory:create', 'inventory', 'create', '新增库存单据', now(3), now(3)),
    ('inventory:edit', 'inventory', 'edit', '编辑库存', now(3), now(3)),
    ('inventory:approve', 'inventory', 'approve', '审核库存', now(3), now(3)),
    ('inventory:export', 'inventory', 'export', '导出库存', now(3), now(3)),
    ('sales:view', 'sales', 'view', '查看销售', now(3), now(3)),
    ('sales:create', 'sales', 'create', '新增销售单', now(3), now(3)),
    ('sales:edit', 'sales', 'edit', '编辑销售单', now(3), now(3)),
    ('sales:delete', 'sales', 'delete', '删除销售单', now(3), now(3)),
    ('sales:approve', 'sales', 'approve', '审核销售单', now(3), now(3)),
    ('sales:export', 'sales', 'export', '导出销售单', now(3), now(3)),
    ('finance:view', 'finance', 'view', '查看财务', now(3), now(3)),
    ('finance:create', 'finance', 'create', '新增财务单据', now(3), now(3)),
    ('finance:edit', 'finance', 'edit', '编辑财务', now(3), now(3)),
    ('finance:approve', 'finance', 'approve', '审核财务', now(3), now(3)),
    ('finance:export', 'finance', 'export', '导出财务', now(3), now(3)),
    ('organization:view', 'organization', 'view', '查看组织架构', now(3), now(3)),
    ('organization:create', 'organization', 'create', '新增组织资料', now(3), now(3)),
    ('organization:edit', 'organization', 'edit', '编辑组织资料', now(3), now(3)),
    ('organization:delete', 'organization', 'delete', '删除组织资料', now(3), now(3)),
    ('organization:export', 'organization', 'export', '导出组织资料', now(3), now(3));

insert into sys_role
    (code, name, description, kind, immutable, is_sensitive, status, data_scope, updated_by, created_at, updated_at)
values
    ('SUPER_ADMIN', '超级管理员', '拥有系统全部权限', 'system', true, true, 'enabled', 'COMPANY', '系统', now(3), now(3)),
    ('PERMISSION_ADMIN', '权限管理员', '维护角色、权限和飞书角色映射', 'system', true, true, 'enabled', 'COMPANY', '系统', now(3), now(3)),
    ('BASIC_EMPLOYEE', '基础员工', '正式员工默认工作台访问权限', 'system', true, false, 'enabled', 'SELF', '系统', now(3), now(3));

insert into sys_role_permission (role_id, permission_id, assigned_at)
select r.id, p.id, now(3)
from sys_role r
cross join sys_permission p
where r.code = 'SUPER_ADMIN';

insert into sys_role_permission (role_id, permission_id, assigned_at)
select r.id, p.id, now(3)
from sys_role r
join sys_permission p on p.code in ('dashboard:view', 'system:role:view', 'system:role:manage')
where r.code = 'PERMISSION_ADMIN';

insert into sys_role_permission (role_id, permission_id, assigned_at)
select r.id, p.id, now(3)
from sys_role r
join sys_permission p on p.code = 'dashboard:view'
where r.code = 'BASIC_EMPLOYEE';

insert into employee
    (employee_no, name, mobile, employment_type, status, source, profile_complete, created_at, updated_at)
values
    ('SYS-ADMIN', '本地系统管理员', '13800138000', 'temporary', 'active', 'manual', true, now(3), now(3));

insert into sys_user
    (employee_id, mobile, password_hash, status, created_at, updated_at)
select id, mobile, '$2a$10$BtngUzsp679G6U0KoHyg1.Q6hB0yM54Y3YDyRy/YfZGCeXrP4v8XG', 'enabled', now(3), now(3)
from employee
where employee_no = 'SYS-ADMIN';

insert into sys_user_role (user_id, role_id, assignment_source, assigned_at)
select u.id, r.id, 'LOCAL', now(3)
from sys_user u
join sys_role r on r.code = 'SUPER_ADMIN'
where u.mobile = '13800138000';
