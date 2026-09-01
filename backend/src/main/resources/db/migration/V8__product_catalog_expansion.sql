alter table product_sku
    add column safety_stock_quantity decimal(18, 4) not null default 0 after standard_cost,
    add column inner_package_length_cm decimal(12, 3) null after package_volume_cm3,
    add column inner_package_width_cm decimal(12, 3) null after inner_package_length_cm,
    add column inner_package_height_cm decimal(12, 3) null after inner_package_width_cm,
    add column inner_package_weight_kg decimal(12, 3) null after gram_weight_g,
    add constraint ck_product_sku_safety_stock check (safety_stock_quantity >= 0),
    add constraint ck_product_sku_inner_length check (inner_package_length_cm is null or inner_package_length_cm >= 0),
    add constraint ck_product_sku_inner_width check (inner_package_width_cm is null or inner_package_width_cm >= 0),
    add constraint ck_product_sku_inner_height check (inner_package_height_cm is null or inner_package_height_cm >= 0),
    add constraint ck_product_sku_inner_weight check (inner_package_weight_kg is null or inner_package_weight_kg >= 0);
