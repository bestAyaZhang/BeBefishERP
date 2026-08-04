insert into customer (
    customer_no,
    customer_name,
    default_shipping_method,
    default_settlement_period,
    is_system,
    status,
    remark,
    created_at,
    updated_at
) values (
    'WALK_IN',
    '散客',
    'pickup',
    'daily',
    true,
    'enabled',
    '系统预置散客客户，不可停用',
    current_timestamp(3),
    current_timestamp(3)
);
