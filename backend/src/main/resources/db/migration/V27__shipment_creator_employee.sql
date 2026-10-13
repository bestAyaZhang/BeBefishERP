alter table shipment add column creator_employee_id bigint null,
    add constraint ck_shipment_creator_employee check (creator_employee_id is null or creator_employee_id > 0);

-- Explicit employee identifiers are stable. Historical phone/name identifiers need administrator confirmation.
update shipment set creator_employee_id = cast(substring(created_by, 10) as unsigned)
where created_by regexp '^employee:[1-9][0-9]{0,18}$'
  and cast(substring(created_by, 10) as decimal(20,0)) <= 9223372036854775807;
