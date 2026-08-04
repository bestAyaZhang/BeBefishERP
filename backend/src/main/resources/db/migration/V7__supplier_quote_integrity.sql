-- MySQL 5.7 parses CHECK constraints but does not enforce them.
-- Use an allowed-state table and composite foreign key so the invariant works
-- without SUPER privileges or server-level binary-log configuration changes.
create table supplier_quote_allowed_state (
    is_default boolean not null,
    status varchar(20) not null,
    primary key (is_default, status)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

insert into supplier_quote_allowed_state (is_default, status)
values
    (false, 'enabled'),
    (false, 'disabled'),
    (true, 'enabled');

alter table sku_supplier_quote
    add constraint ck_sku_supplier_quote_default_enabled
        foreign key (is_default, status)
        references supplier_quote_allowed_state (is_default, status)
        on update restrict on delete restrict;
