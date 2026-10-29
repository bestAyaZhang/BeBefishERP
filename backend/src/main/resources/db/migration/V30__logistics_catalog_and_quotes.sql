create table logistics_company (
 id bigint primary key auto_increment, code varchar(50) not null, name varchar(100) not null,
 provider varchar(20) not null, status varchar(20) not null, contact_name varchar(100) not null default '',
 contact_phone varchar(50) not null default '', sort_order int not null default 0, remark varchar(1000) not null default '',
 version_no bigint not null default 0, test_account_id bigint null, production_account_id bigint null,
 test_account_revision bigint not null default 0, production_account_revision bigint not null default 0,
 created_at datetime(3) not null, updated_at datetime(3) not null,
 unique key uk_logistics_company_code(code), key ix_logistics_company_status(status,sort_order,id),
 constraint ck_logistics_company_provider check(provider in ('ANE','MANUAL')),
 constraint ck_logistics_company_status check(status in ('enabled','disabled')),
 constraint ck_logistics_company_sort check(sort_order between 0 and 9999)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
create table logistics_account (
 id bigint primary key auto_increment, company_id bigint not null, environment varchar(20) not null,
 identity_hash char(64) not null, current_version_id bigint null, created_at datetime(3) not null,
 unique key uk_logistics_account_identity(company_id,environment,identity_hash),
 constraint fk_logistics_account_company foreign key(company_id) references logistics_company(id),
 constraint ck_logistics_account_environment check(environment in ('test','production'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
create table logistics_account_version (
 id bigint primary key auto_increment, account_id bigint not null, version_no bigint not null,
 config_json json not null, secret_cipher text null, app_key_configured boolean not null,
 customer_pass_configured boolean not null, created_at datetime(3) not null,
 unique key uk_logistics_account_version(account_id,version_no),
 constraint fk_logistics_account_version_account foreign key(account_id) references logistics_account(id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
create table logistics_template (
 id bigint primary key auto_increment, company_id bigint not null, environment varchar(20) not null,
 account_id bigint null, version_no bigint not null default 0, status varchar(20) not null,
 active_slot char(64) null, config_json json not null, created_at datetime(3) not null, updated_at datetime(3) not null,
 unique key uk_logistics_template_active(company_id,environment,active_slot),
 key ix_logistics_template_company(company_id,environment,status,id),
 constraint fk_logistics_template_company foreign key(company_id) references logistics_company(id),
 constraint fk_logistics_template_account foreign key(account_id) references logistics_account(id),
 constraint ck_logistics_template_status check(status in ('enabled','disabled')),
 constraint ck_logistics_template_environment check(environment in ('test','production'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
create table logistics_template_version (
 id bigint primary key auto_increment, template_id bigint not null, version_no bigint not null,
 account_id bigint null, config_json json not null, created_at datetime(3) not null,
 unique key uk_logistics_template_version(template_id,version_no),
 constraint fk_logistics_template_version_template foreign key(template_id) references logistics_template(id),
 constraint fk_logistics_template_version_account foreign key(account_id) references logistics_account(id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
create table logistics_quote (
 quote_id char(36) primary key, employee_id bigint not null, environment varchar(20) not null,
 company_id bigint not null, company_version bigint not null, account_id bigint null, account_version_id bigint null,
 template_id bigint not null, template_version bigint not null, conditions_json json not null,
 result_json json not null, amount_cents bigint not null, issued_at datetime(3) not null, expires_at datetime(3) not null,
 key ix_logistics_quote_expiry(expires_at), key ix_logistics_quote_owner(employee_id,issued_at),
 constraint fk_logistics_quote_company foreign key(company_id) references logistics_company(id),
 constraint fk_logistics_quote_account foreign key(account_id) references logistics_account(id),
 constraint fk_logistics_quote_account_version foreign key(account_version_id) references logistics_account_version(id),
 constraint fk_logistics_quote_template foreign key(template_id) references logistics_template(id),
 constraint ck_logistics_quote_amount check(amount_cents >= 0)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
create table logistics_audit (
 id bigint primary key auto_increment, actor_employee_id bigint not null, actor_identifier varchar(100) not null,
 action varchar(50) not null, environment varchar(20) not null, company_id bigint not null,
 account_id bigint null, account_version_id bigint null, template_id bigint null, entity_version bigint null,
 metadata_json json not null, occurred_at datetime(3) not null,
 key ix_logistics_audit_company(company_id,occurred_at,id), key ix_logistics_audit_actor(actor_employee_id,occurred_at,id),
 constraint ck_logistics_audit_environment check(environment in ('test','production'))
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
insert into sys_permission(code,module_key,action_key,display_name,created_at,updated_at) values
('logistics:view','logistics','view','查看物流管理',now(3),now(3)),
('logistics:edit','logistics','edit','管理物流公司',now(3),now(3)),
('logistics:configure','logistics','configure','配置物流接口账号',now(3),now(3)),
('logistics:template','logistics','template','管理运费模板',now(3),now(3));
insert into sys_role_permission(role_id,permission_id,assigned_at)
select r.id,p.id,now(3) from sys_role r cross join sys_permission p where r.code='SUPER_ADMIN' and p.module_key='logistics';
