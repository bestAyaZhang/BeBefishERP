alter table department add column manager_employee_id bigint null,
 add constraint fk_department_manager foreign key (manager_employee_id) references employee(id);
alter table position add column responsibilities varchar(1000) not null default '';
alter table employee add column hire_date date null,
 add column feishu_job_title varchar(100) null,
 add column status_source varchar(20) not null default 'manual',
 add constraint ck_employee_status_source check (status_source in ('manual','feishu'));
update employee set status_source='feishu' where source='feishu' and status='active';
create table sys_feishu_directory_sync (
 id bigint primary key auto_increment,
 tenant_key varchar(128) not null,
 trigger_type varchar(20) not null,
 status varchar(20) not null,
 started_by_user_id bigint null,
 started_at datetime(3) not null,
 finished_at datetime(3) null,
 departments_created int not null default 0,
 departments_updated int not null default 0,
 employees_created int not null default 0,
 employees_updated int not null default 0,
 records_skipped int not null default 0,
 records_failed int not null default 0,
 warning_message varchar(1000) null,
 error_message varchar(1000) null,
 constraint ck_directory_trigger check (trigger_type in ('login','manual')),
 constraint ck_directory_status check (status in ('pending','running','success','partial','failed')),
 constraint fk_directory_user foreign key (started_by_user_id) references sys_user(id),
 key ix_directory_tenant_status (tenant_key,status,started_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
insert into sys_permission (code,module_key,action_key,display_name,created_at,updated_at)
 values ('organization:sync','organization','sync','同步飞书通讯录',now(3),now(3));
insert into sys_role_permission (role_id,permission_id,assigned_at)
 select r.id,p.id,now(3) from sys_role r join sys_permission p on p.code='organization:sync' where r.code='SUPER_ADMIN';
