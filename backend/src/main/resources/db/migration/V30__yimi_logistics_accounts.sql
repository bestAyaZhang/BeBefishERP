alter table logistics_company
 drop check ck_logistics_company_provider,
 add constraint ck_logistics_company_provider check(provider in ('ANE','MANUAL','YIMI'));

alter table logistics_account_version
 add column app_secret_configured boolean not null default false;
