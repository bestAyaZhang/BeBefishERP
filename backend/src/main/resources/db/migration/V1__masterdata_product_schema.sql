create table product_category (
    id bigint primary key auto_increment,
    category_code varchar(50) not null,
    category_name varchar(100) not null,
    parent_id bigint null,
    level_no int not null default 1,
    sort_order int not null default 0,
    status varchar(20) not null,
    remark varchar(500) null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    constraint ck_product_category_level check (level_no >= 1),
    constraint ck_product_category_sort_order check (sort_order >= 0),
    unique key uk_category_code (category_code),
    unique key uk_category_name (category_name),
    constraint fk_product_category_parent foreign key (parent_id)
        references product_category (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table customer (
    id bigint primary key auto_increment,
    customer_no varchar(50) not null,
    customer_name varchar(100) not null,
    contact_person varchar(100) null,
    mobile varchar(30) null,
    telephone varchar(30) null,
    province varchar(100) null,
    city varchar(100) null,
    district varchar(100) null,
    detail_address varchar(500) null,
    default_shipping_method varchar(100) null,
    default_settlement_period varchar(100) null,
    is_system boolean not null default false,
    status varchar(20) not null,
    remark varchar(500) null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    unique key uk_customer_no (customer_no)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table supplier (
    id bigint primary key auto_increment,
    supplier_no varchar(50) not null,
    supplier_name varchar(100) not null,
    contact_person varchar(100) null,
    mobile varchar(30) null,
    telephone varchar(30) null,
    address varchar(500) null,
    status varchar(20) not null,
    remark varchar(500) null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    unique key uk_supplier_no (supplier_no)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table warehouse (
    id bigint primary key auto_increment,
    warehouse_no varchar(50) not null,
    warehouse_name varchar(100) not null,
    address varchar(500) null,
    is_default boolean not null default false,
    status varchar(20) not null,
    enabled_default_guard tinyint generated always as (
        case when status = 'enabled' and is_default then 1 else null end
    ) stored,
    remark varchar(500) null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    constraint ck_warehouse_status check (status in ('enabled', 'disabled')),
    constraint ck_warehouse_default_enabled check (is_default = false or status = 'enabled'),
    unique key uk_warehouse_no (warehouse_no),
    unique key uk_warehouse_name (warehouse_name),
    unique key uk_warehouse_enabled_default (enabled_default_guard)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table file_asset (
    id bigint primary key auto_increment,
    original_name varchar(255) not null,
    storage_name varchar(255) not null,
    storage_path varchar(500) not null,
    access_url varchar(1000) not null,
    content_type varchar(100) not null,
    size_bytes bigint not null,
    status varchar(20) not null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    constraint ck_file_asset_size check (size_bytes >= 0),
    unique key uk_file_asset_storage_name (storage_name)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table product_spu (
    id bigint primary key auto_increment,
    product_code varchar(50) not null,
    item_no varchar(50) not null,
    product_name varchar(200) not null,
    category_id bigint not null,
    brand varchar(100) null,
    product_type varchar(20) not null,
    main_image_file_id bigint null,
    status varchar(20) not null,
    remark varchar(500) null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    constraint ck_product_spu_type check (product_type in ('simple', 'variant')),
    unique key uk_product_spu_product_code (product_code),
    unique key uk_product_spu_item_no (item_no),
    constraint fk_product_spu_category foreign key (category_id)
        references product_category (id) on delete restrict on update restrict,
    constraint fk_product_spu_main_image foreign key (main_image_file_id)
        references file_asset (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table product_spec (
    id bigint primary key auto_increment,
    product_id bigint not null,
    spec_name varchar(100) not null,
    sort_order int not null default 0,
    status varchar(20) not null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    constraint ck_product_spec_sort_order check (sort_order >= 0),
    unique key uk_product_spec_name (product_id, spec_name),
    unique key uk_product_spec_identity (id, product_id),
    constraint fk_product_spec_product foreign key (product_id)
        references product_spu (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table product_spec_value (
    id bigint primary key auto_increment,
    spec_id bigint not null,
    value_name varchar(100) not null,
    sort_order int not null default 0,
    status varchar(20) not null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    constraint ck_product_spec_value_sort_order check (sort_order >= 0),
    unique key uk_product_spec_value_name (spec_id, value_name),
    unique key uk_product_spec_value_identity (id, spec_id),
    constraint fk_product_spec_value_spec foreign key (spec_id)
        references product_spec (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table product_sku (
    id bigint primary key auto_increment,
    product_id bigint not null,
    sku_code varchar(50) not null,
    barcode varchar(100) null,
    sku_name varchar(200) not null,
    spec_text varchar(500) null,
    sales_unit varchar(20) not null,
    default_sale_price decimal(19, 4) not null default 0,
    standard_cost decimal(19, 4) not null default 0,
    package_length_cm decimal(12, 3) null,
    package_width_cm decimal(12, 3) null,
    package_height_cm decimal(12, 3) null,
    package_volume_cm3 decimal(18, 3) null,
    net_weight_kg decimal(12, 3) null,
    gross_weight_kg decimal(12, 3) null,
    gram_weight_g decimal(12, 3) null,
    packaging_method varchar(100) null,
    carton_quantity int null,
    sku_image_file_id bigint null,
    package_image_file_id bigint null,
    carton_image_file_id bigint null,
    is_default boolean not null default false,
    status varchar(20) not null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    constraint ck_product_sku_sale_price check (default_sale_price >= 0),
    constraint ck_product_sku_standard_cost check (standard_cost >= 0),
    constraint ck_product_sku_package_length check (package_length_cm is null or package_length_cm >= 0),
    constraint ck_product_sku_package_width check (package_width_cm is null or package_width_cm >= 0),
    constraint ck_product_sku_package_height check (package_height_cm is null or package_height_cm >= 0),
    constraint ck_product_sku_package_volume check (package_volume_cm3 is null or package_volume_cm3 >= 0),
    constraint ck_product_sku_net_weight check (net_weight_kg is null or net_weight_kg >= 0),
    constraint ck_product_sku_gross_weight check (gross_weight_kg is null or gross_weight_kg >= 0),
    constraint ck_product_sku_gram_weight check (gram_weight_g is null or gram_weight_g >= 0),
    constraint ck_product_sku_carton_quantity check (carton_quantity is null or carton_quantity > 0),
    constraint ck_product_sku_weight_order check (
        gross_weight_kg is null or net_weight_kg is null or gross_weight_kg >= net_weight_kg
    ),
    unique key uk_product_sku_code (sku_code),
    unique key uk_product_sku_barcode (barcode),
    unique key uk_product_sku_identity (id, product_id),
    constraint fk_product_sku_product foreign key (product_id)
        references product_spu (id) on delete restrict on update restrict,
    constraint fk_product_sku_image foreign key (sku_image_file_id)
        references file_asset (id) on delete restrict on update restrict,
    constraint fk_product_sku_package_image foreign key (package_image_file_id)
        references file_asset (id) on delete restrict on update restrict,
    constraint fk_product_sku_carton_image foreign key (carton_image_file_id)
        references file_asset (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table product_sku_spec_value (
    id bigint primary key auto_increment,
    product_id bigint not null,
    sku_id bigint not null,
    spec_id bigint not null,
    spec_value_id bigint not null,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    unique key uk_sku_spec (sku_id, spec_id),
    unique key uk_sku_spec_value (sku_id, spec_value_id),
    constraint fk_sku_spec_value_sku_product foreign key (sku_id, product_id)
        references product_sku (id, product_id) on delete restrict on update restrict,
    constraint fk_sku_spec_value_spec_product foreign key (spec_id, product_id)
        references product_spec (id, product_id) on delete restrict on update restrict,
    constraint fk_sku_spec_value_value_spec foreign key (spec_value_id, spec_id)
        references product_spec_value (id, spec_id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sku_supplier_quote (
    id bigint primary key auto_increment,
    sku_id bigint not null,
    supplier_id bigint not null,
    supplier_item_no varchar(100) null,
    purchase_price decimal(19, 4) not null,
    min_purchase_quantity decimal(19, 4) not null default 1,
    is_default boolean not null default false,
    status varchar(20) not null,
    default_sku_id bigint generated always as (
        case when status = 'enabled' and is_default then sku_id else null end
    ) stored,
    created_by bigint null,
    created_at datetime(3) not null,
    updated_by bigint null,
    updated_at datetime(3) not null,
    constraint ck_sku_supplier_quote_price check (purchase_price >= 0),
    constraint ck_sku_supplier_quote_min_quantity check (min_purchase_quantity > 0),
    constraint ck_sku_supplier_quote_status check (status in ('enabled', 'disabled')),
    constraint ck_sku_supplier_quote_default_enabled check (is_default = false or status = 'enabled'),
    unique key uk_sku_supplier_quote (sku_id, supplier_id),
    unique key uk_sku_supplier_default (default_sku_id),
    constraint fk_sku_supplier_quote_sku foreign key (sku_id)
        references product_sku (id) on delete restrict on update restrict,
    constraint fk_sku_supplier_quote_supplier foreign key (supplier_id)
        references supplier (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
