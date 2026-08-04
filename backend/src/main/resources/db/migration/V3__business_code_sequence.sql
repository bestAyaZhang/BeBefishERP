create table business_code_sequence (
    sequence_name varchar(50) primary key,
    next_value bigint not null,
    constraint ck_business_code_sequence_next_value check (next_value > 0)
) engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

insert into business_code_sequence (sequence_name, next_value)
select 'product', coalesce(max(cast(substring(product_code, 5) as unsigned)), 0) + 1
from product_spu
where product_code regexp '^PRD-[0-9]{6}$';
