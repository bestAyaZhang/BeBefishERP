alter table product_sku
    add column product_mouth_diameter_cm decimal(12, 3) null after product_height_cm,
    add constraint ck_product_sku_mouth_diameter
        check (product_mouth_diameter_cm is null or product_mouth_diameter_cm >= 0);
