alter table shipment
    add column sender_name varchar(30) not null default '' after shipment_date,
    add column sender_phone varchar(30) not null default '' after sender_name,
    add column sender_province varchar(30) not null default '' after sender_phone,
    add column sender_city varchar(30) not null default '' after sender_province,
    add column sender_county varchar(30) not null default '' after sender_city,
    add column sender_detail_address varchar(100) not null default '' after sender_county;
