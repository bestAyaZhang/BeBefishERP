create table sales_order_sequence (
    business_date date primary key,
    current_value int not null,
    constraint ck_sales_order_sequence_value check (current_value > 0 and current_value <= 9999)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sales_order (
    id bigint primary key auto_increment,
    sales_no varchar(50) not null,
    customer_id bigint not null,
    customer_name varchar(100) not null,
    warehouse_id bigint not null,
    warehouse_name varchar(100) not null,
    sales_date date not null,
    salesperson_mobile varchar(30) not null,
    status varchar(20) not null,
    transport_method varchar(30) not null,
    settlement_cycle varchar(30) not null,
    payment_method varchar(30) null,
    delivery_address varchar(500) null,
    logistics_company varchar(100) null,
    tracking_no varchar(100) null,
    package_note varchar(500) null,
    invoice_required boolean not null default false,
    invoice_status varchar(20) not null default 'not_required',
    goods_amount decimal(18, 2) not null default 0,
    discount_amount decimal(18, 2) not null default 0,
    shipping_fee decimal(18, 2) not null default 0,
    total_amount decimal(18, 2) not null default 0,
    received_amount decimal(18, 2) not null default 0,
    outstanding_amount decimal(18, 2) not null default 0,
    remark varchar(1000) null,
    created_at datetime(3) not null,
    updated_at datetime(3) not null,
    constraint ck_sales_order_status check (status in ('draft', 'confirmed', 'void')),
    constraint ck_sales_order_invoice_status check (invoice_status in ('not_required', 'pending', 'issued')),
    constraint ck_sales_order_amounts check (
        goods_amount >= 0 and discount_amount >= 0 and shipping_fee >= 0
        and total_amount >= 0 and received_amount >= 0 and outstanding_amount >= 0
    ),
    unique key uk_sales_order_no (sales_no),
    key idx_sales_order_customer (customer_id),
    key idx_sales_order_warehouse (warehouse_id),
    key idx_sales_order_status_date (status, sales_date),
    constraint fk_sales_order_customer foreign key (customer_id)
        references customer (id) on delete restrict on update restrict,
    constraint fk_sales_order_warehouse foreign key (warehouse_id)
        references warehouse (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

create table sales_order_item (
    id bigint primary key auto_increment,
    sales_order_id bigint not null,
    sku_id bigint not null,
    quantity decimal(18, 4) not null,
    default_unit_price decimal(18, 2) not null default 0,
    unit_price decimal(18, 2) not null default 0,
    discount_rate decimal(7, 4) not null default 0,
    amount decimal(18, 2) not null default 0,
    standard_cost_snapshot decimal(18, 4) null,
    item_no_snapshot varchar(100) null,
    product_name_snapshot varchar(200) not null,
    sku_code_snapshot varchar(100) not null,
    sku_name_snapshot varchar(200) null,
    specification_snapshot varchar(500) null,
    packaging_snapshot varchar(200) null,
    carton_quantity_snapshot int null,
    barcode_snapshot varchar(100) null,
    sales_unit_snapshot varchar(30) not null,
    constraint ck_sales_order_item_quantity check (quantity > 0),
    constraint ck_sales_order_item_prices check (default_unit_price >= 0 and unit_price >= 0),
    constraint ck_sales_order_item_discount check (discount_rate >= 0 and discount_rate <= 100),
    constraint ck_sales_order_item_amount check (amount >= 0),
    key idx_sales_order_item_order (sales_order_id),
    key idx_sales_order_item_sku (sku_id),
    constraint fk_sales_order_item_order foreign key (sales_order_id)
        references sales_order (id) on delete cascade on update restrict,
    constraint fk_sales_order_item_sku foreign key (sku_id)
        references product_sku (id) on delete restrict on update restrict
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
