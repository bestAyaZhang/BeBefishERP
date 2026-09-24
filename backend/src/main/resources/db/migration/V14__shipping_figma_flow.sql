alter table shipment
    add column shop_name varchar(200) not null default '' after platform,
    add column recipient_province varchar(30) not null default '' after recipient_phone,
    add column recipient_city varchar(30) not null default '' after recipient_province,
    add column recipient_county varchar(30) not null default '' after recipient_city,
    add column recipient_detail_address varchar(500) not null default '' after recipient_county,
    add column preparers_json json null after preparer,
    add column cargo_name varchar(32) not null default '' after estimated_freight,
    add column pack_type varchar(50) not null default '' after cargo_name,
    add column volume decimal(12,2) null after weight,
    add column piece_amount int null after volume,
    add column product_type_id int null after piece_amount,
    add column goods_type int null after product_type_id,
    add column pay_type int null after goods_type,
    add column logistics_remark varchar(200) not null default '' after pay_type;

update shipment
set recipient_detail_address = recipient_address,
    preparers_json = case when preparer = '' then json_array() else json_array(preparer) end;

alter table shipment modify preparers_json json not null;
