create table shipment_freight_selection (
 shipment_id bigint primary key, quote_id char(36) null, preferred_company_id bigint null,
 quote_snapshot json null, conditions_snapshot json null,
 updated_by varchar(100) not null, updated_at datetime(3) not null,
 constraint fk_shipment_freight_shipment foreign key(shipment_id) references shipment(id),
 constraint fk_shipment_freight_quote foreign key(quote_id) references logistics_quote(quote_id),
 constraint fk_shipment_freight_company foreign key(preferred_company_id) references logistics_company(id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

alter table shipment_logistics_order
 add column carrier_company_id bigint null,
 add column carrier_account_id bigint null,
 add column carrier_account_version_id bigint null,
 add column cancellation_account_version_id bigint null,
 add column legacy_account_identity_hash char(64) null,
 add column account_confirmed_by varchar(100) null,
 add column account_confirmed_at datetime(3) null,
 add constraint fk_order_carrier_company foreign key(carrier_company_id) references logistics_company(id),
 add constraint fk_order_carrier_account foreign key(carrier_account_id) references logistics_account(id),
 add constraint fk_order_carrier_version foreign key(carrier_account_version_id) references logistics_account_version(id),
 add constraint fk_order_cancellation_version foreign key(cancellation_account_version_id) references logistics_account_version(id);
