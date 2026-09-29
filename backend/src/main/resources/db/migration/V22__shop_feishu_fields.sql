alter table platform_shop
    add column channel_type varchar(20) not null default 'ecommerce',
    add column owner_name varchar(100) not null default '',
    add column option_label varchar(200) null,
    add constraint ck_shop_channel_type check(channel_type in ('ecommerce','private'));
