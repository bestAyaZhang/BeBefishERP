alter table product_spu
    add column image_packs json null,
    add column material_images json null;

alter table product_sku
    add column product_photo_file_id bigint null,
    add constraint fk_product_sku_product_photo foreign key (product_photo_file_id)
        references file_asset (id) on delete restrict on update restrict;
