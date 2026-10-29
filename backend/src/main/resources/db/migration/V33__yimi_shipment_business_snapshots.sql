alter table shipment add column yimi_options json null;

alter table shipment_logistics_order
 add column carrier_provider varchar(20) not null default 'ANE',
 add column carrier_business_snapshot json null,
 add column carrier_response_metadata json null,
 add constraint ck_shipment_order_provider check(carrier_provider in ('ANE','YIMI'));
