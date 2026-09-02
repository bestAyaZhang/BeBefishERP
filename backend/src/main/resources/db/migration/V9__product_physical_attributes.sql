alter table product_sku
    add column product_length_cm decimal(12, 3) null after inner_package_height_cm,
    add column product_width_cm decimal(12, 3) null after product_length_cm,
    add column product_height_cm decimal(12, 3) null after product_width_cm,
    add column capacity_ml decimal(12, 3) null after product_height_cm,
    add constraint ck_product_sku_product_length check (product_length_cm is null or product_length_cm >= 0),
    add constraint ck_product_sku_product_width check (product_width_cm is null or product_width_cm >= 0),
    add constraint ck_product_sku_product_height check (product_height_cm is null or product_height_cm >= 0),
    add constraint ck_product_sku_capacity check (capacity_ml is null or capacity_ml >= 0);
